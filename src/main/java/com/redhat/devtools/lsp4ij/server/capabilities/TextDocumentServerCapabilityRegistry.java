/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.capabilities;

import com.google.gson.JsonObject;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.features.files.PathPatternMatcher;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureManager;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureType;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentRegistrationOptions;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Base class for Server capability registry for 'textDocument/*'.
 *
 * @param <T> the LSP {@link TextDocumentRegistrationOptions}.
 */
public abstract class TextDocumentServerCapabilityRegistry<T extends TextDocumentRegistrationOptions> {

    private final @NotNull LSPClientFeatures clientFeatures;
    private final @Nullable EditorFeatureType editorFeatureType;
    private @Nullable ServerCapabilities serverCapabilities;

    private final List<T> dynamicCapabilities;

    public TextDocumentServerCapabilityRegistry(@NotNull LSPClientFeatures clientFeatures) {
        this(clientFeatures, null);
    }

    public TextDocumentServerCapabilityRegistry(@NotNull LSPClientFeatures clientFeatures,
                                                @Nullable EditorFeatureType editorFeatureType) {
        this.clientFeatures = clientFeatures;
        this.dynamicCapabilities = new ArrayList<>();
        this.editorFeatureType = editorFeatureType;
    }

    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        this.serverCapabilities = serverCapabilities;
        this.dynamicCapabilities.clear();
    }

    public @Nullable ServerCapabilities getServerCapabilities() {
        return serverCapabilities;
    }

    @Nullable
    public T registerCapability(@NotNull JsonObject registerOptions) {
        T t = create(registerOptions);
        if (t != null) {
            synchronized (dynamicCapabilities) {
                dynamicCapabilities.add(t);
            }
        }
        if (editorFeatureType != null) {
            // Refresh codelens, inlay hints, folding, etc according to the register/unregister capability.
            for (var fileData : clientFeatures.getServerWrapper().getOpenedDocuments()) {
                VirtualFile file = fileData.getFile();
                EditorFeatureManager.getInstance(clientFeatures.getProject())
                        .refreshEditorFeature(file, editorFeatureType, true);
            }
        }
        return t;
    }

    @Nullable
    protected abstract T create(@NotNull JsonObject registerOptions);

    public void unregisterCapability(Object options) {
        dynamicCapabilities.remove(options);
    }

    protected boolean isSupported(@NotNull PsiFile file,
                                  @NotNull Predicate<@NotNull ServerCapabilities> matchServerCapabilities) {
        return isSupported(file, matchServerCapabilities, null);
    }

    protected boolean isSupported(@NotNull VirtualFile file,
                                  @NotNull Predicate<@NotNull ServerCapabilities> matchServerCapabilities) {
        return isSupported(file, matchServerCapabilities, null);
    }

    protected boolean isSupported(@NotNull PsiFile psiFile,
                                  @NotNull Predicate<@NotNull ServerCapabilities> matchServerCapabilities,
                                  @Nullable Predicate<@NotNull T> matchOption) {
        return isSupported(psiFile, null, matchServerCapabilities, matchOption);
    }

    protected boolean isSupported(@NotNull VirtualFile file,
                                  @NotNull Predicate<@NotNull ServerCapabilities> matchServerCapabilities,
                                  @Nullable Predicate<@NotNull T> matchOption) {
        return isSupported(null, file, matchServerCapabilities, matchOption);
    }

    private boolean isSupported(@Nullable PsiFile psiFile,
                                @Nullable VirtualFile file,
                                @NotNull Predicate<@NotNull ServerCapabilities> matchServerCapabilities,
                                @Nullable Predicate<@NotNull T> matchOption) {
        var serverCapabilities = getServerCapabilities();
        if (serverCapabilities != null && matchServerCapabilities.test(serverCapabilities)) {
            return true;
        }

        if (dynamicCapabilities.isEmpty()) {
            return false;
        }

        boolean languageIdGet = false;
        String languageId = null;
        URI fileUri = null;
        String scheme = null;
        for (var option : dynamicCapabilities) {
            // Match documentSelector?
            var filters = ((ExtendedDocumentSelector.DocumentFilersProvider) option).getFilters();
            if (filters.isEmpty()) {
                return matchOption != null ? matchOption.test(option) : true;
            }
            for (var filter : filters) {
                boolean hasLanguage = !StringUtils.isEmpty(filter.getLanguage());
                boolean hasScheme = !StringUtils.isEmpty(filter.getScheme());
                boolean hasPattern = !StringUtils.isEmpty(filter.getPattern());

                boolean matchDocumentSelector = false;
                // Matches language?
                if (hasLanguage) {
                    if (!languageIdGet) {
                        if (psiFile == null) {
                            psiFile = LSPIJUtils.getPsiFile(file, clientFeatures.getProject());
                        }
                        languageId = clientFeatures.getServerDefinition().getLanguageIdOrNull(psiFile);
                        languageIdGet = true;
                    }
                    matchDocumentSelector = (languageId == null && !hasScheme && !hasPattern) // to be compatible with LSP4IJ < 0.7.0, when languageId is not defined in the mapping, we consider that it matches the documentSelector
                            || filter.getLanguage().equals(languageId);
                }

                if (!matchDocumentSelector) {
                    // Matches scheme?
                    if (hasScheme) {
                        if (fileUri == null) {
                            fileUri = file != null ? clientFeatures.getFileUri(file) : clientFeatures.getFileUri(LSPIJUtils.getFile(psiFile));
                        }
                        if (scheme == null) {
                            scheme = fileUri.getScheme();
                        }
                        matchDocumentSelector = filter.getScheme().equals(scheme);
                    }

                    if (!matchDocumentSelector) {

                        // Matches pattern?
                        if (hasPattern) {
                            PathPatternMatcher patternMatcher = filter.getPathPattern();
                            if (fileUri == null) {
                                fileUri = file != null ? clientFeatures.getFileUri(file) : clientFeatures.getFileUri(LSPIJUtils.getFile(psiFile));
                            }
                            matchDocumentSelector = patternMatcher.matches(fileUri);
                        }
                    }
                }

                if (matchDocumentSelector) {
                    if (matchOption == null) {
                        return true;
                    }
                    if (matchOption.test(option)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean hasCapability(final Either<Boolean, ?> eitherCapability) {
        if (eitherCapability == null) {
            return false;
        }
        return eitherCapability.isRight() || hasCapability(eitherCapability.getLeft());
    }

    public static boolean hasCapability(Boolean capability) {
        return capability != null && capability;
    }

    public List<T> getOptions() {
        return dynamicCapabilities;
    }

    protected @NotNull LSPClientFeatures getClientFeatures() {
        return clientFeatures;
    }
}

/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition;

import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerEnablementSupport;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.features.semanticTokens.DefaultSemanticTokensColorsProvider;
import com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensColorsProvider;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base class for Language server definition.
 */
public abstract class LanguageServerDefinition implements LanguageServerFactory, LanguageServerEnablementSupport {

    private static final int DEFAULT_LAST_DOCUMENTED_DISCONNECTED_TIMEOUT = 5;

    private static final SemanticTokensColorsProvider DEFAULT_SEMANTIC_TOKENS_COLORS_PROVIDER = new DefaultSemanticTokensColorsProvider();

    private final @NotNull String id;
    private final @NotNull String name;
    private final boolean isSingleton;
    private final String description;
    private final int lastDocumentDisconnectedTimeout;
    private final boolean supportsLightEdit;
    private final @NotNull
    Map<Language, String> languageIdLanguageMappings;
    private final @NotNull
    Map<FileType, String> languageIdFileTypeMappings;
    private final List<Pair<List<FileNameMatcher>, String>> languageIdFileNameMatcherMappings;
    private boolean enabled;
    private SemanticTokensColorsProvider semanticTokensColorsProvider;

    public LanguageServerDefinition(@NotNull String id,
                                    @NotNull String name,
                                    String description,
                                    boolean isSingleton,
                                    Integer lastDocumentDisconnectedTimeout,
                                    boolean supportsLightEdit) {
        this(id, name, description, isSingleton, lastDocumentDisconnectedTimeout, supportsLightEdit, true);
    }

    protected LanguageServerDefinition(@NotNull String id,
                                       @NotNull String name,
                                       String description,
                                       boolean isSingleton,
                                       Integer lastDocumentDisconnectedTimeout,
                                       boolean supportsLightEdit,
                                       boolean updateEnabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isSingleton = isSingleton;
        this.lastDocumentDisconnectedTimeout = lastDocumentDisconnectedTimeout != null && lastDocumentDisconnectedTimeout > 0 ? lastDocumentDisconnectedTimeout : DEFAULT_LAST_DOCUMENTED_DISCONNECTED_TIMEOUT;
        this.languageIdLanguageMappings = new ConcurrentHashMap<>();
        this.languageIdFileTypeMappings = new ConcurrentHashMap<>();
        this.languageIdFileNameMatcherMappings = new CopyOnWriteArrayList<>();
        this.supportsLightEdit = supportsLightEdit;
        setSemanticTokensColorsProvider(DEFAULT_SEMANTIC_TOKENS_COLORS_PROVIDER);
        if (updateEnabled) {
            // Enable by default language server
            setEnabled(true, null);
        }
    }

    /**
     * Returns the language server id.
     *
     * @return the language server id.
     */
    @NotNull
    public String getId() {
        return id;
    }

    /**
     * Returns the language server display name.
     *
     * @return the language server display name.
     */
    @NotNull
    public String getDisplayName() {
        return name;
    }

    /**
     * Returns the language server description.
     *
     * @return the language server description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns true if the language server is a singleton and false otherwise.
     *
     * @return true if the language server is a singleton and false otherwise.
     */
    public boolean isSingleton() {
        return isSingleton;
    }

    public int getLastDocumentDisconnectedTimeout() {
        return lastDocumentDisconnectedTimeout;
    }

    /**
     * Returns true if the language server definition is enabled and false otherwise.
     *
     * @param project the project and null otherwise.
     * @return true if the language server definition is enabled and false otherwise.
     */
    @Override
    public boolean isEnabled(@NotNull Project project) {
        return enabled;
    }

    /**
     * Set enabled the language server definition.
     *
     * @param enabled enabled the language server definition.
     * @param project the project and null otherwise.
     */
    public void setEnabled(boolean enabled, @Nullable Project project) {
        this.enabled = enabled;
    }

    @ApiStatus.Internal
    public void removeAssociations() {
        this.languageIdLanguageMappings.clear();
        this.languageIdFileTypeMappings.clear();
        this.languageIdFileNameMatcherMappings.clear();
    }

    public void registerAssociation(@NotNull Language language, @NotNull String languageId) {
        this.languageIdLanguageMappings.put(language, languageId);
    }

    public void registerAssociation(@NotNull FileType fileType, @NotNull String languageId) {
        this.languageIdFileTypeMappings.put(fileType, languageId);
    }

    public void registerAssociation(List<FileNameMatcher> matchers, String languageId) {
        this.languageIdFileNameMatcherMappings.add(Pair.create(matchers, languageId));
    }

    public Map<Language, String> getLanguageMappings() {
        return languageIdLanguageMappings;
    }

    public Map<FileType, String> getFileTypeMappings() {
        return languageIdFileTypeMappings;
    }

    public List<Pair<List<FileNameMatcher>, String>> getFilenameMatcherMappings() {
        return languageIdFileNameMatcherMappings;
    }

    public <S extends LanguageServer> Launcher.Builder<S> createLauncherBuilder() {
        return new Launcher.Builder<>();
    }

    public boolean supportsCurrentEditMode(@NotNull Project project) {
        return (supportsLightEdit || !LightEdit.owns(project));
    }

    public Icon getIcon() {
        return AllIcons.Webreferences.Server;
    }


    @NotNull
    public SemanticTokensColorsProvider getSemanticTokensColorsProvider() {
        return semanticTokensColorsProvider;
    }

    public void setSemanticTokensColorsProvider(@NotNull SemanticTokensColorsProvider semanticTokensColorsProvider) {
        this.semanticTokensColorsProvider = semanticTokensColorsProvider;
    }

    /**
     * Returns the LSP language id defined in mapping otherwise null.
     *
     * @param file the PsiFile file.
     * @return the LSP language id or null.
     */
    @Nullable
    public String getLanguageIdOrNull(@NotNull PsiFile file) {
        return getLanguageId(file.getLanguage(), file.getFileType(), file.getName(), true);
    }

    /**
     * Returns the LSP language id defined in mapping otherwise the {@link Language#getID()} otherwise the {@link FileType#getName()} otherwise 'unknown'.
     *
     * @param file the PsiFile file.
     * @return the LSP language id.
     */
    @NotNull
    public String getLanguageId(@NotNull PsiFile file) {
        return getLanguageId(file.getLanguage(), file.getFileType(), file.getName());
    }

    /**
     * Returns the LSP language id defined in mapping otherwise the {@link Language#getID()} otherwise the {@link FileType#getName()} otherwise 'unknown'.
     *
     * @param file the virtual file.
     * @param project the project.
     * @return the LSP language id.
     */
    @NotNull
    public String getLanguageId(@Nullable VirtualFile file,
                                @NotNull Project project) {
        if (file == null) {
            return FileTypes.UNKNOWN.getName().toLowerCase(Locale.ROOT);
        }
        Language language = LSPIJUtils.getFileLanguage(file, project);
        return getLanguageId(language, file.getFileType(), file.getName());
    }

    /**
     * Returns the LSP language id defined in mapping otherwise the {@link Language#getID()} otherwise the {@link FileType#getName()} otherwise 'unknown'.
     *
     * @param language the language.
     * @param fileType the file type.
     * @param fileName the file name.
     * @return the LSP language id.
     */
    @NotNull
    public String getLanguageId(@Nullable Language language,
                                @Nullable FileType fileType,
                                @NotNull String fileName) {
        return getLanguageId(language, fileType, fileName, false);
    }
    /**
     * Returns the LSP language id defined in mapping otherwise the {@link Language#getID()} otherwise the {@link FileType#getName()} otherwise 'unknown'.
     *
     * @param language the language.
     * @param fileType the file type.
     * @param fileName the file name.
     * @param nullIfNotFound returns null if not found.
     * @return the LSP language id.
     */
    @Nullable
    private String getLanguageId(@Nullable Language language,
                                 @Nullable FileType fileType,
                                 @NotNull String fileName,
                                 boolean nullIfNotFound) {
        // 1. Try to get the LSP languageId by using language mapping
        String languageId = getLanguageId(language);
        if (languageId != null) {
            return languageId;
        }

        // 2. Try to get the LSP languageId by using the fileType mapping
        languageId = getLanguageId(fileType);
        if (languageId != null) {
            return languageId;
        }

        // 3. Try to get the LSP languageId by using the file name pattern mapping
        languageId = getLanguageId(fileName);
        if (languageId != null) {
            return languageId;
        }

        // At this step there is no mapping
        if (nullIfNotFound) {
            return null;
        }

        // We return the language Id if it exists or file type name
        // with 'lower case' to try to map the recommended languageId specified at
        // https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocumentItem
        if (language != null) {
            // The language exists, use its ID with lower case
            return language.getID().toLowerCase(Locale.ROOT);
        }
        // Returns the existing file type or 'unknown' with lower case
        return fileName.toLowerCase(Locale.ROOT);
    }

    /**
     * @return The language ID that this wrapper is dealing with if defined in the
     * language mapping for the language server
     */
    @Nullable
    public String getLanguageId(@Nullable Language language) {
        while (language != null) {
            String languageId =  languageIdLanguageMappings.get(language);
            if (languageId != null) {
                return languageId;
            }
            language = language.getBaseLanguage();
        }
        return null;
    }

    /**
     * @return The language ID that this wrapper is dealing with if defined in the
     * file type mapping for the language server
     */
    @Nullable
    public String getLanguageId(@Nullable FileType fileType) {
        if (fileType == null) {
            return null;
        }
        return languageIdFileTypeMappings.get(fileType);
    }
    /**
     * @return The language ID that this wrapper is dealing with if defined in the
     * file type mapping for the language server
     */
    public @Nullable String getLanguageId(String filename) {
        for (var mapping : languageIdFileNameMatcherMappings) {
            for (var matcher : mapping.getFirst()) {
                if (matcher.acceptsCharSequence(filename)) {
                    return mapping.getSecond();
                }
            }
        }
        return null;
    }
}

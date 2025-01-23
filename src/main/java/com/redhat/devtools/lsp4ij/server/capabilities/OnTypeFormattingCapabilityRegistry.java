/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
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
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions;
import org.eclipse.lsp4j.DocumentOnTypeFormattingRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Server capability registry for 'textDocument/onTypeFormatting'.
 */
public class OnTypeFormattingCapabilityRegistry extends TextDocumentServerCapabilityRegistry<DocumentOnTypeFormattingRegistrationOptions> {

    private static final @NotNull Predicate<@NotNull ServerCapabilities> SERVER_CAPABILITIES_PREDICATE = sc ->
            sc.getDocumentOnTypeFormattingProvider() != null;

    public OnTypeFormattingCapabilityRegistry(@NotNull LSPClientFeatures clientFeatures) {
        super(clientFeatures);
    }

    static class ExtendedOnTypeFormattingProviderOptions extends DocumentOnTypeFormattingRegistrationOptions implements ExtendedDocumentSelector.DocumentFilersProvider {
        private transient ExtendedDocumentSelector documentSelector;

        @Override
        public List<ExtendedDocumentSelector.ExtendedDocumentFilter> getFilters() {
            if (documentSelector == null) {
                documentSelector = new ExtendedDocumentSelector(super.getDocumentSelector());
            }
            return documentSelector.getFilters();
        }
    }

    @Override
    protected @Nullable DocumentOnTypeFormattingRegistrationOptions create(@NotNull JsonObject registerOptions) {
        return JSONUtils.getLsp4jGson()
                .fromJson(registerOptions,
                        ExtendedOnTypeFormattingProviderOptions.class);
    }

    /**
     * Returns true if the language server can support onTypeFormatting and false otherwise.
     *
     * @param file the Psi file.
     * @return true if the language server can support onTypeFormatting and false otherwise.
     */
    public boolean isOnTypeFormattingSupported(@NotNull PsiFile file) {
        return super.isSupported(file, SERVER_CAPABILITIES_PREDICATE);
    }

    /**
     * Determines whether or not the given character is an on-type formatting trigger for the file according to its
     * language server.
     *
     * @param file      the file.
     * @param charTyped the typed character.
     * @return true if the given character is an on-type formatting trigger for the file and false otherwise.
     */
    public boolean isOnTypeFormattingTriggerCharacter(@NotNull PsiFile file,
                                                      @Nullable String charTyped) {
        return (charTyped != null) && super.isSupported(
                file,
                sc -> {
                    Set<String> onTypeFormattingTriggerCharacters = new LinkedHashSet<>();
                    DocumentOnTypeFormattingOptions onTypeFormattingOptions = sc.getDocumentOnTypeFormattingProvider();
                    if (onTypeFormattingOptions != null) {
                        ContainerUtil.addIfNotNull(onTypeFormattingTriggerCharacters, onTypeFormattingOptions.getFirstTriggerCharacter());
                        ContainerUtil.addAllNotNull(onTypeFormattingTriggerCharacters, onTypeFormattingOptions.getMoreTriggerCharacter());
                    }
                    return onTypeFormattingTriggerCharacters.contains(charTyped);
                }
        );
    }
}

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
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.eclipse.lsp4j.CompletionRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Server capability registry for 'textDocument/completion'.
 */
public class CompletionCapabilityRegistry extends TextDocumentServerCapabilityRegistry<CompletionRegistrationOptions> {

    private static final @NotNull Predicate<@NotNull ServerCapabilities> SERVER_CAPABILITIES_PREDICATE = sc ->
            sc.getCompletionProvider() != null;

    private static final @NotNull Predicate<@NotNull ServerCapabilities> RESOLVE_SERVER_CAPABILITIES_PREDICATE = sc ->
            sc.getCompletionProvider() != null && hasCapability(sc.getCompletionProvider().getResolveProvider());

    private static final @Nullable Predicate<@NotNull CompletionRegistrationOptions> RESOLVE_REGISTRATION_OPTIONS_PREDICATE = o ->
            hasCapability(o.getResolveProvider());

    public CompletionCapabilityRegistry(@NotNull LSPClientFeatures clientFeatures) {
        super(clientFeatures);
    }

    class ExtendedCompletionFormattingRegistrationOptions extends CompletionRegistrationOptions  implements ExtendedDocumentSelector.DocumentFilersProvider {
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
    protected @Nullable CompletionRegistrationOptions create(JsonObject json) {
        return JSONUtils.getLsp4jGson()
                .fromJson(json,
                        ExtendedCompletionFormattingRegistrationOptions.class);
    }

    /**
     * Returns true if the language server can support completion and false otherwise.
     *
     * @param file the Psi file.
     * @return true if the language server can support completion and false otherwise.
     */
    public boolean isCompletionSupported(@NotNull PsiFile file) {
        return super.isSupported(file, SERVER_CAPABILITIES_PREDICATE);
    }

    /**
     * Returns true if the language server can support resolve completion and false otherwise.
     *
     * @param file the Psi file.
     * @return true if the language server can support resolve completion and false otherwise.
     */
    public boolean isResolveCompletionSupported(@NotNull PsiFile file) {
        return super.isSupported(file, RESOLVE_SERVER_CAPABILITIES_PREDICATE, RESOLVE_REGISTRATION_OPTIONS_PREDICATE);
    }

}

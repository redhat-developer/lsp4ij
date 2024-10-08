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
import org.eclipse.lsp4j.DocumentSymbolRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Server capability registry for 'textDocument/documentSymbol'.
 */
public class DocumentSymbolCapabilityRegistry extends TextDocumentServerCapabilityRegistry<DocumentSymbolRegistrationOptions> {

    private static final @NotNull Predicate<@NotNull ServerCapabilities> SERVER_CAPABILITIES_PREDICATE = sc ->
            hasCapability(sc.getDocumentSymbolProvider());

    public DocumentSymbolCapabilityRegistry(@NotNull LSPClientFeatures clientFeatures) {
        super(clientFeatures);
    }

    class ExtendedSymbolFormattingRegistrationOptions extends DocumentSymbolRegistrationOptions implements ExtendedDocumentSelector.DocumentFilersProvider {
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
    protected @Nullable DocumentSymbolRegistrationOptions create(@NotNull JsonObject registerOptions) {
        return JSONUtils.getLsp4jGson()
                .fromJson(registerOptions,
                        ExtendedSymbolFormattingRegistrationOptions.class);
    }

    /**
     * Returns true if the language server can support documentSymbol and false otherwise.
     *
     * @param file the Psi file.
     * @return true if the language server can support documentSymbol and false otherwise.
     */
    public boolean isDocumentSymbolSupported(@NotNull PsiFile file) {
        return super.isSupported(file, SERVER_CAPABILITIES_PREDICATE);
    }

}

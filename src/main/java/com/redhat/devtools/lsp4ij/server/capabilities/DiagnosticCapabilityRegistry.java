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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.DocumentContentSynchronizer;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.eclipse.lsp4j.DiagnosticRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Server capability registry for 'textDocument/diagnostic'.
 */
public class DiagnosticCapabilityRegistry extends TextDocumentServerCapabilityRegistry<DiagnosticRegistrationOptions> {

    private static final @NotNull Predicate<@NotNull ServerCapabilities> SERVER_CAPABILITIES_PREDICATE = sc ->
            sc.getDiagnosticProvider() != null;

    public DiagnosticCapabilityRegistry(@NotNull LSPClientFeatures clientFeatures) {
        super(clientFeatures);
    }

    class ExtendedDiagnosticRegistrationOptions extends DiagnosticRegistrationOptions implements ExtendedDocumentSelector.DocumentFilersProvider {
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
    protected @Nullable DiagnosticRegistrationOptions create(@NotNull JsonObject registerOptions) {
        return JSONUtils.getLsp4jGson()
                .fromJson(registerOptions,
                        ExtendedDiagnosticRegistrationOptions.class);
    }

    /**
     * Returns true if the language server can support diagnostic and false otherwise.
     *
     * @param file the Psi file.
     * @return true if the language server can support diagnostic and false otherwise.
     */
    public boolean isDiagnosticSupported(@NotNull PsiFile file) {
        return super.isSupported(file, SERVER_CAPABILITIES_PREDICATE);
    }

    /**
     * Returns true if the language server can support diagnostic and false otherwise.
     *
     * @param file the Psi file.
     * @return true if the language server can support diagnostic and false otherwise.
     */
    public boolean isDiagnosticSupported(@NotNull VirtualFile file) {
        return super.isSupported(file, SERVER_CAPABILITIES_PREDICATE);
    }

    @Override
    public @Nullable DiagnosticRegistrationOptions registerCapability(@NotNull JsonObject registerOptions) {
        var options =  super.registerCapability(registerOptions);
        // Force the pull diagnostic for all opened document
        // - didOpen have not processed pull diagnostic
        // - and if language server support it for now if textDocument/diagnostic has been dynamically registered.
        for (var openedDocument :  getClientFeatures().getServerWrapper().getOpenedDocuments()) {
            openedDocument.getSynchronizer().refreshPullDiagnostic(DocumentContentSynchronizer.RefreshPullDiagnosticOrigin.ON_REGISTER_CAPABILITY);
        }
        return options;
    }
}

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
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.DiagnosticRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
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

    /**
     * Registers diagnostic options and refreshes document and workspace pulls.
     *
     * @param registerOptions the options supplied by the server's registration
     * @return the registered options, or {@code null} if none were created
     */
    @Override
    public @Nullable DiagnosticRegistrationOptions registerCapability(@NotNull JsonObject registerOptions) {
        var options = super.registerCapability(registerOptions);
        // Force the pull diagnostic for all opened document
        // - didOpen have not processed pull diagnostic
        // - and if language server support it for now if textDocument/diagnostic has been dynamically registered.
        for (var openedDocument : getClientFeatures().getServerWrapper().getOpenedDocuments()) {
            if (openedDocument.getSynchronizer() != null) {
                openedDocument.getSynchronizer()
                        .refreshPullDiagnostic(DocumentContentSynchronizer.RefreshPullDiagnosticOrigin.ON_REGISTER_CAPABILITY);
            }
        }
        getClientFeatures().getServerWrapper().updateWorkspaceDiagnosticProviders();
        return options;
    }

    /**
     * Removes diagnostic options and reconciles active workspace providers.
     *
     * @param options the options returned by
     *                {@link #registerCapability(JsonObject)}
     */
    @Override
    public void unregisterCapability(Object options) {
        super.unregisterCapability(options);
        getClientFeatures().getServerWrapper().updateWorkspaceDiagnosticProviders();
    }

    /**
     * Returns the identifiers of advertised workspace diagnostic providers.
     * Includes static capabilities and dynamic registrations.
     *
     * @return a new set of provider identifiers, with {@code null} for the
     *         default provider
     */
    public Set<String> getWorkspaceDiagnosticIdentifiers() {
        Set<String> identifiers = new HashSet<>();
        var capabilities = getServerCapabilities();
        if (capabilities != null && capabilities.getDiagnosticProvider() != null
                && capabilities.getDiagnosticProvider().isWorkspaceDiagnostics()) {
            identifiers.add(capabilities.getDiagnosticProvider().getIdentifier());
        }
        for (var option : getOptions()) {
            if (option.isWorkspaceDiagnostics()) {
                identifiers.add(option.getIdentifier());
            }
        }
        return identifiers;
    }

    /**
     * Returns the diagnostic identifier declared in the {@link DiagnosticRegistrationOptions} and null otherwise.
     *
     * @return the diagnostic identifier declared in the {@link DiagnosticRegistrationOptions} and null otherwise.
     */
    public @Nullable String getDiagnosticIdentifier() {
        for (var option : getOptions()) {
            String identifier = option.getIdentifier();
            if (!StringUtils.isEmpty(identifier)) {
                return identifier;
            }
        }
        var serverCapabilities = getServerCapabilities();
        return serverCapabilities != null && serverCapabilities.getDiagnosticProvider() != null  ? serverCapabilities.getDiagnosticProvider().getIdentifier() : null;
    }
}

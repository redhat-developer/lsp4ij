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
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract class for any LSP feature.
 */
@ApiStatus.Experimental
public abstract class AbstractLSPFeature implements LSPClientFeatureAware, Disposable {

    private LSPClientFeatures clientFeatures;

    /**
     * Returns the LSP server support.
     *
     * @return the LSP server support.
     */
    public final @NotNull LSPClientFeatures getClientFeatures() {
        return clientFeatures;
    }

    /**
     * Returns the project.
     *
     * @return the project.
     */
    @NotNull
    public final Project getProject() {
        return clientFeatures.getProject();
    }

    /**
     * Returns the server status.
     *
     * @return the server status.
     */
    @NotNull
    public final ServerStatus getServerStatus() {
        return getClientFeatures().getServerStatus();
    }

    /**
     * Returns the language server definition.
     *
     * @return the language server definition.
     */
    @NotNull
    public final LanguageServerDefinition getServerDefinition() {
        return getClientFeatures().getServerDefinition();
    }

    /**
     * Returns true if the given language server id matches the server definition and false otherwise.
     *
     * @param languageServerId the language server id.
     * @return true if the given language server id matches the server definition and false otherwise.
     */
    public boolean isServerDefinition(@NotNull String languageServerId) {
        return getClientFeatures().isServerDefinition(languageServerId);
    }

    /**
     * Returns the LSP4J language server.
     *
     * @return the LSP4J language server.
     */
    @Nullable
    public final LanguageServer getLanguageServer() {
        return getClientFeatures().getLanguageServer();
    }

    /**
     * Set the LSP client features.
     *
     * @param clientFeatures the LSP client features.
     */
    @ApiStatus.Internal
    @Override
    public final void setClientFeatures(@NotNull LSPClientFeatures clientFeatures) {
        this.clientFeatures = clientFeatures;
    }

    @Override
    public void dispose() {
    }

    public abstract void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities);
}

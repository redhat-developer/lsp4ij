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

import com.redhat.devtools.lsp4ij.ServerStatus;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import static com.redhat.devtools.lsp4ij.server.capabilities.TextDocumentServerCapabilityRegistry.hasCapability;

/**
 * LSP workspace symbol feature.
 */
@ApiStatus.Experimental
public class LSPWorkspaceSymbolFeature extends AbstractLSPWorkspaceFeature {

    @Override
    public boolean isEnabled() {
        var serverStatus = getServerStatus();
        return serverStatus == ServerStatus.starting || serverStatus == ServerStatus.started;
    }

    @Override
    public boolean isSupported() {
        var serverCapabilities = getClientFeatures().getServerWrapper().getServerCapabilitiesSync();
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getWorkspaceSymbolProvider());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        // Do nothing
    }

    /**
     * Determines whether or not the language server's workspace/symbol feature implementation is efficient enough to
     * support the JetBrains' IDE's gotoClassContributor EP. In general, the action associated with that EP is invoked
     * much more frequently than the action associated with the IDE's gotoSymbolContributor, so this method should only
     * return true if the language server's implementation of workspace/symbol can realistically accommodate those
     * requests.
     *
     * @return true if the language server's workspace/symbol feature can support the IDE's gotoClassContributor EP
     * efficiently; otherwise false
     */
    public boolean supportsGotoClass() {
        // Default to unsupported
        return false;
    }
}

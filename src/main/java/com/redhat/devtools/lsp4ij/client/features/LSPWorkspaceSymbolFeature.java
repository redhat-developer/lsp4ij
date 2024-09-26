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

import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.ServerStatus;
import org.jetbrains.annotations.ApiStatus;

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
        return LanguageServerItem.isWorkspaceSymbolSupported(getClientFeatures().getServerWrapper().getServerCapabilitiesSync());
    }
}

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
package com.redhat.devtools.lsp4ij.features.workspaceSymbol;

import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.jetbrains.annotations.NotNull;

/**
 * Extension of {@link WorkspaceSymbolParams} that includes additional parameters specific to LSP4IJ.
 */
class LSPWorkspaceSymbolParams extends WorkspaceSymbolParams {

    // Use transient to avoid serializing the fields when GSON will be processed
    private transient final LSPWorkspaceRequestedSymbolTypes requestedSymbolTypes;

    LSPWorkspaceSymbolParams(@NotNull String query, @NotNull LSPWorkspaceRequestedSymbolTypes requestedSymbolTypes) {
        super(query);
        this.requestedSymbolTypes = requestedSymbolTypes;
    }

    /**
     * Returns the types of symbols being requested.
     *
     * @return the types of symbols being requested
     */
    @NotNull
    LSPWorkspaceRequestedSymbolTypes getRequestedSymbolTypes() {
        return requestedSymbolTypes;
    }
}

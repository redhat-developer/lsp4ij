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

import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceSymbolFeature;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.jetbrains.annotations.NotNull;

/**
 * Extension of {@link WorkspaceSymbolParams} that includes additional parameters specific to LSP4IJ.
 */
class LSPWorkspaceSymbolParams extends WorkspaceSymbolParams {

    LSPWorkspaceSymbolParams(@NotNull String query) {
        super(query);
    }

    public boolean canSupport(@NotNull LSPWorkspaceSymbolFeature feature) {
        return true;
    }

    /**
     * Determines whether or not the provided symbol should be included in the contributor's symbol list.
     *
     * @param symbol the symbol
     * @return true if the symbol should be include; otherwise false
     */
    public boolean accept(@NotNull WorkspaceSymbol symbol) {
        return true;
    }

    /**
     * Determines whether or not the provided symbol should be included in the contributor's symbol list.
     *
     * @param symbol the symbol
     * @return true if the symbol should be include; otherwise false
     */
    public boolean accept(SymbolInformation symbol) {
        return true;
    }
}

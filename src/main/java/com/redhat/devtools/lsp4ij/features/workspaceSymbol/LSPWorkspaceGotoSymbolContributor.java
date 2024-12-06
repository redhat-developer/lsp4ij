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

import org.jetbrains.annotations.NotNull;

/**
 * LSP workspace goto symbol contributor.
 */
public class LSPWorkspaceGotoSymbolContributor extends AbstractLSPWorkspaceSymbolContributor {

    @Override
    protected @NotNull LSPWorkspaceSymbolParams createWorkspaceSymbolParams(@NotNull String query) {
        return new LSPWorkspaceSymbolParams(query);
    }
}
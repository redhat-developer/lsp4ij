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

import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * LSP workspace goto class contributor.
 */
public class LSPWorkspaceGotoClassContributor extends AbstractLSPWorkspaceSymbolContributor {

    private final static Set<SymbolKind> TYPE_SYMBOL_KINDS = Set.of(
            SymbolKind.Class,
            SymbolKind.Interface,
            SymbolKind.Enum,
            SymbolKind.Struct
    );

    public LSPWorkspaceGotoClassContributor() {
        super(LSPWorkspaceRequestedSymbolTypes.TYPE_SYMBOLS);
    }

    @Override
    protected boolean accept(@NotNull WorkspaceSymbolData item) {
        // Include only type symbols
        return TYPE_SYMBOL_KINDS.contains(item.getSymbolKind());
    }
}
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
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * LSP workspace/symbol params for selection of only type symbols in support of the IDE's Go To Class action.
 */
public class LSPWorkspaceSymbolForClassesParams extends LSPWorkspaceSymbolParams {

    private final static Set<SymbolKind> TYPE_SYMBOL_KINDS = Set.of(
            SymbolKind.Class,
            SymbolKind.Interface,
            SymbolKind.Enum,
            SymbolKind.Struct
    );

    LSPWorkspaceSymbolForClassesParams(@NotNull String query) {
        super(query);
    }

    @Override
    public boolean canSupport(@NotNull LSPWorkspaceSymbolFeature feature) {
        return feature.supportsGotoClass();
    }

    @Override
    public boolean accept(@NotNull WorkspaceSymbol symbol) {
        return TYPE_SYMBOL_KINDS.contains(symbol.getKind());
    }

    @Override
    public boolean accept(SymbolInformation symbol) {
        return (symbol != null) && TYPE_SYMBOL_KINDS.contains(symbol.getKind());
    }
}
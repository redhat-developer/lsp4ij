package com.redhat.devtools.lsp4ij.features.workspaceSymbol;

import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceSymbolFeature;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class LSPWorkspaceSymbolForClassesParams extends LSPWorkspaceSymbolParams{

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
        return TYPE_SYMBOL_KINDS.contains(symbol.getKind());
    }
}

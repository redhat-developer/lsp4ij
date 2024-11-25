package com.redhat.devtools.lsp4ij.features.workspaceSymbol;

import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * LSP workspace goto class contributor.
 */
public class LSPWorkspaceGotoClassContributor extends AbstractLSPWorkspaceSymbolContributor {

    private final static Logger LOGGER = LoggerFactory.getLogger(LSPWorkspaceGotoClassContributor.class);

    private final static Set<SymbolKind> TYPE_SYMBOL_KINDS = Set.of(
            SymbolKind.Class,
            SymbolKind.Interface,
            SymbolKind.Enum,
            SymbolKind.Struct
    );

    public LSPWorkspaceGotoClassContributor() {
        super(LOGGER);
    }

    @Override
    protected boolean accept(@NotNull WorkspaceSymbolData item) {
        // Include only type symbols
        return TYPE_SYMBOL_KINDS.contains(item.getSymbolKind());
    }
}
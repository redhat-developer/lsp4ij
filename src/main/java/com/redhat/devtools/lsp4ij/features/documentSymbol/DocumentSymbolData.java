/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * CppCXY
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentSymbol;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.NlsSafe;
import com.redhat.devtools.lsp4ij.ui.IconMapper;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


/**
 * LSP document symbol data.
 *
 * @param documentSymbol   the LSP document symbol
 */
record DocumentSymbolData(@NotNull DocumentSymbol documentSymbol) {

    private record LSPItemPresentation(String name, SymbolKind symbolKind, String locationString) implements ItemPresentation {

        public String name() {
            return name;
        }

        @Override
        public @NlsSafe @Nullable String getPresentableText() {
            return name();
        }

        @Override
        public @Nullable Icon getIcon(boolean unused) {
            return IconMapper.getIcon(symbolKind);
        }

        @Override
        public @NlsSafe @Nullable String getLocationString() {
            return locationString;
        }

    }

    public ItemPresentation getPresentation() {
        return new LSPItemPresentation(documentSymbol.getName(), documentSymbol.getKind(), documentSymbol.getDetail());
    }
}

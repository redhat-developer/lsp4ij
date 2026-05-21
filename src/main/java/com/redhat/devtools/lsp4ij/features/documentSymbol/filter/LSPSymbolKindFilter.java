/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentSymbol.filter;

import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.redhat.devtools.lsp4ij.features.documentSymbol.DocumentSymbolData;
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPDocumentSymbolStructureViewModel;
import com.redhat.devtools.lsp4ij.ui.IconMapper;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base class for LSP symbol kind filters in the structure view.
 * Filters can hide/show specific types of symbols based on their SymbolKind.
 */
public abstract class LSPSymbolKindFilter implements Filter {

    private final String id;
    private final String text;
    private final Set<SymbolKind> filteredKinds;
    private volatile ActionPresentation cachedPresentation;

    protected LSPSymbolKindFilter(@NotNull String id,
                                   @NotNull String text,
                                   @NotNull SymbolKind... kinds) {
        this.id = id;
        this.text = text;
        this.filteredKinds = Arrays.stream(kinds).collect(Collectors.toSet());
    }

    @Override
    public boolean isVisible(TreeElement treeNode) {
        if (treeNode instanceof LSPDocumentSymbolStructureViewModel.LSPDocumentSymbolViewElement element) {
            DocumentSymbolData data = element.getValue();
            if (data != null) {
                SymbolKind kind = data.getDocumentSymbol().getKind();
                return !filteredKinds.contains(kind);
            }
        }
        return true;
    }

    @Override
    public @NotNull ActionPresentation getPresentation() {
        if (cachedPresentation == null) {
            synchronized (this) {
                if (cachedPresentation == null) {
                    // Use IconMapper to get the dynamic icon for the first filtered kind
                    Icon icon = filteredKinds.isEmpty() ? null : IconMapper.getIcon(filteredKinds.iterator().next());
                    cachedPresentation = new ActionPresentationData(text, null, icon);
                }
            }
        }
        return cachedPresentation;
    }

    @Override
    public @NotNull String getName() {
        return id;
    }

    @Override
    public boolean isReverted() {
        return true;
    }
}

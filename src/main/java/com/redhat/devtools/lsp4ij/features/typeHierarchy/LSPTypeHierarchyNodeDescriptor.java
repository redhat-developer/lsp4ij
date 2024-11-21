/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and declaration
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.typeHierarchy;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.features.hierarchy.LSPHierarchyNodeDescriptor;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP type hierarchy node descriptor.
 */
public class LSPTypeHierarchyNodeDescriptor extends LSPHierarchyNodeDescriptor<TypeHierarchyItem> {

    protected LSPTypeHierarchyNodeDescriptor(@NotNull Project project,
                                             @Nullable NodeDescriptor parentDescriptor,
                                             @NotNull PsiElement element,
                                             @Nullable TypeHierarchyItem typeHierarchyItem) {
        super(project, parentDescriptor, element, typeHierarchyItem);
    }

    @Override
    protected @Nullable String getDetail(@Nullable TypeHierarchyItem hierarchyItem) {
        return hierarchyItem != null ? hierarchyItem.getDetail() : null;
    }

    @Override
    protected @Nullable SymbolKind getSymbolKind(@Nullable TypeHierarchyItem hierarchyItem) {
        return hierarchyItem != null ? hierarchyItem.getKind() : null;
    }
}

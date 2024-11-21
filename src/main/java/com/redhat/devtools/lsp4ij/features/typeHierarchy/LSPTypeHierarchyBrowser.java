/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.typeHierarchy;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import com.redhat.devtools.lsp4ij.features.hierarchy.LSPHierarchyNodeDescriptor;
import com.redhat.devtools.lsp4ij.features.hierarchy.LSPHierarchyItemPsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * LSP type hierarchy browser.
 */
public class LSPTypeHierarchyBrowser extends TypeHierarchyBrowserBase {

    public LSPTypeHierarchyBrowser(@NotNull PsiElement target) {
        super(target.getProject(), target);
    }

    @Override
    protected boolean isInterface(@NotNull PsiElement psiElement) {
        return false;
    }

    @Override
    protected boolean canBeDeleted(PsiElement psiElement) {
        return false;
    }

    @Override
    protected String getQualifiedName(PsiElement psiElement) {
        return "";
    }

    @Override
    protected @Nullable PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
        if (descriptor instanceof LSPHierarchyNodeDescriptor lspDescriptor) {
            return lspDescriptor.getPsiElement();
        }
        return null;
    }

    @Override
    protected void createTrees(@NotNull Map<? super @Nls String, ? super JTree> trees) {
        createTreeAndSetupCommonActions(trees, IdeActions.GROUP_TYPE_HIERARCHY_POPUP);
    }

    @Override
    protected @Nullable JPanel createLegendPanel() {
        return null;
    }

    @Override
    protected boolean isApplicableElement(@NotNull PsiElement element) {
        return element instanceof LSPPsiElement || element instanceof LSPHierarchyItemPsiElement;
    }

    @Override
    protected @Nullable HierarchyTreeStructure createHierarchyTreeStructure(@NotNull String type, @NotNull PsiElement psiElement) {
        if (getSupertypesHierarchyType().equals(type)) {
            return new LSPTypeHierarchySupertypesTreeStructure(myProject, psiElement);
        }
        if (getSubtypesHierarchyType().equals(type)) {
            return new LSPTypeHierarchySubtypesTreeStructure(myProject, psiElement);
        }
        return null;
    }

    @Override
    protected @Nullable Comparator<NodeDescriptor<?>> getComparator() {
        return null;
    }

    @Override
    protected @NotNull Map<String, Supplier<String>> getPresentableNameMap() {
        HashMap<String, Supplier<String>> map = new HashMap<>();
        // Disable type hierarchy
        // map.put(getTypeHierarchyType(), TypeHierarchyBrowserBase::getTypeHierarchyType);
        map.put(getSubtypesHierarchyType(), TypeHierarchyBrowserBase::getSubtypesHierarchyType);
        map.put(getSupertypesHierarchyType(), TypeHierarchyBrowserBase::getSupertypesHierarchyType);
        return map;
    }
}

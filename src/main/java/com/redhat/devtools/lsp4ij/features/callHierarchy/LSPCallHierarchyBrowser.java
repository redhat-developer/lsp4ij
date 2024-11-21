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
package com.redhat.devtools.lsp4ij.features.callHierarchy;

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.psi.PsiElement;
import com.intellij.ui.PopupHandler;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import com.redhat.devtools.lsp4ij.features.hierarchy.LSPHierarchyItemPsiElement;
import com.redhat.devtools.lsp4ij.features.hierarchy.LSPHierarchyNodeDescriptor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;

/**
 * LSP call hierarchy browser.
 */
public class LSPCallHierarchyBrowser extends CallHierarchyBrowserBase {

    public LSPCallHierarchyBrowser(@NotNull PsiElement target) {
        super(target.getProject(), target);
    }

    @Override
    protected @Nullable PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
        if (descriptor instanceof LSPHierarchyNodeDescriptor lspDescriptor) {
            return lspDescriptor.getPsiElement();
        }
        return null;
    }

    @Override
    protected void createTrees(@NotNull Map<? super @Nls String, ? super JTree> type2TreeMap) {
        ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction(IdeActions.GROUP_CALL_HIERARCHY_POPUP);

        JTree callerTree = createHierarchyTree(group);
        JTree calleeTree = createHierarchyTree(group);

        type2TreeMap.put(getCallerType(), callerTree);
        type2TreeMap.put(getCalleeType(), calleeTree);
    }

    private JTree createHierarchyTree(ActionGroup group) {
        JTree tree = createTree(false);
        PopupHandler.installPopupMenu(tree, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP);
        return tree;
    }

    @Override
    protected boolean isApplicableElement(@NotNull PsiElement element) {
        return element instanceof LSPPsiElement || element instanceof LSPHierarchyItemPsiElement;
    }

    @Override
    protected @Nullable HierarchyTreeStructure createHierarchyTreeStructure(@NotNull String type,
                                                                            @NotNull PsiElement psiElement) {
        if (getCallerType().equals(type)) {
            return new LSPCallHierarchyIncomingCallsTreeStructure(myProject, psiElement);
        }
        if (getCalleeType().equals(type)) {
            return new LSPCallHierarchyOutgoingCallsTreeStructure(myProject, psiElement);
        }
        return null;
    }

    @Override
    protected @Nullable Comparator<NodeDescriptor<?>> getComparator() {
        return null;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

}

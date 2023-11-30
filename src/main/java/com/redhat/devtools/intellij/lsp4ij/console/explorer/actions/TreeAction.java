/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
package com.redhat.devtools.intellij.lsp4ij.console.explorer.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.util.NlsActions;
import com.intellij.ui.treeStructure.Tree;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.intellij.lsp4ij.console.explorer.LanguageServerProcessTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.awt.*;

/**
 * Base class for Actions processed from the Language Server tree.
 */
public abstract class TreeAction extends AnAction {
    public final void actionPerformed(@NotNull AnActionEvent e) {
        Tree tree = getTree(e);
        if (tree == null) {
            return;
        }
        actionPerformed(tree, e);
    }

    /**
     * Returns the language server tree and null otherwise.
     *
     * @param e the action event.
     * @return the language server tree and null otherwise.
     */
    private Tree getTree(AnActionEvent e) {
        Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
        if (component instanceof Tree) {
            return (Tree) component;
        }
        return null;
    }

    /**
     * Returns the selected language server tree node and null otherwise.
     *
     * @param tree the tree.
     * @return the selected language server tree node and null otherwise.
     */
    protected LanguageServerWrapper getSelectedLanguageServer(Tree tree) {
        TreePath path = tree.getSelectionPath();
        Object node = path != null ? path.getLastPathComponent() : null;
        if (node instanceof LanguageServerProcessTreeNode) {
            return ((LanguageServerProcessTreeNode) node).getLanguageServer();
        }
        return null;
    }

    protected abstract void actionPerformed(@NotNull Tree tree, @NotNull AnActionEvent e);

}

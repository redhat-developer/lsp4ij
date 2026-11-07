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
package com.redhat.devtools.lsp4ij.console.explorer;

import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Language server node.
 */
public class LanguageServerTreeNode extends DefaultMutableTreeNode {

    private final LanguageServerDefinition serverDefinition;

    public LanguageServerTreeNode(LanguageServerDefinition serverDefinition) {
        this.serverDefinition = serverDefinition;
    }

    public LanguageServerDefinition getServerDefinition() {
        return serverDefinition;
    }

    /**
     * Returns the process node to show when nothing tells which language server instance is meant: the node of the
     * instance created first, as the language server manager takes it, or else the first node.
     *
     * @return the process node to show, and null if there is none.
     */
    public @Nullable LanguageServerProcessTreeNode getActiveProcessTreeNode() {
        if (super.getChildCount() == 0) {
            return null;
        }
        var first = (LanguageServerProcessTreeNode) super.getChildAt(0);
        var project = first.getLanguageServer().getProject();
        if (!project.isDisposed()) {
            for (var languageServer : LanguageServiceAccessor.getInstance(project).getStartedServers()) {
                if (serverDefinition.equals(languageServer.getServerDefinition())) {
                    var processTreeNode = findProcessTreeNode(languageServer.getInstanceRoot());
                    return processTreeNode != null ? processTreeNode : first;
                }
            }
        }
        return first;
    }

    /**
     * Returns the process node of the given language server: the node that shows it, or else the node of its root,
     * which shows the language server of that root that replaced it or that it replaced.
     *
     * @param languageServer the language server.
     * @return the process node of the given language server and null if it has none yet.
     */
    public @Nullable LanguageServerProcessTreeNode findProcessTreeNode(@NotNull LanguageServerWrapper languageServer) {
        for (int i = 0; i < super.getChildCount(); i++) {
            if (super.getChildAt(i) instanceof LanguageServerProcessTreeNode processTreeNode
                    && processTreeNode.getLanguageServer() == languageServer) {
                return processTreeNode;
            }
        }
        return findProcessTreeNode(languageServer.getInstanceRoot());
    }

    /**
     * Returns the process node of the language server instance with the given root, or of the language server without
     * a root for null.
     *
     * @param instanceRoot the root of the language server instance, or null.
     * @return the process node of the given root and null if there is none.
     */
    public @Nullable LanguageServerProcessTreeNode findProcessTreeNode(@Nullable WorkspaceFolder instanceRoot) {
        for (int i = 0; i < super.getChildCount(); i++) {
            if (super.getChildAt(i) instanceof LanguageServerProcessTreeNode processTreeNode
                    && processTreeNode.hasInstanceRoot(instanceRoot)) {
                return processTreeNode;
            }
        }
        return null;
    }

    /**
     * Returns the index at which the given process node goes, so that the nodes are sorted by the name of their
     * language server instance, the one without a root first.
     *
     * @param processTreeNode the process node.
     * @return the index at which the given process node goes.
     */
    int getInsertionIndex(@NotNull LanguageServerProcessTreeNode processTreeNode) {
        int index = 0;
        while (index < super.getChildCount()
                && super.getChildAt(index) instanceof LanguageServerProcessTreeNode other
                && LanguageServerProcessTreeNode.ORDER.compare(other, processTreeNode) <= 0) {
            index++;
        }
        return index;
    }

    public Icon getIcon() {
        return serverDefinition.getIcon();
    }

    public String getDisplayName() {
        return serverDefinition.getDisplayName();
    }
}

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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerStatus;
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
    private @Nullable Project project;

    public LanguageServerTreeNode(@NotNull LanguageServerDefinition serverDefinition, @Nullable Project project) {
        this.serverDefinition = serverDefinition;
        this.project = project;
    }

    public LanguageServerTreeNode(@NotNull LanguageServerDefinition serverDefinition) {
        this(serverDefinition, null);
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

    public void setProject(@Nullable Project project) {
        if (project != null) {
            this.project = project;
        }
    }

    public @Nullable Project getProject() {
        if (project != null) {
            return project;
        }
        if (super.getChildCount() > 0 && super.getChildAt(0) instanceof LanguageServerProcessTreeNode first) {
            return first.getLanguageServer().getProject();
        }
        return null;
    }

    public boolean isEnabled(@Nullable Project project) {
        Project p = project != null ? project : getProject();
        return p != null && serverDefinition.isEnabled(p);
    }

    public boolean isEnabled() {
        return isEnabled(null);
    }

    public @Nullable String getStatusBadge(@Nullable Project project) {
        if (!isEnabled(project)) {
            return "[disabled]";
        }
        int total = getChildCount();
        if (total == 0) {
            return null;
        }
        int startedCount = 0;
        int crashedCount = 0;
        int transitionalCount = 0;
        int instanceDisabledCount = 0;

        for (int i = 0; i < total; i++) {
            if (getChildAt(i) instanceof LanguageServerProcessTreeNode child) {
                ServerStatus status = child.getServerStatus();
                boolean enabled = child.getLanguageServer().isEnabled();
                boolean hasError = child.getLanguageServer().getServerError() != null;

                if (!enabled) {
                    instanceDisabledCount++;
                } else if (status == ServerStatus.started) {
                    startedCount++;
                } else if (status == ServerStatus.stopped && hasError) {
                    crashedCount++;
                } else if (isTransitional(status)) {
                    transitionalCount++;
                }
            }
        }

        if (instanceDisabledCount == total) {
            return "[disabled]";
        }
        if (startedCount == total) {
            return "[started]";
        }
        if (startedCount > 0) {
            return "[" + startedCount + "/" + total + " started]";
        }
        if (transitionalCount > 0) {
            return "[starting]";
        }
        if (crashedCount > 0) {
            return total > 1 ? "[" + crashedCount + "/" + total + " crashed]" : "[crashed]";
        }
        return "[stopped]";
    }

    public @Nullable String getStatusBadge() {
        return getStatusBadge(null);
    }

    public @Nullable String getToolTipText(@Nullable Project project) {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("<b>Language Server:</b> ").append(escapeHtml(getDisplayName()))
                .append(" (").append(escapeHtml(serverDefinition.getId())).append(")<br/>");
        boolean enabled = isEnabled(project);
        sb.append("<b>Status:</b> ").append(enabled ? "Enabled" : "Disabled");
        int total = getChildCount();
        if (total > 0) {
            sb.append("<br/><b>Instances:</b> ").append(total);
            String badge = getStatusBadge(project);
            if (badge != null) {
                sb.append(" ").append(escapeHtml(badge));
            }
        }
        Throwable error = findFirstError();
        if (error != null && error.getMessage() != null) {
            sb.append("<br/><b>Error:</b> ").append(escapeHtml(error.getMessage()));
        } else if (!enabled) {
            sb.append("<br/><b>Reason:</b> Server is disabled");
        }
        sb.append("</html>");
        return sb.toString();
    }

    public @Nullable String getToolTipText() {
        return getToolTipText(null);
    }

    private @Nullable Throwable findFirstError() {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof LanguageServerProcessTreeNode child) {
                Throwable err = child.getLanguageServer().getServerError();
                if (err != null) {
                    return err;
                }
            }
        }
        return null;
    }

    private static @NotNull String escapeHtml(@Nullable String text) {
        return text != null ? StringUtil.escapeXmlEntities(text) : "";
    }

    private static boolean isTransitional(ServerStatus status) {
        return status == ServerStatus.starting
                || status == ServerStatus.stopping
                || status == ServerStatus.checking_installed
                || status == ServerStatus.installing;
    }
}

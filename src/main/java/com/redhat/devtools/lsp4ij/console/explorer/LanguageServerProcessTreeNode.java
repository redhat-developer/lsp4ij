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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.Formats;
import com.intellij.ui.AnimatedIcon;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.ServerStatus;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Comparator;
import java.util.Objects;

/**
 * Language server process node. It shows the language server of its definition and root: a language server that
 * replaces it, with the same root, takes the node over, with the traces and logs so far, as a restarted language
 * server keeps them.
 */
public class LanguageServerProcessTreeNode extends DefaultMutableTreeNode {

    private static final Icon RUNNING_ICON = new AnimatedIcon.Default();

    /**
     * The order of the process nodes of a definition: by the name of their language server instance, the one without
     * a root first.
     */
    static final Comparator<LanguageServerProcessTreeNode> ORDER = Comparator.comparing(
            (LanguageServerProcessTreeNode node) -> node.getLanguageServer().getInstanceLabel(),
            Comparator.nullsFirst(Comparator.<String>naturalOrder()));

    private volatile LanguageServerWrapper languageServer;

    private final @Nullable WorkspaceFolder instanceRoot;

    private final DefaultTreeModel treeModel;

    private ServerStatus serverStatus;

    private long startTime = -1;

    private String displayName;

    public LanguageServerProcessTreeNode(LanguageServerWrapper languageServer, DefaultTreeModel treeModel) {
        this.languageServer = languageServer;
        this.instanceRoot = languageServer.getInstanceRoot();
        this.treeModel = treeModel;
        setServerStatus(ServerStatus.none);
    }

    /**
     * Returns true if this node shows the language server instance with the given root, or the language server without
     * a root for null.
     *
     * @param instanceRoot the root of a language server instance, or null.
     * @return true if this node shows the language server instance with the given root.
     */
    public boolean hasInstanceRoot(@Nullable WorkspaceFolder instanceRoot) {
        return this.instanceRoot == null
                ? instanceRoot == null
                : instanceRoot != null && Objects.equals(this.instanceRoot.getUri(), instanceRoot.getUri());
    }

    /**
     * Shows the given language server, which replaces the one of this node and has the same root.
     *
     * @param languageServer the language server.
     */
    void setLanguageServer(@NotNull LanguageServerWrapper languageServer) {
        this.languageServer = languageServer;
    }

    /**
     * Shows the current status again, for a change that has no status of its own, such as that of the enabled flag
     * of the definition.
     */
    void refresh() {
        setServerStatus(serverStatus);
    }

    public void setServerStatus(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
        displayName = getDisplayName(serverStatus);
        if (isEnabled()) {
            switch (serverStatus) {
                case starting:
                case stopping:
                case checking_installed:
                case installing:
                    startTime = System.currentTimeMillis();
                    break;
                case stopped:
                case started:
                case installed:
                    startTime = -1;
                    break;
            }
        } else {
            startTime = -1;
        }
        this.setUserObject(displayName);
        treeModel.nodeChanged(this);
    }

    private String getDisplayName(@NotNull ServerStatus serverStatus) {
        String instanceLabel = languageServer.getInstanceLabel();
        String statusName = getStatusName(serverStatus);
        return instanceLabel != null ? instanceLabel + ": " + statusName : statusName;
    }

    /**
     * Returns true if the language server of this node is enabled. A disposed one, whose node stays until another
     * language server with its root takes it over, counts as its definition does.
     */
    private boolean isEnabled() {
        var languageServer = this.languageServer;
        return languageServer.isDisposed()
                ? languageServer.getServerDefinition().isEnabled(languageServer.getProject())
                : languageServer.isEnabled();
    }

    private String getStatusName(@NotNull ServerStatus serverStatus) {
        if (isInstallStatus(serverStatus)) {
            return serverStatus.name();
        }
        if (!isEnabled()) {
            return "disabled";
        }
        Throwable serverError = languageServer.getServerError();
        StringBuilder name = new StringBuilder();
        if (serverError == null) {
            name.append(serverStatus.name());
        } else {
            name.append(serverStatus == ServerStatus.stopped ? "crashed" : serverStatus.name());
            int nbTryRestart = languageServer.getNumberOfRestartAttempts();
            int nbTryRestartMax = languageServer.getMaxNumberOfRestartAttempts();
            name.append(" [");
            name.append(nbTryRestart);
            name.append("/");
            name.append(nbTryRestartMax);
            name.append("]");
        }
        Long pid = languageServer.getCurrentProcessId();
        if (pid != null) {
            name.append(" pid:");
            name.append(pid);
        }
        return name.toString();
    }

    public LanguageServerWrapper getLanguageServer() {
        return languageServer;
    }

    public ServerStatus getServerStatus() {
        return serverStatus;
    }

    public Icon getIcon() {
        if (!isEnabled() && !isInstallStatus(serverStatus)) {
            return AllIcons.Actions.Cancel;
        }
        boolean hasError = languageServer.getServerError() != null;
        return switch (serverStatus) {
            case started -> {
                if (hasError) {
                    yield AllIcons.RunConfigurations.TestFailed;
                }
                yield AllIcons.Actions.Commit;
            }
            case stopped -> {
                if (hasError) {
                    yield AllIcons.RunConfigurations.TestError;
                }
                yield AllIcons.Actions.Suspend;
            }
            case installed -> AllIcons.Actions.Install;
            default -> RUNNING_ICON;
        };
    }

    private static boolean isInstallStatus(@NotNull ServerStatus serverStatus) {
        return serverStatus == ServerStatus.checking_installed ||
                serverStatus == ServerStatus.installing ||
                serverStatus == ServerStatus.installed;
    }

    public String getDisplayName() {
        return displayName;
    }

    public @Nullable String getElapsedTime() {
        if (!isEnabled()) {
            return null;
        }
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        return Formats.formatDuration(duration, "\u2009");
    }
}

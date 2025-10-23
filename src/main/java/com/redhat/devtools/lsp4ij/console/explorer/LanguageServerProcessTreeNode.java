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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * Language server process node.
 */
public class LanguageServerProcessTreeNode extends DefaultMutableTreeNode {

    private static final Icon RUNNING_ICON = new AnimatedIcon.Default();

    private final LanguageServerWrapper languageServer;

    private final DefaultTreeModel treeModel;

    private ServerStatus serverStatus;

    private long startTime = -1;

    private String displayName;

    public LanguageServerProcessTreeNode(LanguageServerWrapper languageServer, DefaultTreeModel treeModel) {
        this.languageServer = languageServer;
        this.treeModel = treeModel;
        setServerStatus(ServerStatus.none);
    }

    public void setServerStatus(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
        displayName = getDisplayName(serverStatus);
        if (languageServer.isEnabled()) {
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
        if (isInstallStatus(serverStatus)) {
            return serverStatus.name();
        }
        if (!languageServer.isEnabled()) {
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
        if (!languageServer.isEnabled() && !isInstallStatus(serverStatus)) {
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
        if (!languageServer.isEnabled()) {
            return null;
        }
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        return Formats.formatDuration(duration, "\u2009");
    }
}

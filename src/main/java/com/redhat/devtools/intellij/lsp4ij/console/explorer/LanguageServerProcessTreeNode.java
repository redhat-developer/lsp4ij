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
package com.redhat.devtools.intellij.lsp4ij.console.explorer;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.Formats;
import com.intellij.ui.AnimatedIcon;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.intellij.lsp4ij.ServerStatus;

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
    }

    public void setServerStatus(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
        displayName = getDisplayName(serverStatus);
        switch (serverStatus) {
            case starting:
            case stopping:
                startTime = System.currentTimeMillis();
                break;
            case stopped:
            case started:
                startTime = -1;
                break;
        }
        this.setUserObject(displayName);
        treeModel.nodeChanged(this);
    }

    private String getDisplayName(ServerStatus serverStatus) {
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
        if (!languageServer.isEnabled()) {
            return AllIcons.Actions.Cancel;
        }
        boolean hasError = languageServer.getServerError() != null;
        switch (serverStatus) {
            case started:
                if (hasError) {
                    return AllIcons.RunConfigurations.TestFailed;
                }
                return AllIcons.Actions.Commit;
            case stopped:
                if (hasError) {
                    return AllIcons.RunConfigurations.TestError;
                }
                return AllIcons.Actions.Suspend;
            default:
                return RUNNING_ICON;
        }
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getElapsedTime() {
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        return Formats.formatDuration(duration, "\u2009");
    }
}

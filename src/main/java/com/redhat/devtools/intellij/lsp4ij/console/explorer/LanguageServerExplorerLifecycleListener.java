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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.intellij.lsp4ij.ServerStatus;
import com.redhat.devtools.intellij.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.intellij.lsp4ij.lifecycle.LanguageServerLifecycleListener;
import com.redhat.devtools.intellij.lsp4ij.settings.UserDefinedLanguageServerSettings;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.HashMap;
import java.util.Map;

/**
 * Language server listener to refresh the language server explorer according to the server state and fill the LSP console.
 *
 * @author Angelo ZERR
 */
public class LanguageServerExplorerLifecycleListener implements LanguageServerLifecycleListener {

    private final Map<LanguageServerWrapper, TracingMessageConsumer> tracingPerServer = new HashMap<>(10);

    private boolean disposed;

    private final LanguageServerExplorer explorer;

    public LanguageServerExplorerLifecycleListener(LanguageServerExplorer explorer) {
        this.explorer = explorer;
    }

    @Override
    public void handleStatusChanged(LanguageServerWrapper languageServer) {
        ServerStatus serverStatus = languageServer.getServerStatus();
        boolean selectProcess = serverStatus == ServerStatus.starting;
        updateServerStatus(languageServer, serverStatus, selectProcess);
    }

    @Override
    public void handleLSPMessage(Message message, MessageConsumer messageConsumer, LanguageServerWrapper languageServer) {
        if (explorer.isDisposed()) {
            return;
        }
        LanguageServerProcessTreeNode processTreeNode = updateServerStatus(languageServer, null, false);
        ServerTrace serverTrace = getServerTrace(explorer.getProject(), languageServer.serverDefinition.id);
        if (serverTrace == ServerTrace.off) {
            return;
        }

        TracingMessageConsumer tracing = getLSPRequestCacheFor(languageServer);
        String log = tracing.log(message, messageConsumer, serverTrace);
        invokeLater(() -> showMessage(processTreeNode, log));
    }

    @Override
    public void handleError(LanguageServerWrapper languageServer, Throwable exception) {
        LanguageServerProcessTreeNode processTreeNode = updateServerStatus(languageServer, null, false);
        if (exception == null) {
            return;
        }

        invokeLater(() -> showError(processTreeNode, exception));
    }

    private TracingMessageConsumer getLSPRequestCacheFor(LanguageServerWrapper languageServer) {
        TracingMessageConsumer cache = tracingPerServer.get(languageServer);
        if (cache != null) {
            return cache;
        }
        synchronized (tracingPerServer) {
            cache = tracingPerServer.get(languageServer);
            if (cache != null) {
                return cache;
            }
            cache = new TracingMessageConsumer();
            tracingPerServer.put(languageServer, cache);
            return cache;
        }
    }


    private static ServerTrace getServerTrace(Project project, String languageServerId) {
        ServerTrace serverTrace = null;
        UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = UserDefinedLanguageServerSettings.getInstance(project).getLanguageServerSettings(languageServerId);
        if (settings != null) {
            serverTrace = settings.getServerTrace();
        }
        return serverTrace != null ? serverTrace : ServerTrace.off;
    }

    private LanguageServerTreeNode findLanguageServerTreeNode(LanguageServerWrapper languageServer) {
        var tree = explorer.getTree();
        DefaultMutableTreeNode top = (DefaultMutableTreeNode) tree.getModel().getRoot();
        for (int i = 0; i < top.getChildCount(); i++) {
            LanguageServerTreeNode node = (LanguageServerTreeNode) top.getChildAt(i);
            if (node.getServerDefinition().equals(languageServer.serverDefinition)) {
                return node;
            }
        }
        return null;
    }

    private LanguageServerProcessTreeNode updateServerStatus(LanguageServerWrapper languageServer, ServerStatus serverStatus, boolean selectProcess) {
        LanguageServerTreeNode serverNode = findLanguageServerTreeNode(languageServer);
        if (serverNode == null) {
            // Should never occur.
            return null;
        }
        var processTreeNode = serverNode.getActiveProcessTreeNode();
        if (processTreeNode == null) {
            var treeModel = explorer.getTreeModel();
            processTreeNode = new LanguageServerProcessTreeNode(languageServer, treeModel);
            if (serverStatus == null) {
                // compute the server status
                serverStatus = languageServer.getServerStatus();
            }
            selectProcess = true;
            serverNode.add(processTreeNode);
        }
        boolean serverStatusChanged = serverStatus != null && serverStatus != processTreeNode.getServerStatus();
        boolean updateUI = serverStatusChanged || selectProcess;
        if (updateUI) {
            final var node = processTreeNode;
            final var status = serverStatus;
            final var select = selectProcess;
            invokeLater(() -> {
                if (explorer.isDisposed()) {
                    return;
                }
                if (serverStatusChanged) {
                    node.setServerStatus(status);
                }
                if (select) {
                    explorer.selectAndExpand(node);
                }
            });
        }
        return processTreeNode;
    }

    private void showMessage(LanguageServerProcessTreeNode processTreeNode, String message) {
        if (explorer.isDisposed()) {
            return;
        }
        explorer.showMessage(processTreeNode, message);
    }

    private void showError(LanguageServerProcessTreeNode processTreeNode, Throwable exception) {
        if (explorer.isDisposed()) {
            return;
        }
        explorer.showError(processTreeNode, exception);
    }

    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void dispose() {
        disposed = true;
        tracingPerServer.clear();
    }

    private static void invokeLater(Runnable runnable) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            runnable.run();
        } else {
            ApplicationManager.getApplication().invokeLater(runnable);
        }
    }

}

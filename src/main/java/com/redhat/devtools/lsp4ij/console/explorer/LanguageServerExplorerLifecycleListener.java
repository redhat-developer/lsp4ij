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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;

import java.util.HashMap;
import java.util.Map;

import static com.redhat.devtools.lsp4ij.internal.ApplicationUtils.invokeLaterIfNeeded;

/**
 * Language server listener to refresh the language server explorer according to the server state and fill the LSP console.
 *
 * @author Angelo ZERR
 */
public class LanguageServerExplorerLifecycleListener implements LanguageServerLifecycleListener {

    private final Map<LanguageServerItem, TracingMessageConsumer> tracingPerServer = new HashMap<>(10);

    private boolean disposed;

    private final LanguageServerExplorer explorer;

    public LanguageServerExplorerLifecycleListener(LanguageServerExplorer explorer) {
        this.explorer = explorer;
    }

    @Override
    public void handleStatusChanged(LanguageServerItem languageServer) {
        ServerStatus serverStatus = languageServer.getServerStatus();
        boolean selectProcess = serverStatus == ServerStatus.starting;
        updateServerStatus(languageServer, serverStatus, selectProcess);
    }

    @Override
    public void handleLSPMessage(Message message, MessageConsumer messageConsumer, LanguageServerItem languageServer) {
        if (explorer.isDisposed()) {
            return;
        }
        LanguageServerProcessTreeNode processTreeNode = updateServerStatus(languageServer, null, false);
        ServerTrace serverTrace = getServerTrace(explorer.getProject(), languageServer.getServerDefinition().getId());
        if (serverTrace == ServerTrace.off) {
            return;
        }

        ApplicationManager.getApplication()
                .executeOnPooledThread(() -> {
                    TracingMessageConsumer tracing = getLSPRequestCacheFor(languageServer);
                    String log = tracing.log(message, messageConsumer, serverTrace);
                    invokeLaterIfNeeded(() -> showTrace(processTreeNode, log));
                });
    }

    @Override
    public void handleError(LanguageServerItem languageServer, Throwable exception) {
        LanguageServerProcessTreeNode processTreeNode = updateServerStatus(languageServer, null, false);
        if (exception == null) {
            return;
        }

        invokeLaterIfNeeded(() -> showError(processTreeNode, exception));
    }

    private TracingMessageConsumer getLSPRequestCacheFor(LanguageServerItem languageServer) {
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
        return serverTrace != null ? serverTrace : ServerTrace.getDefaultValue();
    }

    private LanguageServerProcessTreeNode updateServerStatus(LanguageServerItem languageServer, ServerStatus serverStatus, boolean selectProcess) {
        LanguageServerTreeNode serverNode = explorer.findNodeForServer(languageServer.getServerDefinition());
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
            invokeLaterIfNeeded(() -> {
                if (explorer.isDisposed()) {
                    return;
                }
                if (serverStatusChanged) {
                    node.setServerStatus(status);
                }
                if (select && !explorer.isEditingCommand(serverNode)) {
                    // The LSP console is selected only if the command used to start the language server is not editing.
                    explorer.selectAndExpand(node);
                }
            });
        }
        return processTreeNode;
    }

    private void showTrace(LanguageServerProcessTreeNode processTreeNode, String message) {
        if (explorer.isDisposed()) {
            return;
        }
        explorer.showTrace(processTreeNode, message);
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

}

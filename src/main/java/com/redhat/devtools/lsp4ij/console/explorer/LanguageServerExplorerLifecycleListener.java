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
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentLinkedQueue;

import static com.redhat.devtools.lsp4ij.internal.ApplicationUtils.invokeLaterIfNeeded;

/**
 * Language server listener to refresh the language server explorer according to the server state and fill the LSP console.
 *
 * @author Angelo ZERR
 */
@ApiStatus.Internal
public class LanguageServerExplorerLifecycleListener implements LanguageServerLifecycleListener {

    private static final long TRACE_FLUSH_DELAY_MS = 500L; // Debounce delay before flushing LSP traces
    private final LanguageServerExplorer explorer;
    private boolean disposed;

    public LanguageServerExplorerLifecycleListener(@NotNull LanguageServerExplorer explorer) {
        this.explorer = explorer;
    }

    @Override
    public void handleStatusChanged(LanguageServerWrapper languageServer) {
        ServerStatus serverStatus = languageServer.getServerStatus();
        boolean selectProcess = serverStatus == ServerStatus.starting;
        updateServerStatus(languageServer, serverStatus, selectProcess);
    }

    @Override
    public void handleLSPMessage(@NotNull Message message,
                                 @NotNull MessageConsumer messageConsumer,
                                 @NotNull LanguageServerWrapper languageServer) {
        if (explorer.isDisposed()) {
            return;
        }

        // Update UI server status
        @Nullable LanguageServerProcessTreeNode processTreeNode = updateServerStatus(languageServer, null, false);
        if (processTreeNode != null && languageServer.addTrace(message, messageConsumer)) {
            // Display traces in LSP console
            scheduleFlushLogs(languageServer, processTreeNode);
        }
    }

    private void scheduleFlushLogs(@NotNull LanguageServerWrapper languageServer,
                                   @NotNull LanguageServerProcessTreeNode processTreeNode) {
        var traceFlushAlarm = languageServer.getTraceFlushAlarm();
        if (traceFlushAlarm == null) {
            return;
        }
        traceFlushAlarm.addRequest(() -> {
            if (disposed || explorer.isDisposed()) return;

            // Get cached LSP traces
            ConcurrentLinkedQueue<LanguageServerWrapper.LSPTrace> traces = languageServer.getTraces();
            if (traces == null || traces.isEmpty()) return;

            // There are some LSP traces to display in the LSP console
            StringBuilder batch = new StringBuilder();
            LanguageServerWrapper.LSPTrace lspTrace;
            // Merge LSP traces in one String
            while ((lspTrace = traces.poll()) != null) {
                batch.append(lspTrace.toMessage());
            }

            // Flush logs in the UI Thread
            if (!batch.isEmpty()) {
                invokeLaterIfNeeded(() -> showTrace(processTreeNode, batch.toString()));
            }
        }, TRACE_FLUSH_DELAY_MS);
    }


    @Override
    public void handleError(@NotNull LanguageServerWrapper languageServer, @Nullable Throwable exception) {
        LanguageServerProcessTreeNode processTreeNode = updateServerStatus(languageServer, null, false);
        if (exception == null || processTreeNode == null) {
            return;
        }

        invokeLaterIfNeeded(() -> showError(processTreeNode, exception));
    }

    private @Nullable LanguageServerProcessTreeNode updateServerStatus(@NotNull LanguageServerWrapper languageServer,
                                                                       @Nullable ServerStatus serverStatus, boolean selectProcess) {
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
    }

}

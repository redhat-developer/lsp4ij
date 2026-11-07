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
    public void handleStatusChanged(@NotNull LanguageServerWrapper languageServer) {
        ServerStatus serverStatus = languageServer.getServerStatus();
        boolean selectProcess = (serverStatus == ServerStatus.starting || serverStatus ==  ServerStatus.installing);
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

    @Override
    public void handleDisposed(@NotNull LanguageServerWrapper languageServer) {
        // Its node stays, with its traces and logs, until a language server with its root takes it over
        invokeLaterIfNeeded(() -> {
            if (explorer.isDisposed()) {
                return;
            }
            var serverNode = explorer.findNodeForServer(languageServer.getServerDefinition());
            if (serverNode == null) {
                return;
            }
            var processTreeNode = serverNode.findProcessTreeNode(languageServer);
            if (processTreeNode != null && processTreeNode.getLanguageServer() == languageServer) {
                // The stop of a disposed language server says stopping and nothing after, but no process runs any more
                processTreeNode.setServerStatus(ServerStatus.stopped);
            }
            refreshProcessNodes(serverNode);
        });
    }

    private @Nullable LanguageServerProcessTreeNode updateServerStatus(@NotNull LanguageServerWrapper languageServer,
                                                                       @Nullable ServerStatus serverStatus,
                                                                       boolean selectProcess) {
        LanguageServerTreeNode serverNode = explorer.findNodeForServer(languageServer.getServerDefinition());
        if (serverNode == null) {
            // Should never occur.
            return null;
        }
        LanguageServerProcessTreeNode processTreeNode;
        boolean created = false;
        boolean takenOver = false;
        // Instances of one definition can start at the same time
        synchronized (serverNode) {
            processTreeNode = serverNode.findProcessTreeNode(languageServer.getInstanceRoot());
            if (processTreeNode != null && processTreeNode.getLanguageServer() != languageServer) {
                if (languageServer.isDisposed() || !isGone(processTreeNode.getLanguageServer())) {
                    // A late status of a language server which went, or which another one with its root replaced
                    return null;
                }
                // The node shows the language server that replaces the one it showed, as for a restart
                processTreeNode.setLanguageServer(languageServer);
                if (serverStatus == null) {
                    serverStatus = languageServer.getServerStatus();
                }
                takenOver = true;
            }
            if (processTreeNode == null) {
                if (languageServer.isDisposed()) {
                    // A late status must not bring back the node of a language server which went with its definition
                    return null;
                }
                var treeModel = explorer.getTreeModel();
                processTreeNode = new LanguageServerProcessTreeNode(languageServer, treeModel);
                if (serverStatus == null) {
                    // compute the server status
                    serverStatus = languageServer.getServerStatus();
                }
                selectProcess = true;
                serverNode.insert(processTreeNode, serverNode.getInsertionIndex(processTreeNode));
                created = true;
            }
        }
        boolean serverStatusChanged = serverStatus != null && serverStatus != processTreeNode.getServerStatus();
        boolean updateUI = serverStatusChanged || selectProcess || takenOver;
        if (updateUI) {
            final var node = processTreeNode;
            final var status = serverStatus;
            final var select = selectProcess;
            final var inserted = created;
            final var replaced = takenOver;
            invokeLaterIfNeeded(() -> {
                // The definition can have gone in the meantime, and the node with it
                if (explorer.isDisposed() || node.getRoot() != explorer.getTreeModel().getRoot()) {
                    return;
                }
                if (inserted) {
                    // An expanded language server node shows the new process node only after this. The tree reads the
                    // children again: another thread can have added a node, and an insert event would show it twice.
                    explorer.getTreeModel().nodeStructureChanged(serverNode);
                }
                // A status that comes once another language server took the node over is no longer the node's, and one
                // that comes after the disposal of its language server, as from another thread, must not outlast it
                if ((serverStatusChanged || replaced) && node.getLanguageServer() == languageServer) {
                    node.setServerStatus(languageServer.isDisposed() ? ServerStatus.stopped : status);
                    // A stop or start changes the enabled flag of the definition, which the other nodes show too
                    refreshProcessNodes(serverNode);
                }
                if (select && !explorer.isEditingCommand(serverNode)) {
                    // The LSP console is selected only if the command used to start the language server is not editing.
                    explorer.selectAndExpand(node);
                }
            });
        }
        return processTreeNode;
    }

    /**
     * Returns true if the given language server went or was replaced: it is disposed, or no longer one of the started
     * language servers, as the one made for an installation never is.
     */
    private static boolean isGone(@NotNull LanguageServerWrapper languageServer) {
        return languageServer.isDisposed()
                || languageServer.getProject().isDisposed()
                || !LanguageServiceAccessor.getInstance(languageServer.getProject()).getStartedServers().contains(languageServer);
    }

    private static void refreshProcessNodes(@NotNull LanguageServerTreeNode serverNode) {
        for (int i = 0; i < serverNode.getChildCount(); i++) {
            if (serverNode.getChildAt(i) instanceof LanguageServerProcessTreeNode processTreeNode) {
                processTreeNode.refresh();
            }
        }
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

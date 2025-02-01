/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.util.Key;
import com.redhat.devtools.lsp4ij.dap.configurations.extractors.NetworkAddressExtractor;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.descriptors.ServerReadyConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

/**
 * Tracker to notify that DAP server is ready to consume by DAP clients.
 * (DAP server is started and can be listened to a given port if DAP server uses Socket).
 */
public class DAPServerReadyTracker extends CompletableFuture<Void> implements ProcessListener {

    private static final Key<DAPServerReadyTracker> SERVER_READY_TRACKER_KEY = Key.create("dap.server.ready");

    private final @NotNull ServerReadyConfig config;
    private final @NotNull ProcessHandler processHandler;
    private @Nullable Integer port;
    private boolean foundedTrace;

    public DAPServerReadyTracker(@NotNull ServerReadyConfig config,
                                 @NotNull ProcessHandler processHandler) {
        this.config = config;
        this.processHandler = processHandler;
        this.port = DebugAdapterDescriptor.getServerPort(processHandler);
        processHandler.addProcessListener(this);
        processHandler.putUserData(SERVER_READY_TRACKER_KEY, this);
    }


    public @Nullable Integer getPort() {
        return port;
    }

    public CompletableFuture<Void> track() {
        if (!waitForTimeout()) {
            if (!waitForTrace(null)) {
                if (port == null) {
                    onServerReady();
                } else {
                    // wait for available socket...
                    CompletableFuture.supplyAsync(() -> {
                        while (!DAPServerReadyTracker.this.isDone()) {
                            // The tracker future has not been cancelled
                            // Check if socket is available for the given port
                            if (isSocketAvailable(InetAddress.getLoopbackAddress().getHostAddress(), port)) {
                                // The socket is available, notify onServerReady
                                break;
                            }
                        }
                        return null;
                    }).thenRun(() -> onServerReady());
                }
            }
        }
        return this;
    }

    private static boolean isSocketAvailable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void onServerReady() {
        processHandler.removeProcessListener(this);
        processHandler.putUserData(SERVER_READY_TRACKER_KEY, null);
        super.complete(null);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        processHandler.removeProcessListener(this);
        processHandler.putUserData(SERVER_READY_TRACKER_KEY, null);
        return super.cancel(mayInterruptIfRunning);
    }

    @NotNull
    public static DAPServerReadyTracker getServerReadyTracker(@NotNull ProcessHandler processHandler) {
        return processHandler.getUserData(SERVER_READY_TRACKER_KEY);
    }

    @Override
    public void startNotified(@NotNull ProcessEvent event) {
        waitForTimeout();
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        waitForTrace(event.getText());
    }

    private boolean waitForTimeout() {
        Integer connectTimeout = config.connectTimeout();
        if (connectTimeout != null && connectTimeout > 0) {
            if (processHandler.isStartNotified()) {
                // The process is started
                CompletableFuture.runAsync(() -> {
                    try {
                        // Wait for some ms...
                        Thread.sleep(connectTimeout);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).thenRun(() -> onServerReady()); // then notify that server is ready
            }
            return true;
        }
        return false;
    }

    private boolean waitForTrace(@Nullable String text) {
        try {
            if (!foundedTrace) {
                NetworkAddressExtractor trackTrace = config.waitForTrace();
                if (trackTrace != null) {
                    var result = trackTrace.extract(text);
                    if (result.matches()) {
                        // ex : text=DAP server listening at: 127.0.0.1:61537
                        if (port == null) {
                           String extractedPort = result.port();
                           if (extractedPort != null) {
                               // ex: extractedPort=61537
                               port = Integer.valueOf(extractedPort);
                           }
                        }
                        foundedTrace = true;
                    }
                }
                return trackTrace != null;
            }
        } finally {
            if (foundedTrace) {
                onServerReady();
            }
        }
        return false;
    }

}

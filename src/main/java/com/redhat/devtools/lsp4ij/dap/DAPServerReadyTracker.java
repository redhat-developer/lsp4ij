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
import com.redhat.devtools.lsp4ij.dap.features.DAPClientFeatures;
import com.redhat.devtools.lsp4ij.dap.features.ServerReadyConfig;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Tracker to notify that DAP server is ready to consume by DAP clients.
 * (DAP server is started and can be listened to a given port if DAP server uses Socket).
 */
public class DAPServerReadyTracker extends CompletableFuture<Void> implements ProcessListener {

    private static final Key<DAPServerReadyTracker> SERVER_READY_TRACKER_KEY = Key.create("dap.server.ready");

    private final @NotNull ServerReadyConfig config;
    private final @NotNull ProcessHandler processHandler;
    private final @Nullable Integer port;
    private boolean foundedTrace;

    public DAPServerReadyTracker(@NotNull ServerReadyConfig config,
                                 @NotNull ProcessHandler processHandler) {
        this.config = config;
        this.processHandler = processHandler;
        this.port = DAPClientFeatures.getServerPort(processHandler);
        processHandler.addProcessListener(this);
        processHandler.putUserData(SERVER_READY_TRACKER_KEY, this);
    }


    public @Nullable Integer getPort() {
        return port;
    }

    public CompletableFuture<Void> track() {
        if (!waitForTimeout()) {
            if (!waitForTrace(null)) {
                onServerReady();
            }
        }
        return this;
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
        Integer wait = config.waitForTimeout();
        if (wait != null && wait > 0) {
            if (processHandler.isStartNotified()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).thenRun(() -> onServerReady());
            }
            return true;
        }
        return false;
    }


    private boolean waitForTrace(@Nullable String text) {
        try {
            if (!foundedTrace) {
                String waitForTrace = config.waitForTrace();
                if (StringUtils.isNotBlank(waitForTrace)) {
                    foundedTrace = text != null && text.startsWith(waitForTrace);
                    return true;
                }
            }
        } finally {
            if (foundedTrace) {
                onServerReady();
            }
        }
        return false;
    }
}

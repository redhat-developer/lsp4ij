/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XDropFrameHandler;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import org.eclipse.lsp4j.debug.RestartFrameArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Debug Adapter Protocol (DAP) drop frame handler: "Reset Frame" restarts a stack frame with the 'restartFrame'
 * request, when the debug adapter supports it (supportsRestartFrame) and the frame doesn't say that it can't be
 * restarted (canRestart).
 */
public class DAPDropFrameHandler implements XDropFrameHandler {

    private final @NotNull DAPDebugProcess debugProcess;

    public DAPDropFrameHandler(@NotNull DAPDebugProcess debugProcess) {
        this.debugProcess = debugProcess;
    }

    @Override
    public boolean canDrop(@NotNull XStackFrame frame) {
        return frame instanceof DAPStackFrame dapFrame && dapFrame.canRestart();
    }

    @Override
    public void drop(@NotNull XStackFrame frame) {
        if (!(frame instanceof DAPStackFrame dapFrame) || !dapFrame.canRestart()) {
            return;
        }
        var server = dapFrame.getClient().getDebugProtocolServer();
        if (server == null) {
            return;
        }
        var session = debugProcess.getSession();
        var context = session.getSuspendContext();
        // The values of the frames of this stop can't be set once the program resumes, unless the frame isn't restarted
        var client = dapFrame.getClient();
        int ended = client.endExecutionEpochBeforeResuming();
        restartFrame(server, dapFrame.getFrameId(), session, context, () -> client.stoppedOrContinuedSince(ended), error -> {
            client.resumeRefused(ended);
            debugProcess.print(DAPBundle.message("dap.frames.restart.failed", error.getMessage()), ConsoleViewContentType.ERROR_OUTPUT);
        });
    }

    /**
     * Restarts the given stack frame. Once the debug adapter has answered, the session runs until the 'stopped' event
     * with the reason 'restart', which the debug adapter sends once the frame is restarted, suspends it in the
     * restarted frame. When the debug adapter can't restart the frame, the session stays suspended where it was. An
     * answer which comes once the debug adapter said that the program stopped or continued, or once another stop
     * suspended the session, leaves the session as it is (see {@link DAPClient#resumeWhenAnswered}).
     *
     * @param server             the debug adapter.
     * @param frameId            the DAP frame to restart.
     * @param session            the debug session.
     * @param context            the suspend context of the session when the frame is restarted.
     * @param stoppedOrContinued returns whether the debug adapter said that the program stopped or continued since the
     *                           request was sent.
     * @param failed             reports why the debug adapter can't restart the frame.
     * @return the future, completed once the debug adapter has answered.
     */
    static CompletableFuture<@Nullable Void> restartFrame(@NotNull IDebugProtocolServer server,
                                                          int frameId,
                                                          @NotNull XDebugSession session,
                                                          @Nullable XSuspendContext context,
                                                          @NotNull BooleanSupplier stoppedOrContinued,
                                                          @NotNull Consumer<Throwable> failed) {
        RestartFrameArguments args = new RestartFrameArguments();
        args.setFrameId(frameId);
        return DAPClient.resumeWhenAnswered(server.restartFrame(args), session, context, stoppedOrContinued, failed);
    }
}

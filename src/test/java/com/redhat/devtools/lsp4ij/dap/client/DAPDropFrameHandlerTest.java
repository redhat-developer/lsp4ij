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

import com.intellij.xdebugger.XDebugSession;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.RestartFrameArguments;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Reset Frame" restarts a frame with the 'restartFrame' request, when the debug adapter supports it and the frame
 * doesn't say that it can't be restarted; the session then runs until the debug adapter stops it in the restarted
 * frame, and stays suspended when the debug adapter can't restart the frame.
 */
public class DAPDropFrameHandlerTest {

    private final List<String> calls = new ArrayList<>();

    @Test
    void aDebugAdapterWithoutRestartFrameRestartsNoFrame() {
        var capabilities = new Capabilities();
        assertFalse(DAPStackFrame.canRestart(capabilities, frame(null)));
        assertFalse(DAPStackFrame.canRestart(capabilities, frame(true)));

        capabilities.setSupportsRestartFrame(false);
        assertFalse(DAPStackFrame.canRestart(capabilities, frame(null)));
        assertFalse(DAPStackFrame.canRestart(capabilities, frame(true)));
    }

    @Test
    void aFrameCanBeRestartedUnlessItSaysItCannot() {
        var capabilities = new Capabilities();
        capabilities.setSupportsRestartFrame(true);

        assertTrue(DAPStackFrame.canRestart(capabilities, frame(null)));
        assertTrue(DAPStackFrame.canRestart(capabilities, frame(true)));
        assertFalse(DAPStackFrame.canRestart(capabilities, frame(false)));
    }

    @Test
    void aDropRestartsTheFrameThenTheSessionRuns() {
        var failures = new ArrayList<Throwable>();

        DAPDropFrameHandler.restartFrame(server(CompletableFuture.completedFuture(null)), 7, session(), failures::add).join();

        assertEquals(List.of("restartFrame 7", "resumed"), calls);
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void theSessionRunsOnceTheDebugAdapterHasAnswered() {
        var answer = new CompletableFuture<Void>();

        var done = DAPDropFrameHandler.restartFrame(server(answer), 7, session(), error -> calls.add("failed"));

        // the session stays suspended until the debug adapter answers
        assertEquals(List.of("restartFrame 7"), calls);
        answer.complete(null);
        done.join();
        assertEquals(List.of("restartFrame 7", "resumed"), calls);
    }

    @Test
    void aFailedRestartLeavesTheSessionSuspended() {
        var failures = new ArrayList<Throwable>();
        var error = new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, "frame cannot be restarted", null));

        DAPDropFrameHandler.restartFrame(server(CompletableFuture.failedFuture(error)), 7, session(), failures::add).join();

        // the failure is reported, and the session is still suspended where it was
        assertEquals(List.of("restartFrame 7"), calls);
        assertEquals(List.of(error), failures);
    }

    @Test
    void aRestartStopsInTheRestartedFrameRatherThanAtABreakpoint() {
        var restart = new StoppedEventArguments();
        restart.setReason("restart");
        restart.setThreadId(1);

        assertFalse(DAPClient.isBreakpointStop(restart));
        assertNull(DAPClient.getStopBreakpoint(restart, id -> {
            throw new AssertionError("no breakpoint stopped the program");
        }, () -> {
            throw new AssertionError("no breakpoint stopped the program");
        }));
    }

    private static StackFrame frame(Boolean canRestart) {
        var frame = new StackFrame();
        frame.setId(7);
        frame.setName("main");
        frame.setCanRestart(canRestart);
        return frame;
    }

    private IDebugProtocolServer server(CompletableFuture<Void> answer) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),
                new Class[]{IDebugProtocolServer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "restartFrame" -> {
                        calls.add("restartFrame " + ((RestartFrameArguments) args[0]).getFrameId());
                        yield answer;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new AssertionError(method.getName());
                });
    }

    private XDebugSession session() {
        return (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class[]{XDebugSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "sessionResumed" -> {
                        calls.add("resumed");
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}

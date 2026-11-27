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
import com.intellij.xdebugger.frame.XSuspendContext;
import org.eclipse.lsp4j.debug.ReverseContinueArguments;
import org.eclipse.lsp4j.debug.StepBackArguments;
import org.eclipse.lsp4j.debug.SteppingGranularity;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step Back and Reverse Continue send 'stepBack' and 'reverseContinue' for a thread, with the granularity and
 * 'singleThread' of the steps. The session shows the program running once the debug adapter has answered, and stays
 * suspended when the debug adapter refuses.
 */
public class DAPClientReverseExecutionTest {

    private final List<String> calls = new ArrayList<>();
    private final XSuspendContext stop = new XSuspendContext() {
    };

    @Test
    void stepBackSendsTheThreadTheGranularityAndSingleThread() {
        var requests = new ArrayList<Object>();

        DAPClient.stepBack(server(requests, CompletableFuture.completedFuture(null)), 3, SteppingGranularity.INSTRUCTION, Boolean.TRUE).join();
        DAPClient.stepBack(server(requests, CompletableFuture.completedFuture(null)), 4, null, null).join();

        var stepBack = (StepBackArguments) requests.get(0);
        assertEquals(3, stepBack.getThreadId());
        assertEquals(SteppingGranularity.INSTRUCTION, stepBack.getGranularity());
        assertEquals(Boolean.TRUE, stepBack.getSingleThread());
        var defaultStepBack = (StepBackArguments) requests.get(1);
        assertEquals(4, defaultStepBack.getThreadId());
        assertNull(defaultStepBack.getGranularity(), "the DAP server's default granularity");
        assertNull(defaultStepBack.getSingleThread(), "every thread resumes");
    }

    @Test
    void reverseContinueSendsTheThreadAndSingleThread() {
        var requests = new ArrayList<Object>();

        DAPClient.reverseContinue(server(requests, CompletableFuture.completedFuture(null)), 3, Boolean.TRUE).join();
        DAPClient.reverseContinue(server(requests, CompletableFuture.completedFuture(null)), 4, null).join();

        var reverseContinue = (ReverseContinueArguments) requests.get(0);
        assertEquals(3, reverseContinue.getThreadId());
        assertEquals(Boolean.TRUE, reverseContinue.getSingleThread());
        var allThreads = (ReverseContinueArguments) requests.get(1);
        assertEquals(4, allThreads.getThreadId());
        assertNull(allThreads.getSingleThread(), "every thread resumes");
    }

    @Test
    void theSessionRunsOnceTheDebugAdapterHasAnswered() {
        var answer = new CompletableFuture<Void>();

        var done = DAPClient.resumeWhenAnswered(answer, session(stop), stop, () -> false, error -> calls.add("refused"));

        // the session stays suspended until the debug adapter answers
        assertTrue(calls.isEmpty(), calls.toString());
        answer.complete(null);
        done.join();
        assertEquals(List.of("resumed"), calls);
    }

    @Test
    void aRefusedRequestLeavesTheSessionSuspended() {
        var failures = new ArrayList<Throwable>();
        var error = new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed,
                "Reverse Continue is not supported. Only \"Step back\" is supported.", null));

        DAPClient.resumeWhenAnswered(CompletableFuture.failedFuture(error), session(stop), stop, () -> false,
                failures::add).join();

        // the refusal is reported, and the session is still suspended where it was
        assertTrue(calls.isEmpty(), calls.toString());
        assertEquals(List.of(error), failures);
    }

    @Test
    void aSessionWhichAnotherStopSuspendedMeanwhileStaysSuspended() {
        var nextStop = new XSuspendContext() {
        };

        DAPClient.resumeWhenAnswered(CompletableFuture.completedFuture(null), session(nextStop), stop, () -> false,
                error -> calls.add("refused")).join();

        assertTrue(calls.isEmpty(), calls.toString());
    }

    @Test
    void anAnswerWhichComesOnceTheDebugAdapterSaidThatTheProgramStoppedLeavesTheSessionAsItIs() {
        // The 'stopped' event, which the debug adapter sent before its answer, hasn't suspended the session at the new
        // stop yet: it does so meanwhile, which resuming the session would undo
        DAPClient.resumeWhenAnswered(CompletableFuture.completedFuture(null), session(stop), stop, () -> true,
                error -> calls.add("refused")).join();

        assertTrue(calls.isEmpty(), calls.toString());
    }

    private static IDebugProtocolServer server(List<Object> requests, CompletableFuture<Void> answer) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),
                new Class<?>[]{IDebugProtocolServer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "stepBack", "reverseContinue" -> {
                        requests.add(args[0]);
                        yield answer;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new AssertionError(method.getName());
                });
    }

    /**
     * Returns a debug session suspended at the given suspend context.
     */
    private XDebugSession session(@Nullable XSuspendContext context) {
        return (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class<?>[]{XDebugSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSuspendContext" -> context;
                    case "sessionResumed" -> {
                        calls.add("resumed");
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}

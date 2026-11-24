/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.ContinueResponse;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.ReverseContinueArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * At a breakpoint, the session logs the value of its "Evaluate and log" expression, and a breakpoint which
 * doesn't suspend resumes the program. A stop from which the debug adapter says that the thread continued is over.
 */
public class DAPClientBreakpointReachedTest {

    private final List<String> evaluated = new ArrayList<>();
    private final List<Integer> continued = new ArrayList<>();
    private final List<Boolean> continuedSingleThread = new ArrayList<>();
    private final List<Integer> reverseContinued = new ArrayList<>();
    private final List<String> logged = new ArrayList<>();
    // How many 'continue' requests were sent when the client was told that the program resumes
    private final List<Integer> resumings = new ArrayList<>();

    @Test
    void logsTheExpressionAndResumesWhenTheBreakpointDoesNotSuspend() {
        var server = server(CompletableFuture.completedFuture(result("21")));

        DAPClient.breakpointReached(session(false), server, breakpoint("n"), new XSuspendContext() {
        }, 3, 7, null, this::resuming, () -> false).join();

        assertEquals(List.of("n in frame 3"), evaluated);
        assertEquals(List.of("21"), logged);
        assertEquals(List.of(7), continued);
        assertEquals(Collections.singletonList(null), continuedSingleThread, "every thread resumes");
        assertEquals(List.of(0), resumings, "the client is told that the program resumes before 'continue' is sent");
    }

    @Test
    void resumesOnlyTheStoppedThreadWhenAStepResumedItAlone() {
        var server = server(CompletableFuture.completedFuture(result("21")));

        DAPClient.breakpointReached(session(false), server, breakpoint("n"), new XSuspendContext() {
        }, 3, 7, Boolean.TRUE, this::resuming, () -> false).join();

        // the threads that the step kept stopped stay stopped
        assertEquals(List.of(7), continued);
        assertEquals(List.of(Boolean.TRUE), continuedSingleThread);
    }

    @Test
    void resumesBackwardsWhenTheProgramRanBackwardsToTheBreakpoint() {
        var server = server(CompletableFuture.completedFuture(result("21")));

        // As at a breakpoint which Reverse Continue reached, of a debug adapter without logpoints
        DAPClient.breakpointReached(session(false), server, breakpoint("n"), new XSuspendContext() {
        }, 3, 7, null, true, this::resuming, () -> false).join();

        assertEquals(List.of("21"), logged);
        assertEquals(List.of(7), reverseContinued, "the program runs on backwards");
        assertEquals(List.of(), continued, "not forward");
    }

    @Test
    void staysStoppedWhenTheBreakpointSuspends() {
        var server = server(CompletableFuture.completedFuture(result("21")));

        DAPClient.breakpointReached(session(true), server, breakpoint("n"), new XSuspendContext() {
        }, 3, 7, null, this::resuming, () -> false).join();

        assertEquals(List.of("21"), logged);
        assertTrue(continued.isEmpty(), continued.toString());
        assertTrue(resumings.isEmpty(), "the program doesn't resume");
    }

    @Test
    void logsWhyTheExpressionCannotBeEvaluated() {
        var error = new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError, "There is no variable \"nope\"", null));
        var server = server(CompletableFuture.failedFuture(error));

        DAPClient.breakpointReached(session(false), server, breakpoint("nope"), new XSuspendContext() {
        }, 3, 7, null, this::resuming, () -> false).join();

        assertEquals(List.of("Cannot evaluate 'nope': There is no variable \"nope\""), logged);
        assertEquals(List.of(7), continued);
    }

    @Test
    void failsWhenTheProgramCannotBeResumed() {
        var server = server(CompletableFuture.completedFuture(result("21")), CompletableFuture.failedFuture(new IllegalStateException("not stopped")));

        var reached = DAPClient.breakpointReached(session(false), server, breakpoint("n"), new XSuspendContext() {
        }, 3, 7, null, this::resuming, () -> false);

        // the caller reports the failure, rather than leaving the program stopped without a word
        var error = assertThrows(CompletionException.class, reached::join);
        assertEquals("not stopped", error.getCause().getMessage());
    }

    @Test
    void evaluatesNothingWithoutAnExpression() {
        var server = server(CompletableFuture.completedFuture(result("21")));

        DAPClient.breakpointReached(session(true), server, breakpoint(null), new XSuspendContext() {
        }, 3, 7, null, this::resuming, () -> false).join();

        assertTrue(evaluated.isEmpty(), evaluated.toString());
        assertEquals(1, logged.size());
        assertNull(logged.get(0));
    }

    @Test
    void neitherSuspendsNorResumesWhenTheThreadContinuedWhileTheExpressionWasEvaluated() {
        var evaluation = new CompletableFuture<EvaluateResponse>();
        var server = server(evaluation);
        var threadContinued = new AtomicBoolean();

        var reached = DAPClient.breakpointReached(session(false), server, breakpoint("n"), new XSuspendContext() {
        }, 3, 7, null, this::resuming, threadContinued::get);
        // the debug adapter says that the thread continued before it answers 'evaluate'
        threadContinued.set(true);
        evaluation.complete(result("21"));
        reached.join();

        // the stop is over: the session isn't told, and the program, which runs, isn't resumed
        assertEquals(List.of("n in frame 3"), evaluated);
        assertTrue(logged.isEmpty(), logged.toString());
        assertTrue(continued.isEmpty(), continued.toString());
        assertTrue(resumings.isEmpty(), "the program doesn't resume");
    }

    @Test
    void resumesTheSessionWhenTheThreadContinuedWhileTheSessionWasBeingSuspended() {
        var server = server(CompletableFuture.completedFuture(result("21")));
        var context = new XSuspendContext() {
        };
        var threadContinued = new AtomicBoolean();
        var resumed = new ArrayList<String>();
        var session = proxy(XDebugSession.class, (method, args) -> switch (method) {
            case "breakpointReached" -> {
                // the 'continued' event arrives as the session is being suspended, and finds it not suspended yet
                threadContinued.set(true);
                logged.add((String) args[1]);
                yield true;
            }
            case "getSuspendContext" -> context;
            case "sessionResumed" -> {
                resumed.add("resumed");
                yield null;
            }
            default -> throw new AssertionError(method);
        });

        DAPClient.breakpointReached(session, server, breakpoint("n"), context, 3, 7, null, this::resuming, threadContinued::get).join();

        assertEquals(List.of("21"), logged);
        assertEquals(List.of("resumed"), resumed);
        assertTrue(continued.isEmpty(), "the program, which runs, isn't resumed: " + continued);
    }

    private void resuming() {
        resumings.add(continued.size());
    }

    private XDebugSession session(boolean suspends) {
        return proxy(XDebugSession.class, (method, args) -> {
            if (method.equals("breakpointReached")) {
                logged.add((String) args[1]);
                return suspends;
            }
            throw new AssertionError(method);
        });
    }

    private IDebugProtocolServer server(CompletableFuture<EvaluateResponse> evaluation) {
        return server(evaluation, CompletableFuture.completedFuture(new ContinueResponse()));
    }

    private IDebugProtocolServer server(CompletableFuture<EvaluateResponse> evaluation, CompletableFuture<ContinueResponse> continuation) {
        return proxy(IDebugProtocolServer.class, (method, args) -> switch (method) {
            case "evaluate" -> {
                var arguments = (EvaluateArguments) args[0];
                evaluated.add(arguments.getExpression() + " in frame " + arguments.getFrameId());
                yield evaluation;
            }
            case "continue_" -> {
                continued.add(((ContinueArguments) args[0]).getThreadId());
                continuedSingleThread.add(((ContinueArguments) args[0]).getSingleThread());
                yield continuation;
            }
            case "reverseContinue" -> {
                reverseContinued.add(((ReverseContinueArguments) args[0]).getThreadId());
                yield CompletableFuture.completedFuture(null);
            }
            default -> throw new AssertionError(method);
        });
    }

    private static XBreakpoint<?> breakpoint(String logExpression) {
        XExpression expression = logExpression == null ? null : proxy(XExpression.class, (method, args) -> switch (method) {
            case "getExpression" -> logExpression;
            default -> null;
        });
        return proxy(XBreakpoint.class, (method, args) -> switch (method) {
            case "getLogExpressionObject" -> expression;
            default -> throw new AssertionError(method);
        });
    }

    private static EvaluateResponse result(String value) {
        var response = new EvaluateResponse();
        response.setResult(value);
        return response;
    }

    private interface Answer {
        Object answer(String method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Answer answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> type.getSimpleName();
            default -> answer.answer(method.getName(), args);
        });
    }
}

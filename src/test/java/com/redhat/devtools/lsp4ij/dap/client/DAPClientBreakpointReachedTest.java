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
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * At a breakpoint, the session logs the value of its "Evaluate and log" expression, and a breakpoint which
 * doesn't suspend resumes the program.
 */
public class DAPClientBreakpointReachedTest {

    private final List<String> evaluated = new ArrayList<>();
    private final List<Integer> continued = new ArrayList<>();
    private final List<String> logged = new ArrayList<>();

    @Test
    void logsTheExpressionAndResumesWhenTheBreakpointDoesNotSuspend() {
        var server = server(CompletableFuture.completedFuture(result("21")));

        DAPClient.breakpointReached(session(false), server, breakpoint("n"), new XSuspendContext() {
        }, 3, 7).join();

        assertEquals(List.of("n in frame 3"), evaluated);
        assertEquals(List.of("21"), logged);
        assertEquals(List.of(7), continued);
    }

    @Test
    void staysStoppedWhenTheBreakpointSuspends() {
        var server = server(CompletableFuture.completedFuture(result("21")));

        DAPClient.breakpointReached(session(true), server, breakpoint("n"), new XSuspendContext() {
        }, 3, 7).join();

        assertEquals(List.of("21"), logged);
        assertTrue(continued.isEmpty(), continued.toString());
    }

    @Test
    void logsWhyTheExpressionCannotBeEvaluated() {
        var error = new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError, "There is no variable \"nope\"", null));
        var server = server(CompletableFuture.failedFuture(error));

        DAPClient.breakpointReached(session(false), server, breakpoint("nope"), new XSuspendContext() {
        }, 3, 7).join();

        assertEquals(List.of("Cannot evaluate 'nope': There is no variable \"nope\""), logged);
        assertEquals(List.of(7), continued);
    }

    @Test
    void failsWhenTheProgramCannotBeResumed() {
        var server = server(CompletableFuture.completedFuture(result("21")), CompletableFuture.failedFuture(new IllegalStateException("not stopped")));

        var reached = DAPClient.breakpointReached(session(false), server, breakpoint("n"), new XSuspendContext() {
        }, 3, 7);

        // the caller reports the failure, rather than leaving the program stopped without a word
        var error = assertThrows(CompletionException.class, reached::join);
        assertEquals("not stopped", error.getCause().getMessage());
    }

    @Test
    void evaluatesNothingWithoutAnExpression() {
        var server = server(CompletableFuture.completedFuture(result("21")));

        DAPClient.breakpointReached(session(true), server, breakpoint(null), new XSuspendContext() {
        }, 3, 7).join();

        assertTrue(evaluated.isEmpty(), evaluated.toString());
        assertEquals(1, logged.size());
        assertNull(logged.get(0));
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
                yield continuation;
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
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> type.getSimpleName();
            default -> answer.answer(method.getName(), args);
        });
    }
}

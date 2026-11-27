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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step Back and Reverse Continue send 'stepBack' and 'reverseContinue' for a thread, with the granularity and
 * 'singleThread' of the steps. The session shows the program running once the debug adapter has answered, and stays
 * suspended when the debug adapter refuses. The resume doesn't interleave with the suspension at a stop.
 */
public class DAPClientReverseExecutionTest {

    private static final long BOUND_SECONDS = 30;

    private final DAPSessionChanges sessionChanges = new DAPSessionChanges();
    private final List<String> calls = new CopyOnWriteArrayList<>();
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

        var done = DAPClient.resumeWhenAnswered(answer, session(stop), sessionChanges, stop, () -> false, error -> calls.add("refused"));

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

        DAPClient.resumeWhenAnswered(CompletableFuture.failedFuture(error), session(stop), sessionChanges, stop, () -> false,
                failures::add).join();

        // the refusal is reported, and the session is still suspended where it was
        assertTrue(calls.isEmpty(), calls.toString());
        assertEquals(List.of(error), failures);
    }

    @Test
    void aSessionWhichAnotherStopSuspendedMeanwhileStaysSuspended() {
        var nextStop = new XSuspendContext() {
        };

        DAPClient.resumeWhenAnswered(CompletableFuture.completedFuture(null), session(nextStop), sessionChanges, stop, () -> false,
                error -> calls.add("refused")).join();

        assertTrue(calls.isEmpty(), calls.toString());
    }

    @Test
    void anAnswerWhichComesOnceTheDebugAdapterSaidThatTheProgramStoppedLeavesTheSessionAsItIs() {
        // The 'stopped' event, which the debug adapter sent before its answer, hasn't suspended the session at the new
        // stop yet: it does so meanwhile, which resuming the session would undo
        DAPClient.resumeWhenAnswered(CompletableFuture.completedFuture(null), session(stop), sessionChanges, stop, () -> true,
                error -> calls.add("refused")).join();

        assertTrue(calls.isEmpty(), calls.toString());
    }

    @Test
    void aStopWhichComesWhileTheAnswerResumesTheSessionSuspendsItOnceItResumed() {
        var session = session(() -> stop, () -> {
            // The stop comes on another thread, which has its frames, while the answer resumes the session: that
            // thread doesn't wait
            await(CompletableFuture.runAsync(() -> sessionChanges.execute(() -> calls.add("suspended at the stop"))),
                    "the thread of the stop returns");
            calls.add("resumed");
        });

        DAPClient.resumeWhenAnswered(CompletableFuture.completedFuture(null), session, sessionChanges, stop, () -> false,
                error -> calls.add("refused")).join();

        assertEquals(List.of("resumed", "suspended at the stop"), calls);
    }

    @Test
    void anAnswerWhichComesWhileAnotherStopSuspendsTheSessionLeavesTheSessionAtThatStop() throws Exception {
        var context = new AtomicReference<XSuspendContext>(stop);
        var session = session(context::get, () -> calls.add("resumed"));
        var suspending = new CompletableFuture<Void>();
        var mayEnd = new CompletableFuture<Void>();
        var nextStop = new XSuspendContext() {
        };
        // Another stop, such as one of a child session, suspends the session on another thread
        var stopped = CompletableFuture.runAsync(() -> sessionChanges.execute(() -> {
            suspending.complete(null);
            await(mayEnd, "the test lets the stop suspend the session");
            context.set(nextStop);
        }));
        await(suspending, "the stop suspends the session");

        var done = DAPClient.resumeWhenAnswered(CompletableFuture.completedFuture(null), session, sessionChanges, stop,
                () -> false, error -> calls.add("refused"));
        // The thread which handles the answer doesn't wait for the stop
        assertFalse(done.isDone(), "the answer is handled once the stop suspended the session");
        mayEnd.complete(null);
        stopped.get(BOUND_SECONDS, TimeUnit.SECONDS);
        done.get(BOUND_SECONDS, TimeUnit.SECONDS);

        assertTrue(calls.isEmpty(), "the session stays at the stop: " + calls);
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
        return session(() -> context, () -> calls.add("resumed"));
    }

    /**
     * Returns a debug session suspended at the suspend context which the given supplier returns, which runs the given
     * runnable when it resumes.
     */
    private static XDebugSession session(@NotNull Supplier<@Nullable XSuspendContext> context, @NotNull Runnable resumed) {
        return (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class<?>[]{XDebugSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSuspendContext" -> context.get();
                    case "sessionResumed" -> {
                        resumed.run();
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static void await(@NotNull CompletableFuture<?> future, @NotNull String what) {
        try {
            future.get(BOUND_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError(what, e);
        }
    }
}

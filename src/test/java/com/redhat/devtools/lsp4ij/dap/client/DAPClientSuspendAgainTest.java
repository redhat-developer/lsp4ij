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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * When the debug adapter refuses a Resume or a step, which IntelliJ resumed the session for, the session is suspended
 * again at the stop where the program still is, unless another stop suspended it meanwhile or the session ended. That
 * doesn't interleave with the suspension at another stop.
 */
public class DAPClientSuspendAgainTest {

    private static final long BOUND_SECONDS = 30;

    private final DAPSessionChanges sessionChanges = new DAPSessionChanges();
    private final List<XSuspendContext> reached = new CopyOnWriteArrayList<>();
    private final XSuspendContext stop = stop("the stop of the refused request");

    @Test
    void theSessionIsSuspendedAgainAtTheStopWhereTheProgramIs() {
        DAPClient.suspendAgain(session(null, false), sessionChanges, stop);

        assertEquals(List.of(stop), reached);
    }

    @Test
    void aSessionWhichAnotherStopSuspendedMeanwhileStaysThere() {
        var nextStop = stop("the next stop");

        DAPClient.suspendAgain(session(nextStop, false), sessionChanges, stop);

        assertEquals(List.of(), reached);
    }

    @Test
    void aSessionWhichEndedIsLeftAsItIs() {
        DAPClient.suspendAgain(session(null, true), sessionChanges, stop);

        assertEquals(List.of(), reached);
    }

    @Test
    void aStopWhichComesWhileTheSessionIsSuspendedAgainSuspendsItAfterwardsAndTheSessionStaysThere() {
        var nextStop = stop("the next stop");
        var stopCame = new AtomicBoolean();
        var session = new AtomicReference<XDebugSession>();
        session.set(session(null, false, () -> {
            if (stopCame.compareAndSet(false, true)) {
                // Another stop, such as one of another thread or of a child session, comes on another thread, which has
                // its frames, once the session was found running: that thread doesn't wait
                await(CompletableFuture.runAsync(() -> sessionChanges.execute(
                        () -> session.get().positionReached(nextStop))), "the thread of the stop returns");
            }
        }));

        DAPClient.suspendAgain(session.get(), sessionChanges, stop);

        assertEquals(List.of(stop, nextStop), reached);
        assertSame(nextStop, session.get().getSuspendContext(),
                "the session stays at the newer stop, where the program is");
    }

    /**
     * Returns a debug session suspended at the given suspend context, or running for null, which may have ended.
     */
    private @NotNull XDebugSession session(@Nullable XSuspendContext context, boolean stopped) {
        return session(context, stopped, () -> {
        });
    }

    /**
     * Returns a debug session suspended at the given suspend context, or running for null, which may have ended, and
     * which positionReached() suspends once it ran the given runnable.
     */
    private @NotNull XDebugSession session(@Nullable XSuspendContext context,
                                           boolean stopped,
                                           @NotNull Runnable reaching) {
        var suspendContext = new AtomicReference<>(context);
        return (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class<?>[]{XDebugSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSuspendContext" -> suspendContext.get();
                    case "isStopped" -> stopped;
                    case "positionReached" -> {
                        reaching.run();
                        var reachedAt = (XSuspendContext) args[0];
                        reached.add(reachedAt);
                        suspendContext.set(reachedAt);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static @NotNull XSuspendContext stop(@NotNull String name) {
        return new XSuspendContext() {
            @Override
            public String toString() {
                return name;
            }
        };
    }

    private static void await(@NotNull CompletableFuture<?> future, @NotNull String what) {
        try {
            future.get(BOUND_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError(what, e);
        }
    }
}

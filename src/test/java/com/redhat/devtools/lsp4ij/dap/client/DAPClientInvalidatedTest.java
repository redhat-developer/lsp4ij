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
import org.eclipse.lsp4j.debug.InvalidatedAreas;
import org.eclipse.lsp4j.debug.InvalidatedEventArguments;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An invalidated event shows again what the debug adapter says is out of date: the threads, the stacks or the
 * variables, or all of them.
 */
public class DAPClientInvalidatedTest {

    private final List<String> calls = new ArrayList<>();

    @Test
    void refreshesEverythingWhenTheEventNamesNoArea() {
        DAPClient.invalidated(session(true), event(null), this::refreshThreads, this::reloadStacks).join();

        assertEquals(List.of("threads", "stacks of every thread", "rebuild"), calls);
    }

    @Test
    void refreshesEverythingForAll() {
        DAPClient.invalidated(session(true), event(null, InvalidatedAreas.ALL), this::refreshThreads, this::reloadStacks).join();

        assertEquals(List.of("threads", "stacks of every thread", "rebuild"), calls);
    }

    @Test
    void rebuildsTheViewsForVariablesWithoutLoadingTheStacks() {
        DAPClient.invalidated(session(true), event(null, InvalidatedAreas.VARIABLES), this::refreshThreads, this::reloadStacks).join();

        assertEquals(List.of("rebuild"), calls);
    }

    @Test
    void reloadsTheStacksOfTheNamedThreadBeforeRebuildingTheViews() {
        var reloaded = new CompletableFuture<Void>();

        var done = DAPClient.invalidated(session(true), event(7, InvalidatedAreas.STACKS), this::refreshThreads, threadId -> {
            calls.add("stacks of thread " + threadId);
            return reloaded;
        });

        // the views show the stack frames, which are loaded again first
        assertEquals(List.of("stacks of thread 7"), calls);
        reloaded.complete(null);
        done.join();
        assertEquals(List.of("stacks of thread 7", "rebuild"), calls);
    }

    @Test
    void reloadsTheStacksOnceForAnAreaNamedTwice() {
        DAPClient.invalidated(session(true), event(null, InvalidatedAreas.STACKS, InvalidatedAreas.STACKS), this::refreshThreads, this::reloadStacks).join();

        assertEquals(List.of("stacks of every thread", "rebuild"), calls);
    }

    @Test
    void rebuildsTheViewsWhenTheStacksCannotBeLoaded() {
        DAPClient.invalidated(session(true), event(null), this::refreshThreads, threadId -> {
            calls.add("stacks of every thread");
            return CompletableFuture.failedFuture(new IllegalStateException("no stack trace"));
        }).join();

        // the variables are out of date too, and don't need the stack frames to be loaded again
        assertEquals(List.of("threads", "stacks of every thread", "rebuild"), calls);
    }

    @Test
    void refreshesTheThreadsAloneForThreads() {
        DAPClient.invalidated(session(true), event(null, InvalidatedAreas.THREADS), this::refreshThreads, this::reloadStacks).join();

        assertEquals(List.of("threads"), calls);
    }

    @Test
    void refreshesOnlyTheThreadsOfARunningProgram() {
        DAPClient.invalidated(session(false), event(null), this::refreshThreads, this::reloadStacks).join();

        assertEquals(List.of("threads"), calls);
        assertTrue(calls.stream().noneMatch("rebuild"::equals));
    }

    private void refreshThreads() {
        calls.add("threads");
    }

    private CompletableFuture<Void> reloadStacks(Integer threadId) {
        calls.add(threadId == null ? "stacks of every thread" : "stacks of thread " + threadId);
        return CompletableFuture.completedFuture(null);
    }

    private static InvalidatedEventArguments event(Integer threadId, String... areas) {
        var args = new InvalidatedEventArguments();
        args.setThreadId(threadId);
        if (areas.length > 0) {
            args.setAreas(areas);
        }
        return args;
    }

    private XDebugSession session(boolean suspended) {
        return (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class[]{XDebugSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isSuspended" -> suspended;
                    case "rebuildViews" -> {
                        calls.add("rebuild");
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}

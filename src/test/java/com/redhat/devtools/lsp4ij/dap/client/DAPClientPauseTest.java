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

import org.eclipse.lsp4j.debug.PauseArguments;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pausing a debug session pauses the first thread the debug adapter reports.
 */
public class DAPClientPauseTest {

    @Test
    void pausesTheFirstThreadTheAdapterReports() {
        var paused = new ArrayList<Integer>();
        var server = server(response(thread(3, "main"), thread(5, "worker")), paused);

        DAPClient.pauseFirstThread(server).join();

        assertEquals(List.of(3), paused);
    }

    @Test
    void pausesNothingWhenTheAdapterReportsNoThread() {
        var paused = new ArrayList<Integer>();

        DAPClient.pauseFirstThread(server(response(), paused)).join();
        DAPClient.pauseFirstThread(server(null, paused)).join();

        assertTrue(paused.isEmpty(), paused.toString());
    }

    private static IDebugProtocolServer server(ThreadsResponse threads, List<Integer> paused) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),
                new Class[]{IDebugProtocolServer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "threads" -> CompletableFuture.completedFuture(threads);
                    case "pause" -> {
                        paused.add(((PauseArguments) args[0]).getThreadId());
                        yield CompletableFuture.completedFuture(null);
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new AssertionError(method.getName());
                });
    }

    private static ThreadsResponse response(Thread... threads) {
        var response = new ThreadsResponse();
        response.setThreads(threads);
        return response;
    }

    private static Thread thread(int id, String name) {
        var thread = new Thread();
        thread.setId(id);
        thread.setName(name);
        return thread;
    }
}

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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.debug.TerminateThreadsArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * {@link DAPClient#terminateThreads(IDebugProtocolServer, List, Executor)} tests: the
 * 'terminateThreads' request has the IDs of the threads, and is sent off the EDT, which doesn't wait for it.
 */
public class DAPClientTerminateThreadsTest extends BasePlatformTestCase {

    /**
     * Bounds the waits for the request, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 30;

    public void testTheRequestHasTheIdsOfTheThreads() throws Exception {
        var sent = new LinkedBlockingQueue<TerminateThreadsArguments>();
        var server = server(args -> {
            sent.add(args);
            return CompletableFuture.completedFuture(null);
        });

        DAPClient.terminateThreads(server, List.of(3, 5), AppExecutorUtil.getAppExecutorService())
                .get(BOUND_SECONDS, TimeUnit.SECONDS);

        var args = sent.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'terminateThreads'", args);
        assertEquals(List.of(3, 5), Arrays.stream(args.getThreadIds()).boxed().toList());
    }

    public void testTheRequestIsSentOffTheEdtWhichDoesNotWaitForIt() throws Exception {
        assertTrue(ApplicationManager.getApplication().isDispatchThread());
        var release = new CountDownLatch(1);
        var sentOnEdt = new CompletableFuture<Boolean>();
        var server = server(args -> {
            sentOnEdt.complete(ApplicationManager.getApplication().isDispatchThread());
            // As a connection which doesn't take the request yet: LSP4J writes it on the thread which sends it
            try {
                release.await(BOUND_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return CompletableFuture.completedFuture(null);
        });

        var answered = DAPClient.terminateThreads(server, List.of(2), AppExecutorUtil.getAppExecutorService());

        assertFalse("The request is sent from another thread than the EDT", sentOnEdt.get(BOUND_SECONDS, TimeUnit.SECONDS));
        assertFalse("The EDT doesn't wait for the request", answered.isDone());
        release.countDown();
        answered.get(BOUND_SECONDS, TimeUnit.SECONDS);
    }

    private static @NotNull IDebugProtocolServer server(@NotNull Function<TerminateThreadsArguments, CompletableFuture<Void>> terminateThreads) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),
                new Class<?>[]{IDebugProtocolServer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "terminateThreads" -> terminateThreads.apply((TerminateThreadsArguments) args[0]);
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new AssertionError(method.getName());
                });
    }
}

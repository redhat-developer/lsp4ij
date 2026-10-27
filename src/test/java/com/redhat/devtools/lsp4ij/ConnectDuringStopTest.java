/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;

import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A document that connects while the language server stops does not deadlock with the stop. stop() holds the
 * wrapper's monitor while it waits for documentsLock, so connecting a document must not wait for that monitor while
 * it holds documentsLock, as it did when it asked for the document's didOpen under the lock.
 */
public class ConnectDuringStopTest extends LSPCodeInsightFixtureTestCase {

    public ConnectDuringStopTest() {
        super("*.foo");
    }

    public void testADocumentConnectingWhileTheServerStopsDoesNotHoldDocumentsLockForTheMonitor() throws Exception {
        var main = myFixture.configureByText("main.foo", "");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(main, null, null).get(30, TimeUnit.SECONDS).get(0).getServerWrapper();
        var other = myFixture.addFileToProject("other.foo", "other").getVirtualFile();
        var documentsLock = documentsLock(wrapper);

        var connection = new AtomicReference<CompletableFuture<?>>();
        var outcome = new CompletableFuture<String>();
        var stopping = new Thread(() -> {
            try {
                // as stop() does: it holds the wrapper's monitor, and then takes documentsLock
                synchronized (wrapper) {
                    connection.set(wrapper.connect(other, new LanguageServerWrapper.LSPFileConnectionInfo(null, null, null, true)));
                    // the connection goes on in a pooled thread, until it needs the monitor for the didOpen
                    var connecting = awaitBlockedForDidOpen(wrapper);
                    if (holds(connecting, documentsLock)) {
                        // taking documentsLock here would wait forever
                        outcome.complete("A document waits for the wrapper's monitor while it holds documentsLock:\n" + connecting);
                        return;
                    }
                    synchronized (documentsLock) {
                        outcome.complete(null);
                    }
                }
            } catch (Throwable e) {
                outcome.completeExceptionally(e);
            }
        }, "stopping while a document connects");
        stopping.setDaemon(true);
        stopping.start();

        var failure = outcome.get(30, TimeUnit.SECONDS);
        assertNull(failure, failure);
        // and once the monitor is free, the document connects
        assertNotNull(connection.get().get(30, TimeUnit.SECONDS));
    }

    /**
     * Waits for the pooled thread that connects the document to block on the wrapper's monitor, in the didOpen it
     * asks for.
     */
    private static ThreadInfo awaitBlockedForDidOpen(Object wrapper) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Optional<ThreadInfo> blocked = Arrays.stream(ManagementFactory.getThreadMXBean().dumpAllThreads(true, false))
                    .filter(info -> info.getThreadState() == Thread.State.BLOCKED
                            && info.getLockInfo() != null
                            && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(wrapper)
                            && Arrays.stream(info.getStackTrace()).anyMatch(frame ->
                            frame.getClassName().equals(DocumentContentSynchronizer.class.getName())
                                    && frame.getMethodName().equals("getDidOpenFutureSync")))
                    .findFirst();
            if (blocked.isPresent()) {
                return blocked.get();
            }
            Thread.sleep(10);
        }
        throw new AssertionError("The document never asked for its didOpen");
    }

    private static boolean holds(ThreadInfo thread, Object lock) {
        return Arrays.stream(thread.getLockedMonitors())
                .mapToInt(MonitorInfo::getIdentityHashCode)
                .anyMatch(hash -> hash == System.identityHashCode(lock));
    }

    private static Object documentsLock(LanguageServerWrapper wrapper) throws ReflectiveOperationException {
        var field = LanguageServerWrapper.class.getDeclaredField("documentsLock");
        field.setAccessible(true);
        return field.get(wrapper);
    }
}

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import junit.framework.TestCase;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;

import java.util.List;
import java.util.concurrent.*;

/**
 * Verifies that the message writer dispatch in {@link LanguageServerWrapper} routes
 * {@link NotificationMessage}s to a single-thread executor (preserving order) and all
 * other message types to a separate pool (so they are never blocked behind a slow
 * notification write).
 */
public class MessageWriterDispatchTest extends TestCase {

    private ExecutorService messageWriter;
    private ExecutorService requestWriter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        messageWriter = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-messageWriter"));
        requestWriter = Executors.newCachedThreadPool(r -> new Thread(r, "test-requestWriter"));
    }

    @Override
    protected void tearDown() throws Exception {
        messageWriter.shutdownNow();
        requestWriter.shutdownNow();
        super.tearDown();
    }

    public void testNotificationDispatchesToMessageWriter() throws Exception {
        var threadName = new CompletableFuture<String>();
        MessageConsumer consumer = message -> threadName.complete(Thread.currentThread().getName());

        var notification = new NotificationMessage();
        notification.setMethod("textDocument/didOpen");
        dispatch(consumer, notification);

        assertEquals("test-messageWriter", threadName.get(5, TimeUnit.SECONDS));
    }

    public void testRequestDispatchesToRequestWriter() throws Exception {
        var threadName = new CompletableFuture<String>();
        MessageConsumer consumer = message -> threadName.complete(Thread.currentThread().getName());

        var request = new RequestMessage();
        request.setMethod("textDocument/completion");
        dispatch(consumer, request);

        assertTrue(threadName.get(5, TimeUnit.SECONDS).startsWith("test-requestWriter"));
    }

    public void testResponseDispatchesToRequestWriter() throws Exception {
        var threadName = new CompletableFuture<String>();
        MessageConsumer consumer = message -> threadName.complete(Thread.currentThread().getName());

        var response = new ResponseMessage();
        response.setId("1");
        dispatch(consumer, response);

        assertTrue(threadName.get(5, TimeUnit.SECONDS).startsWith("test-requestWriter"));
    }

    public void testSlowNotificationDoesNotBlockRequest() throws Exception {
        var requestDone = new CompletableFuture<Void>();
        var notificationBlocked = new CountDownLatch(1);
        var releaseNotification = new CountDownLatch(1);

        MessageConsumer consumer = message -> {
            if (message instanceof NotificationMessage) {
                notificationBlocked.countDown();
                try {
                    releaseNotification.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                requestDone.complete(null);
            }
        };

        var notification = new NotificationMessage();
        notification.setMethod("textDocument/didOpen");
        dispatch(consumer, notification);
        assertTrue(notificationBlocked.await(5, TimeUnit.SECONDS));

        var request = new RequestMessage();
        request.setMethod("textDocument/completion");
        dispatch(consumer, request);

        requestDone.get(5, TimeUnit.SECONDS);

        releaseNotification.countDown();
    }

    public void testNotificationsAreSerializedOnSingleThread() throws Exception {
        var order = new CopyOnWriteArrayList<Integer>();
        var latch = new CountDownLatch(3);
        MessageConsumer consumer = message -> {
            order.add(Integer.parseInt(((NotificationMessage) message).getMethod()));
            latch.countDown();
        };

        for (int i = 1; i <= 3; i++) {
            var n = new NotificationMessage();
            n.setMethod(String.valueOf(i));
            dispatch(consumer, n);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(1, 2, 3), order);
    }

    /**
     * Mirrors the dispatch logic in {@link LanguageServerWrapper}: notifications go
     * to the single-thread messageWriter, everything else to the requestWriter pool.
     */
    private void dispatch(MessageConsumer consumer, Message message) {
        ExecutorService writer = message instanceof NotificationMessage
                ? messageWriter
                : requestWriter;
        CompletableFuture.runAsync(() -> consumer.consume(message), writer);
    }
}

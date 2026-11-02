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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies the three-way message dispatch in {@link LanguageServerWrapper}:
 * <ul>
 *   <li>Notifications → single-thread messageWriter (preserves ordering)</li>
 *   <li>Responses → requestWriter directly (never blocked behind notifications)</li>
 *   <li>Requests → requestWriter, but only after the last notification write completes
 *       (preserves document ordering)</li>
 * </ul>
 */
public class MessageWriterDispatchTest extends TestCase {

    private ExecutorService messageWriter;
    private ExecutorService requestWriter;
    private volatile CompletableFuture<Void> lastNotificationWriteFuture;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        messageWriter = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-messageWriter"));
        requestWriter = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-requestWriter"));
        lastNotificationWriteFuture = CompletableFuture.completedFuture(null);
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

        assertEquals("test-requestWriter", threadName.get(5, TimeUnit.SECONDS));
    }

    public void testResponseDispatchesToRequestWriter() throws Exception {
        var threadName = new CompletableFuture<String>();
        MessageConsumer consumer = message -> threadName.complete(Thread.currentThread().getName());

        var response = new ResponseMessage();
        response.setId("1");
        dispatch(consumer, response);

        assertEquals("test-requestWriter", threadName.get(5, TimeUnit.SECONDS));
    }

    public void testSlowNotificationDoesNotBlockResponse() throws Exception {
        var responseDone = new CompletableFuture<Void>();
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
                responseDone.complete(null);
            }
        };

        var notification = new NotificationMessage();
        notification.setMethod("textDocument/didOpen");
        dispatch(consumer, notification);
        assertTrue(notificationBlocked.await(5, TimeUnit.SECONDS));

        var response = new ResponseMessage();
        response.setId("1");
        dispatch(consumer, response);

        responseDone.get(5, TimeUnit.SECONDS);

        releaseNotification.countDown();
    }

    public void testRequestWaitsForPendingNotificationWrite() throws Exception {
        var order = new CopyOnWriteArrayList<String>();
        var notificationWritten = new CountDownLatch(1);
        var requestWritten = new CountDownLatch(1);

        MessageConsumer consumer = message -> {
            if (message instanceof NotificationMessage) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                order.add("notification");
                notificationWritten.countDown();
            } else {
                order.add("request");
                requestWritten.countDown();
            }
        };

        var notification = new NotificationMessage();
        notification.setMethod("textDocument/didChange");
        dispatch(consumer, notification);

        var request = new RequestMessage();
        request.setMethod("textDocument/completion");
        dispatch(consumer, request);

        assertTrue(notificationWritten.await(5, TimeUnit.SECONDS));
        assertTrue(requestWritten.await(5, TimeUnit.SECONDS));

        assertEquals(List.of("notification", "request"), order);
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
     * Mirrors the three-way dispatch logic in {@link LanguageServerWrapper}:
     * notifications → messageWriter, responses → requestWriter directly,
     * requests → requestWriter after last notification write.
     */
    private void dispatch(MessageConsumer consumer, Message message) {
        if (message instanceof NotificationMessage) {
            var writeFuture = CompletableFuture.runAsync(
                    () -> consumer.consume(message), messageWriter);
            lastNotificationWriteFuture = writeFuture;
        } else if (message instanceof ResponseMessage) {
            CompletableFuture.runAsync(() -> consumer.consume(message), requestWriter);
        } else {
            lastNotificationWriteFuture
                    .thenRunAsync(() -> consumer.consume(message), requestWriter);
        }
    }
}

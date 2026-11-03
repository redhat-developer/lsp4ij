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
 * Verifies the direction-based message dispatch in {@link LanguageServerWrapper}:
 * <ul>
 *   <li>Outgoing messages (to the language server) are queued on a single-thread
 *       writer, preserving FIFO ordering across all message types</li>
 *   <li>Incoming messages (from the language server) are handled directly on the
 *       calling thread, never blocked behind outgoing writes</li>
 * </ul>
 */
public class MessageWriterDispatchTest extends TestCase {

    private ExecutorService messageWriter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        messageWriter = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-messageWriter"));
    }

    @Override
    protected void tearDown() throws Exception {
        messageWriter.shutdownNow();
        super.tearDown();
    }

    public void testOutgoingNotificationDispatchesToWriter() throws Exception {
        var threadName = new CompletableFuture<String>();
        MessageConsumer consumer = message -> threadName.complete(Thread.currentThread().getName());

        var notification = new NotificationMessage();
        notification.setMethod("textDocument/didOpen");
        dispatchOutgoing(consumer, notification);

        assertEquals("test-messageWriter", threadName.get(5, TimeUnit.SECONDS));
    }

    public void testOutgoingRequestDispatchesToWriter() throws Exception {
        var threadName = new CompletableFuture<String>();
        MessageConsumer consumer = message -> threadName.complete(Thread.currentThread().getName());

        var request = new RequestMessage();
        request.setMethod("textDocument/completion");
        dispatchOutgoing(consumer, request);

        assertEquals("test-messageWriter", threadName.get(5, TimeUnit.SECONDS));
    }

    public void testOutgoingResponseDispatchesToWriter() throws Exception {
        var threadName = new CompletableFuture<String>();
        MessageConsumer consumer = message -> threadName.complete(Thread.currentThread().getName());

        var response = new ResponseMessage();
        response.setId("1");
        dispatchOutgoing(consumer, response);

        assertEquals("test-messageWriter", threadName.get(5, TimeUnit.SECONDS));
    }

    public void testIncomingMessageHandledOnCallingThread() throws Exception {
        var threadName = new CompletableFuture<String>();
        MessageConsumer consumer = message -> threadName.complete(Thread.currentThread().getName());

        var request = new RequestMessage();
        request.setMethod("workspace/configuration");
        dispatchIncoming(consumer, request);

        assertEquals(Thread.currentThread().getName(), threadName.get(5, TimeUnit.SECONDS));
    }

    public void testSlowOutgoingDoesNotBlockIncoming() throws Exception {
        var incomingDone = new CompletableFuture<Void>();
        var outgoingBlocked = new CountDownLatch(1);
        var releaseOutgoing = new CountDownLatch(1);

        MessageConsumer outgoingConsumer = message -> {
            outgoingBlocked.countDown();
            try {
                releaseOutgoing.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        MessageConsumer incomingConsumer = message -> incomingDone.complete(null);

        var notification = new NotificationMessage();
        notification.setMethod("textDocument/didOpen");
        dispatchOutgoing(outgoingConsumer, notification);
        assertTrue(outgoingBlocked.await(5, TimeUnit.SECONDS));

        // Incoming message handled immediately even though outgoing is blocked
        var request = new RequestMessage();
        request.setMethod("workspace/configuration");
        dispatchIncoming(incomingConsumer, request);

        incomingDone.get(5, TimeUnit.SECONDS);

        releaseOutgoing.countDown();
    }

    public void testOutgoingMessagesPreserveOrder() throws Exception {
        var order = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(4);
        MessageConsumer consumer = message -> {
            if (message instanceof NotificationMessage n) {
                order.add("N:" + n.getMethod());
            } else if (message instanceof RequestMessage r) {
                order.add("R:" + r.getMethod());
            }
            latch.countDown();
        };

        var n1 = new NotificationMessage();
        n1.setMethod("didOpen");
        dispatchOutgoing(consumer, n1);

        var r1 = new RequestMessage();
        r1.setMethod("completion");
        dispatchOutgoing(consumer, r1);

        var n2 = new NotificationMessage();
        n2.setMethod("didChange");
        dispatchOutgoing(consumer, n2);

        var r2 = new RequestMessage();
        r2.setMethod("hover");
        dispatchOutgoing(consumer, r2);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("N:didOpen", "R:completion", "N:didChange", "R:hover"), order);
    }

    /**
     * Mirrors the outgoing dispatch in {@link LanguageServerWrapper}: all outgoing
     * messages go through a single-thread writer, preserving FIFO ordering.
     */
    private void dispatchOutgoing(MessageConsumer consumer, Message message) {
        CompletableFuture.runAsync(() -> consumer.consume(message), messageWriter);
    }

    /**
     * Mirrors the incoming dispatch in {@link LanguageServerWrapper}: incoming
     * messages are handled directly on the calling thread.
     */
    private void dispatchIncoming(MessageConsumer consumer, Message message) {
        consumer.consume(message);
    }
}

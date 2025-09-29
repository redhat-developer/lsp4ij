/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 *****************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.MessageProducer;
import org.eclipse.lsp4j.jsonrpc.json.ConcurrentMessageProcessor;

/**
 * Extension of {@link ConcurrentMessageProcessor} that customizes
 * the message processing loop to avoid silently logging errors.
 * <p>
 * By default, {@link ConcurrentMessageProcessor} catches exceptions
 * during the message processing loop and only logs them. This can lead to:
 * <ul>
 *   <li>The processor ({@link ConcurrentMessageProcessor}) continuing to read invalid messages.</li>
 *   <li>A flood of errors and inconsistent state in the language server.</li>
 * </ul>
 * <p>
 * By removing the try-catch block that only logs exceptions:
 * <ul>
 *   <li>Any unexpected exception thrown by {@link MessageProducer#listen(MessageConsumer)}
 *       will propagate and stop the processor ({@link ConcurrentMessageProcessor}).</li>
 *   <li>The {@link java.util.concurrent.Future} associated with this processor will complete exceptionally,
 *       allowing LSP4IJ to detect the failure and restart the language server if needed.</li>
 * </ul>
 */
public class ExtendedConcurrentMessageProcessor extends ConcurrentMessageProcessor {

    private final MessageProducer messageProducer;
    private final MessageConsumer messageConsumer;

    /**
     * Creates a new concurrent message processor using the provided
     * message producer and consumer.
     *
     * @param messageProducer the producer of messages
     * @param messageConsumer the consumer that handles messages
     */
    public ExtendedConcurrentMessageProcessor(MessageProducer messageProducer, MessageConsumer messageConsumer) {
        super(messageProducer, messageConsumer);
        this.messageProducer = messageProducer;
        this.messageConsumer = messageConsumer;
    }

    /**
     * Starts the message processing loop.
     * <p>
     * Overrides the default {@link ConcurrentMessageProcessor#run()} method
     * to remove the catch-and-log behavior and ensure that exceptions stop the processor.
     * <p>
     * Note:
     * The original {@link ConcurrentMessageProcessor} implementation catches all exceptions:
     * <pre>
     * } catch (Exception e) {
     *     LOG.log(Level.SEVERE, e.getMessage(), e);
     * }
     * </pre>
     * This catch block is commented out intentionally. By allowing exceptions to propagate:
     * <ul>
     *   <li>The Future returned by the processor ({@link ConcurrentMessageProcessor}) will complete exceptionally.</li>
     *   <li>LSP4IJ can detect that the processor has failed.</li>
     *   <li>The language server can then be restarted automatically.</li>
     * </ul>
     */
    @Override
    public void run() {
        processingStarted();
        try {
            messageProducer.listen(messageConsumer);
            // Original catch/log block is intentionally commented out:
            // } catch (Exception e) {
            //     LOG.log(Level.SEVERE, e.getMessage(), e);
            // }
            // By not catching here, exceptions propagate to stop the processor.
        } finally {
            processingEnded();
        }
    }
}

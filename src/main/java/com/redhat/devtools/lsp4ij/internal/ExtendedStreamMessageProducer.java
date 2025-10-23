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

import com.redhat.devtools.lsp4ij.server.LanguageServerException;
import org.eclipse.lsp4j.jsonrpc.MessageIssueHandler;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer;

import java.io.InputStream;

/**
 * Extension of {@link StreamMessageProducer} that ensures errors are not only logged,
 * but actually thrown as exceptions.
 * <p>
 * By default, {@link StreamMessageProducer} logs errors without throwing them.
 * This can lead to situations where the processor ({@link StreamMessageProducer})
 * keeps reading the input stream even if it is producing invalid messages. As a result:
 * <ul>
 *   <li>You may get a flood of errors such as
 *       {@code java.lang.IllegalStateException: Missing header Content-Length in input}.</li>
 *   <li>The processor does not stop, and the language server is never marked as crashed.</li>
 * </ul>
 * (see <a href="https://github.com/redhat-developer/lsp4ij/issues/1238">issue #1238</a>)
 * <p>
 * By throwing exceptions instead:
 * <ul>
 *   <li>The processor ({@link ExtendedConcurrentMessageProcessor}) is immediately stopped when an error occurs.</li>
 *   <li>LSP4IJ can detect the failure and automatically restart the language server ({@link LanguageServerException}).</li>
 * </ul>
 */
public class ExtendedStreamMessageProducer extends StreamMessageProducer {

    public ExtendedStreamMessageProducer(InputStream input,
                                         MessageJsonHandler jsonHandler,
                                         MessageIssueHandler issueHandler) {
        super(input, jsonHandler, issueHandler);
    }

    /**
     * Overrides the default error handling to immediately throw the error.
     * <p>
     * Prevents the processor ({@link StreamMessageProducer}) from continuing
     * to read invalid messages and flooding the log with errors.
     *
     * @param error the error that occurred during message processing
     */
    @Override
    protected void fireError(Throwable error) {
        throwError(error);
    }

    /**
     * Overrides the default stream closed handling.
     * <p>
     * Ensures that unexpected stream closures stop the processor
     * ({@link StreamMessageProducer}) and allow LSP4IJ to restart the server.
     *
     * @param cause the exception that caused the stream to close
     */
    @Override
    protected void fireStreamClosed(Exception cause) {
        if (cause.getMessage() != null) {
            // If the cause has a message, rethrow it to stop processing
            throwError(cause);
        }
        // Otherwise, explicitly signal the stream closure
        throw new LanguageServerException("The input stream was closed.", cause);
    }

    /**
     * Utility method that rethrows the given error.
     * <p>
     * - If it is a {@link RuntimeException}, it is thrown directly.
     * - Otherwise, it is wrapped in a {@link LanguageServerException}.
     *
     * @param error the error to rethrow
     */
    private static void throwError(Throwable error) {
        if (error instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new LanguageServerException(error);
    }
}

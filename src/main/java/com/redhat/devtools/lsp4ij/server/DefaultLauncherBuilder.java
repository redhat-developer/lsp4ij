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
package com.redhat.devtools.lsp4ij.server;

import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.internal.ExtendedConcurrentMessageProcessor;
import com.redhat.devtools.lsp4ij.internal.ExtendedStreamMessageProducer;
import com.redhat.devtools.lsp4ij.internal.capabilities.CodeLensOptionsAdapter;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.jsonrpc.*;
import org.eclipse.lsp4j.jsonrpc.json.ConcurrentMessageProcessor;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Default implementation of {@link Launcher.Builder} for lsp4ij that:
 * <ul>
 *   <li>Uses {@link ExtendedStreamMessageProducer} for error-propagating message reading</li>
 *   <li>Uses {@link ExtendedConcurrentMessageProcessor} for error-propagating message processing</li>
 *   <li>Supports integer JSON-RPC IDs via {@link LSPClientFeatures#isUseIntAsJsonRpcId()}</li>
 *   <li>Registers {@link CodeLensOptionsAdapter} for backward compatibility with legacy servers</li>
 * </ul>
 *
 * <p>Subclasses can override {@link #createMessageProducer} to provide a custom
 * {@link MessageProducer} (e.g., for non-stdio message sources).</p>
 *
 * @param <S> the language server interface type
 */
public class DefaultLauncherBuilder<S extends LanguageServer> extends Launcher.Builder<S> {

    private final @NotNull LSPClientFeatures clientFeatures;

    public DefaultLauncherBuilder(@NotNull LSPClientFeatures clientFeatures) {
        this.clientFeatures = clientFeatures;
        configureGson(builder -> {
            // Add a custom CodeLensOptionsAdapter to support old language server
            // which declares codeLensProvider with a boolean instead of Json object.
            builder.registerTypeAdapter(CodeLensOptions.class, new CodeLensOptionsAdapter());
        });
    }

    /**
     * Returns the {@link LSPClientFeatures} associated with this builder.
     *
     * @return the client features.
     */
    @NotNull
    protected LSPClientFeatures getClientFeatures() {
        return clientFeatures;
    }

    @Override
    public Launcher<S> create() {
        // Validate input
        if (input == null)
            throw new IllegalStateException("Input stream must be configured.");
        if (output == null)
            throw new IllegalStateException("Output stream must be configured.");
        if (localServices == null)
            throw new IllegalStateException("Local service must be configured.");
        if (remoteInterfaces == null)
            throw new IllegalStateException("Remote interface must be configured.");

        // Create the JSON handler, remote endpoint and remote proxy
        MessageJsonHandler jsonHandler = createJsonHandler();
        RemoteEndpoint remoteEndpoint = createRemoteEndpoint(jsonHandler);
        S remoteProxy = createProxy(remoteEndpoint);

        // Create the message processor
        MessageProducer reader = createMessageProducer(input, jsonHandler, remoteEndpoint);
        MessageConsumer messageConsumer = wrapMessageConsumer(remoteEndpoint);
        ConcurrentMessageProcessor msgProcessor = createMessageProcessor(reader, messageConsumer, remoteProxy);
        ExecutorService execService = executorService != null ? executorService : Executors.newCachedThreadPool();
        return createLauncher(execService, remoteProxy, remoteEndpoint, msgProcessor);
    }

    /**
     * Creates the {@link MessageProducer} that reads incoming LSP messages.
     *
     * <p>The default implementation returns an {@link ExtendedStreamMessageProducer}
     * that reads from the given input stream. Subclasses can override this method
     * to provide a custom message source (e.g., a queue-based producer for
     * non-stdio transports).</p>
     *
     * @param input        the input stream to read messages from.
     * @param jsonHandler  the JSON handler for message parsing.
     * @param issueHandler the handler for message issues.
     * @return the message producer.
     */
    protected @NotNull MessageProducer createMessageProducer(@NotNull InputStream input,
                                                              @NotNull MessageJsonHandler jsonHandler,
                                                              @NotNull MessageIssueHandler issueHandler) {
        return new ExtendedStreamMessageProducer(input, jsonHandler, issueHandler);
    }

    @Override
    protected ConcurrentMessageProcessor createMessageProcessor(MessageProducer reader,
                                                                 MessageConsumer messageConsumer,
                                                                 S remoteProxy) {
        return new ExtendedConcurrentMessageProcessor(reader, messageConsumer);
    }

    @Override
    protected RemoteEndpoint createRemoteEndpoint(MessageJsonHandler jsonHandler) {
        boolean useIntAsId = clientFeatures.isUseIntAsJsonRpcId();
        if (!useIntAsId) {
            // Use JSON-RPC as String (default behavior of LSP4J)
            return super.createRemoteEndpoint(jsonHandler);
        }

        // Override the remote endpoint to use JSON-RPC id as int
        MessageConsumer outgoingMessageStream = new StreamMessageConsumer(output, jsonHandler);
        outgoingMessageStream = wrapMessageConsumer(outgoingMessageStream);
        Endpoint localEndpoint = ServiceEndpoints.toEndpoint(localServices);
        RemoteEndpoint remoteEndpoint;
        if (exceptionHandler == null)
            remoteEndpoint = new RemoteEndpointWithIdAsInt(outgoingMessageStream, localEndpoint);
        else
            remoteEndpoint = new RemoteEndpointWithIdAsInt(outgoingMessageStream, localEndpoint, exceptionHandler);
        jsonHandler.setMethodProvider(remoteEndpoint);
        return remoteEndpoint;
    }

    /**
     * Custom RemoteEndpoint that uses integer IDs instead of string IDs for JSON-RPC messages.
     */
    private static class RemoteEndpointWithIdAsInt extends RemoteEndpoint {

        private final AtomicInteger nextRequestId = new AtomicInteger();

        public RemoteEndpointWithIdAsInt(MessageConsumer out,
                                         Endpoint localEndpoint,
                                         Function<Throwable, ResponseError> exceptionHandler) {
            super(out, localEndpoint, exceptionHandler);
        }

        public RemoteEndpointWithIdAsInt(MessageConsumer out,
                                         Endpoint localEndpoint) {
            super(out, localEndpoint);
        }

        @Override
        protected RequestMessage createRequestMessage(String method, Object parameter) {
            RequestMessage requestMessage = new RequestMessage();
            // Use int as JSON-RPC id instead of String (by default from LSP4J)
            requestMessage.setId(nextRequestId.incrementAndGet());
            requestMessage.setMethod(method);
            requestMessage.setParams(parameter);
            return requestMessage;
        }
    }
}

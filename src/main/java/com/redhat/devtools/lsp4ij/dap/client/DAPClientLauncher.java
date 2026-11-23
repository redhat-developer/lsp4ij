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

import org.eclipse.lsp4j.debug.CancelArguments;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.eclipse.lsp4j.jsonrpc.JsonRpcException;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.debug.DebugLauncher;
import org.eclipse.lsp4j.jsonrpc.debug.DebugRemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugResponseMessage;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Creates the launcher of a {@link DAPClient}: LSP4J's debug launcher, except that cancelling the future of a request
 * sends the DAP {@code cancel} request.
 */
final class DAPClientLauncher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DAPClientLauncher.class);

    private DAPClientLauncher() {
    }

    /**
     * Returns the launcher of a DAP client, as {@code DSPLauncher.createClientLauncher} creates it, except that
     * cancelling the future of a request sends the DAP {@code cancel} request when the debug adapter supports it, and
     * nothing otherwise.
     *
     * @param client          the DAP client.
     * @param in              the stream of the messages of the debug adapter.
     * @param out             the stream of the messages to the debug adapter.
     * @param executorService the executor service of the threads of the launcher.
     * @param wrapper         the wrapper of the message consumers.
     * @param capabilities    the current capabilities of the debug adapter.
     * @return the launcher.
     */
    static @NotNull Launcher<IDebugProtocolServer> create(@NotNull IDebugProtocolClient client,
                                                          @NotNull InputStream in,
                                                          @NotNull OutputStream out,
                                                          @NotNull ExecutorService executorService,
                                                          @NotNull Function<MessageConsumer, MessageConsumer> wrapper,
                                                          @NotNull Supplier<@NotNull Capabilities> capabilities) {
        return new DebugLauncher.Builder<IDebugProtocolServer>() {
            @Override
            protected RemoteEndpoint createRemoteEndpoint(MessageJsonHandler jsonHandler) {
                // As DebugLauncher.Builder does, with the remote endpoint below
                MessageConsumer outgoing = wrapMessageConsumer(new StreamMessageConsumer(output, jsonHandler));
                Endpoint localEndpoint = ServiceEndpoints.toEndpoint(localServices);
                RemoteEndpoint remoteEndpoint = new CancelRequestRemoteEndpoint(outgoing, localEndpoint, capabilities);
                jsonHandler.setMethodProvider(remoteEndpoint);
                remoteEndpoint.setJsonHandler(jsonHandler);
                return remoteEndpoint;
            }
        }
                .setLocalService(client)
                .setRemoteInterface(IDebugProtocolServer.class)
                .setInput(in)
                .setOutput(out)
                .setExecutorService(executorService)
                .wrapMessages(wrapper)
                .create();
    }

    /**
     * A remote endpoint which cancels a request with the DAP
     * <a href="https://microsoft.github.io/debug-adapter-protocol/specification#Base_Protocol_Cancel">cancel
     * request</a>, whose {@code requestId} is the {@code seq} of the request.
     */
    private static final class CancelRequestRemoteEndpoint extends DebugRemoteEndpoint {

        private static final String CANCEL = "cancel";

        private final MessageConsumer out;
        private final Supplier<@NotNull Capabilities> capabilities;

        CancelRequestRemoteEndpoint(@NotNull MessageConsumer out,
                                    @NotNull Endpoint localEndpoint,
                                    @NotNull Supplier<@NotNull Capabilities> capabilities) {
            super(out, localEndpoint);
            this.out = out;
            this.capabilities = capabilities;
        }

        /**
         * Sends the {@code cancel} request for the request of the given {@code seq}, if the debug adapter supports it,
         * and nothing otherwise. The request is cancelled locally either way.
         * <p>
         * The {@code cancel} request is not kept as pending: no one waits for its answer, which some debug adapters,
         * such as CodeLLDB, never send.
         */
        // TODO LSP4J: remove once eclipse-lsp4j/lsp4j#1009 is released (DebugRemoteEndpoint sends $/cancelRequest as
        //  a DAP event), but keep sending nothing when the debug adapter doesn't support the cancel request
        @Override
        protected void sendCancelNotification(Either<String, Number> id) {
            if (!Boolean.TRUE.equals(capabilities.get().getSupportsCancelRequest())) {
                return;
            }
            var arguments = new CancelArguments();
            arguments.setRequestId(id.map(Integer::valueOf, Number::intValue));
            try {
                out.consume(createRequestMessage(CANCEL, arguments));
            } catch (RuntimeException exception) {
                // As RemoteEndpoint.notify does, when the connection is closed for instance
                if (JsonRpcException.indicatesStreamClosed(exception)) {
                    LOGGER.info("Failed to send the DAP 'cancel' request", exception);
                } else {
                    LOGGER.warn("Failed to send the DAP 'cancel' request", exception);
                }
            }
        }

        /**
         * Drops the answer to a {@code cancel} request which {@link #sendCancelNotification(Either)} sent, for which
         * {@link RemoteEndpoint} would log an unmatched response.
         */
        // TODO LSP4J: remove with sendCancelNotification
        @Override
        protected void handleResponse(ResponseMessage responseMessage) {
            if (responseMessage instanceof DebugResponseMessage response
                    && CANCEL.equals(response.getMethod())
                    && resolveMethod(response.getId()) == null) {
                return;
            }
            super.handleResponse(responseMessage);
        }
    }
}

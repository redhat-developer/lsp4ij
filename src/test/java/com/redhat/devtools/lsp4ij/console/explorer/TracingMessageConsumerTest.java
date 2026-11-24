/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.console.explorer;

import com.google.gson.JsonObject;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.MessageIssue;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The LSP console traces of a {@code workspace/executeCommand} request, and of its response, name the command that
 * it executes, without the verbose level (#284). The trace of a message that LSP4J can't parse shows what kept LSP4J
 * from parsing it (#584).
 */
class TracingMessageConsumerTest {

    private static final StreamMessageConsumer SENDING =
            new StreamMessageConsumer(new ByteArrayOutputStream(), new MessageJsonHandler(Map.of()));
    private static final RemoteEndpoint RECEIVING = new RemoteEndpoint(message -> {
    }, new Endpoint() {
        @Override
        public CompletableFuture<?> request(String method, Object parameter) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void notify(String method, Object parameter) {
        }
    });

    private final TracingMessageConsumer tracing = new TracingMessageConsumer();

    @Test
    void aSentExecuteCommandRequestAndItsResponseNameTheCommand() {
        String request = trace(request("62", "workspace/executeCommand",
                new ExecuteCommandParams("gopls.reset_go_mod_diagnostics", List.of())), SENDING, ServerTrace.messages);
        String response = trace(response("62"), RECEIVING, ServerTrace.messages);

        assertTrue(request.contains("Sending request 'workspace/executeCommand [gopls.reset_go_mod_diagnostics] - (62)'."), request);
        assertTrue(response.contains("Received response 'workspace/executeCommand [gopls.reset_go_mod_diagnostics] - (62)' in "), response);
    }

    @Test
    void theVerboseTraceOfAnExecuteCommandRequestNamesTheCommandToo() {
        String request = trace(request("7", "workspace/executeCommand",
                new ExecuteCommandParams("java.project.import", List.of())), SENDING, ServerTrace.verbose);

        assertTrue(request.contains("Sending request 'workspace/executeCommand [java.project.import] - (7)'."), request);
    }

    @Test
    void aReceivedExecuteCommandRequestWithJsonParamsAndItsResponseNameTheCommand() {
        var params = new JsonObject();
        params.addProperty("command", "client.refresh");
        String request = trace(request("3", "workspace/executeCommand", params), RECEIVING, ServerTrace.messages);
        String response = trace(response("3"), SENDING, ServerTrace.messages);

        assertTrue(request.contains("Received request 'workspace/executeCommand [client.refresh] - (3)'."), request);
        assertTrue(response.contains("Sending response 'workspace/executeCommand [client.refresh] - (3)'."), response);
    }

    @Test
    void anExecuteCommandRequestWithoutACommandAndOtherRequestsShowTheirMethodOnly() {
        String withoutParams = trace(request("1", "workspace/executeCommand", null), SENDING, ServerTrace.messages);
        String hover = trace(request("2", "textDocument/hover", new JsonObject()), SENDING, ServerTrace.messages);

        assertTrue(withoutParams.contains("Sending request 'workspace/executeCommand - (1)'."), withoutParams);
        assertTrue(hover.contains("Sending request 'textDocument/hover - (2)'."), hover);
    }

    @Test
    void aResponseWithoutItsRequestShowsAnUnknownMethod() {
        String response = trace(response("99"), RECEIVING, ServerTrace.messages);

        assertTrue(response.contains("Received response '<unknown> - (99)' in ?ms."), response);
        assertEquals(1, response.lines().count(), response);
    }

    @Test
    void anUnparsableRequestAndTheErrorResponseToItNameItsMethod() {
        var issue = new MessageIssue("Message could not be parsed.", -32700,
                new IllegalStateException("Expected an int but was 4294967295"));
        String request = tracing.logIssues(request("5", "workspace/applyEdit", null), List.of(issue),
                ServerTrace.verbose, Instant.now());
        String response = trace(response("5"), SENDING, ServerTrace.messages);

        assertTrue(request.contains("Received request 'workspace/applyEdit - (5)' that could not be parsed.\n"
                + "Issue: Message could not be parsed. Expected an int but was 4294967295\n"), request);
        // LSP4J didn't keep the params, which the trace doesn't show as null
        assertFalse(request.contains("Params"), request);
        assertTrue(response.contains("Sending response 'workspace/applyEdit - (5)'."), response);
    }

    @Test
    void anUnparsableResponseShowsTheMethodOfItsRequest() {
        trace(request("8", "textDocument/hover", new JsonObject()), SENDING, ServerTrace.messages);
        String response = tracing.logIssues(response("8"), List.of(new MessageIssue("JSON document was not fully consumed.")),
                ServerTrace.messages, Instant.now());

        assertTrue(response.contains("Received response 'textDocument/hover - (8)' in "), response);
        assertTrue(response.contains("that could not be parsed.\nIssue: JSON document was not fully consumed.\n"), response);
        assertEquals(2, response.lines().count(), response);
    }

    private String trace(Message message, MessageConsumer consumer, ServerTrace serverTrace) {
        return tracing.log(message, consumer, serverTrace);
    }

    private static RequestMessage request(String id, String method, Object params) {
        var request = new RequestMessage();
        request.setId(id);
        request.setMethod(method);
        request.setParams(params);
        return request;
    }

    private static ResponseMessage response(String id) {
        var response = new ResponseMessage();
        response.setId(id);
        response.setResult(null);
        return response;
    }
}

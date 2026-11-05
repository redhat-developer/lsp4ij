/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client;

import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.ValueFormat;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the evaluation of a line typed in the debug console: an evaluate request with the context repl, as VS Code's
 * Debug Console sends it.
 */
public class DAPClientConsoleLineTest {

    @Test
    void aTypedLineIsEvaluatedWithTheContextReplInTheSelectedFrame() {
        var requests = new ArrayList<EvaluateArguments>();
        var format = new ValueFormat();
        format.setHex(true);

        var result = DAPClient.evaluateConsoleLine(server(requests, CompletableFuture.completedFuture(response("3"))),
                "1 + 2", 7, format).join();

        assertEquals("3", result);
        assertEquals(1, requests.size());
        var request = requests.get(0);
        assertEquals("1 + 2", request.getExpression());
        assertEquals(EvaluateArgumentsContext.REPL, request.getContext());
        assertEquals(7, request.getFrameId());
        assertSame(format, request.getFormat());
    }

    @Test
    void aTypedLineWhileTheProgramRunsHasNoFrame() {
        // A debug adapter which takes the running program's input over the debug connection can take the line
        var requests = new ArrayList<EvaluateArguments>();

        var result = DAPClient.evaluateConsoleLine(server(requests, CompletableFuture.completedFuture(response(""))),
                "", null, null).join();

        assertNull(result, "an empty result shows nothing");
        assertNull(requests.get(0).getFrameId());
        assertEquals("", requests.get(0).getExpression());
    }

    @Test
    void theAdaptersErrorFailsTheEvaluation() {
        var error = new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError, "not an expression", null));

        var evaluation = DAPClient.evaluateConsoleLine(server(new ArrayList<>(), CompletableFuture.failedFuture(error)),
                "?", 1, null);

        var thrown = assertThrows(CompletionException.class, evaluation::join);
        assertEquals("not an expression", thrown.getCause().getMessage());
    }

    private static IDebugProtocolServer server(List<EvaluateArguments> requests,
                                               CompletableFuture<EvaluateResponse> response) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),
                new Class[]{IDebugProtocolServer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "evaluate" -> {
                        requests.add((EvaluateArguments) args[0]);
                        yield response;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new AssertionError(method.getName());
                });
    }

    private static EvaluateResponse response(String result) {
        var response = new EvaluateResponse();
        response.setResult(result);
        return response;
    }
}

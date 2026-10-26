/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client;

import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ExceptionDetails;
import org.eclipse.lsp4j.debug.ExceptionInfoArguments;
import org.eclipse.lsp4j.debug.ExceptionInfoResponse;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The error hint of an exception stop shows what the 'exceptionInfo' request answers, when the debug adapter
 * supports it.
 */
public class DAPClientExceptionHintTest {

    private final List<Integer> asked = new ArrayList<>();

    @Test
    void showsTheExceptionInfoWhenTheAdapterSupportsIt() {
        var info = info("IllegalArgument", "bad 5", details(null, "at check() (DebugThrows.x:12)\nat run() (DebugThrows.x:5)"));

        var hint = DAPClient.getExceptionHint(server(CompletableFuture.completedFuture(info)), capabilities(true), stopped()).join();

        assertEquals(List.of(7), asked);
        assertEquals("""
                Exception has occurred: IllegalArgument
                bad 5
                at check() (DebugThrows.x:12)
                at run() (DebugThrows.x:5)""", hint);
    }

    @Test
    void usesTheTypeNameWithABlankExceptionId() {
        var info = info("", "bad 5", details("IllegalArgument", null));

        var hint = DAPClient.getExceptionHint(server(CompletableFuture.completedFuture(info)), capabilities(true), stopped()).join();

        assertEquals("Exception has occurred: IllegalArgument\nbad 5", hint);
    }

    @Test
    void showsOnlyTheFirstLinesOfALongStackTrace() {
        var stack = IntStream.rangeClosed(1, 12).mapToObj(i -> "at f" + i + "()").collect(Collectors.joining("\n"));
        var info = info("Overflow", null, details(null, stack));

        var hint = DAPClient.getExceptionHint(server(CompletableFuture.completedFuture(info)), capabilities(true), stopped()).join();

        var lines = hint.lines().toList();
        assertEquals(12, lines.size(), hint);
        assertEquals("at f10()", lines.get(10));
        assertEquals("...", lines.get(11));
    }

    @Test
    void showsTheStoppedEventWhenTheAdapterDoesNotSupportIt() {
        var hint = DAPClient.getExceptionHint(server(null), capabilities(false), stopped()).join();

        assertTrue(asked.isEmpty(), asked.toString());
        assertEquals("Exception has occurred: IllegalArgument: bad 5\nUncaught", hint);
    }

    @Test
    void showsTheStoppedEventWhenTheRequestFails() {
        var server = server(CompletableFuture.failedFuture(new IllegalStateException("no exception")));

        var hint = DAPClient.getExceptionHint(server, capabilities(true), stopped()).join();

        assertEquals("Exception has occurred: IllegalArgument: bad 5\nUncaught", hint);
    }

    @Test
    void showsTheStoppedEventWhenTheAdapterDoesNotAnswer() {
        var hint = DAPClient.getExceptionHint(server(new CompletableFuture<>()), capabilities(true), stopped()).join();

        assertEquals(List.of(7), asked);
        assertEquals("Exception has occurred: IllegalArgument: bad 5\nUncaught", hint);
    }

    @Test
    void showsEachLineOfTheHintAndItsTextAsWritten() {
        assertEquals("Exception has occurred: Array&lt;Int&gt;<br>at f()", DAPClient.toHintHtml("Exception has occurred: Array<Int>\nat f()"));
    }

    private static StoppedEventArguments stopped() {
        var args = new StoppedEventArguments();
        args.setReason("exception");
        args.setThreadId(7);
        args.setText("IllegalArgument: bad 5");
        args.setDescription("Uncaught");
        return args;
    }

    private static Capabilities capabilities(boolean exceptionInfo) {
        var capabilities = new Capabilities();
        capabilities.setSupportsExceptionInfoRequest(exceptionInfo);
        return capabilities;
    }

    private static ExceptionInfoResponse info(String id, String description, ExceptionDetails details) {
        var info = new ExceptionInfoResponse();
        info.setExceptionId(id);
        info.setDescription(description);
        info.setDetails(details);
        return info;
    }

    private static ExceptionDetails details(String typeName, String stackTrace) {
        var details = new ExceptionDetails();
        details.setTypeName(typeName);
        details.setStackTrace(stackTrace);
        return details;
    }

    private IDebugProtocolServer server(CompletableFuture<ExceptionInfoResponse> answer) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),
                new Class[]{IDebugProtocolServer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "exceptionInfo" -> {
                        asked.add(((ExceptionInfoArguments) args[0]).getThreadId());
                        yield answer;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new AssertionError(method.getName());
                });
    }
}

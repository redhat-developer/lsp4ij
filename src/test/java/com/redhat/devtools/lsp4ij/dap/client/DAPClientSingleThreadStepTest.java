/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client;

import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.StepInArguments;
import org.eclipse.lsp4j.debug.StepOutArguments;
import org.eclipse.lsp4j.debug.SteppingGranularity;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the steps that resume only their thread: Step Over, Step Into and Step Out send DAP's 'singleThread' when the
 * user chose to step only the current thread and the DAP server supports single thread execution requests.
 */
public class DAPClientSingleThreadStepTest {

    @Test
    void aStepIsSingleThreadWhenChosenAndSupported() {
        assertEquals(Boolean.TRUE, DAPClient.singleThreadStep(true, capabilities(true)));
    }

    @Test
    void aStepResumesEveryThreadWhenNotChosen() {
        assertNull(DAPClient.singleThreadStep(false, capabilities(true)));
    }

    @Test
    void aStepResumesEveryThreadWhenTheServerDoesNotSupportSingleThreadExecutionRequests() {
        assertNull(DAPClient.singleThreadStep(true, capabilities(false)));
        assertNull(DAPClient.singleThreadStep(true, new Capabilities()), "a server that doesn't say doesn't support them");
    }

    @Test
    void aSingleThreadStepSendsSingleThread() {
        var requests = new ArrayList<Object>();
        var server = server(requests);

        DAPClient.next(server, 3, null, Boolean.TRUE);
        DAPClient.stepIn(server, 3, null, null, Boolean.TRUE);
        DAPClient.stepOut(server, 3, null, Boolean.TRUE);

        var next = (NextArguments) requests.get(0);
        assertEquals(3, next.getThreadId());
        assertEquals(Boolean.TRUE, next.getSingleThread());
        var stepIn = (StepInArguments) requests.get(1);
        assertEquals(3, stepIn.getThreadId());
        assertEquals(Boolean.TRUE, stepIn.getSingleThread());
        var stepOut = (StepOutArguments) requests.get(2);
        assertEquals(3, stepOut.getThreadId());
        assertEquals(Boolean.TRUE, stepOut.getSingleThread());
    }

    @Test
    void aStepThatResumesEveryThreadLeavesSingleThreadOut() {
        var requests = new ArrayList<Object>();
        var server = server(requests);

        DAPClient.next(server, 3, null, null);
        DAPClient.stepIn(server, 3, null, null, null);
        DAPClient.stepOut(server, 3, null, null);

        assertNull(((NextArguments) requests.get(0)).getSingleThread());
        assertNull(((StepInArguments) requests.get(1)).getSingleThread());
        assertNull(((StepOutArguments) requests.get(2)).getSingleThread());
    }

    @Test
    void aSmartStepIntoKeepsItsTargetAndGranularityWithSingleThread() {
        var requests = new ArrayList<Object>();

        DAPClient.stepIn(server(requests), 5, 42, SteppingGranularity.INSTRUCTION, Boolean.TRUE);

        var stepIn = (StepInArguments) requests.get(0);
        assertEquals(5, stepIn.getThreadId());
        assertEquals(42, stepIn.getTargetId());
        assertEquals(SteppingGranularity.INSTRUCTION, stepIn.getGranularity());
        assertEquals(Boolean.TRUE, stepIn.getSingleThread());
    }

    private static Capabilities capabilities(boolean supportsSingleThreadExecutionRequests) {
        var capabilities = new Capabilities();
        capabilities.setSupportsSingleThreadExecutionRequests(supportsSingleThreadExecutionRequests);
        return capabilities;
    }

    private static IDebugProtocolServer server(List<Object> requests) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),
                new Class[]{IDebugProtocolServer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "next", "stepIn", "stepOut" -> {
                        requests.add(args[0]);
                        yield CompletableFuture.completedFuture(null);
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new AssertionError(method.getName());
                });
    }
}

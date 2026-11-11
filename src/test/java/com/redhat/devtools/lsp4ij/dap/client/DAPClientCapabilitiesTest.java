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
import org.eclipse.lsp4j.debug.CapabilitiesEventArguments;
import org.eclipse.lsp4j.debug.ExceptionBreakpointsFilter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A DAP 'capabilities' event updates the capabilities of the debug adapter: it sends only the changed ones, all
 * others keep their value.
 */
public class DAPClientCapabilitiesTest {

    @Test
    void enablesACapabilityWhichTheInitializeResponseLeftOut() {
        var future = initialized(new Capabilities());

        DAPClient.updateCapabilities(future, event(setVariable(true)));

        assertTrue(DAPClient.isSupportsSetVariable(future.join()));
    }

    @Test
    void enablesACapabilityWhichTheInitializeResponseDisabled() {
        var future = initialized(setVariable(false));

        DAPClient.updateCapabilities(future, event(setVariable(true)));

        assertTrue(DAPClient.isSupportsSetVariable(future.join()));
    }

    @Test
    void disablesACapabilityWithAnExplicitFalse() {
        var future = initialized(setVariable(true));

        DAPClient.updateCapabilities(future, event(setVariable(false)));

        assertFalse(DAPClient.isSupportsSetVariable(future.join()));
        assertEquals(Boolean.FALSE, future.join().getSupportsSetVariable());
    }

    @Test
    void keepsTheCapabilitiesWhichTheEventLeavesOut() {
        var initial = setVariable(true);
        initial.setSupportsConditionalBreakpoints(true);
        initial.setSupportsTerminateRequest(false);
        var filter = new ExceptionBreakpointsFilter();
        filter.setFilter("uncaught");
        initial.setExceptionBreakpointFilters(new ExceptionBreakpointsFilter[]{filter});
        var future = initialized(initial);

        var update = new Capabilities();
        update.setSupportsLogPoints(true);
        DAPClient.updateCapabilities(future, event(update));

        var capabilities = future.join();
        assertEquals(Boolean.TRUE, capabilities.getSupportsLogPoints());
        assertEquals(Boolean.TRUE, capabilities.getSupportsSetVariable());
        assertEquals(Boolean.TRUE, capabilities.getSupportsConditionalBreakpoints());
        assertEquals(Boolean.FALSE, capabilities.getSupportsTerminateRequest());
        assertArrayEquals(new ExceptionBreakpointsFilter[]{filter}, capabilities.getExceptionBreakpointFilters());
        assertNull(capabilities.getSupportsStepBack());
    }

    @Test
    void appliesSuccessiveEventsInTheirOrder() {
        var future = initialized(new Capabilities());

        var first = setVariable(true);
        first.setSupportsLogPoints(true);
        DAPClient.updateCapabilities(future, event(first));
        DAPClient.updateCapabilities(future, event(setVariable(false)));

        assertEquals(Boolean.FALSE, future.join().getSupportsSetVariable());
        assertEquals(Boolean.TRUE, future.join().getSupportsLogPoints());
    }

    @Test
    void replacesAnArrayCapabilityWhichTheEventSends() {
        var initial = new Capabilities();
        var filter = new ExceptionBreakpointsFilter();
        filter.setFilter("uncaught");
        initial.setExceptionBreakpointFilters(new ExceptionBreakpointsFilter[]{filter});
        initial.setCompletionTriggerCharacters(new String[]{"."});
        var future = initialized(initial);

        // an empty array is sent explicitly: the adapter has no filter anymore
        var update = new Capabilities();
        update.setExceptionBreakpointFilters(new ExceptionBreakpointsFilter[0]);
        DAPClient.updateCapabilities(future, event(update));

        assertArrayEquals(new ExceptionBreakpointsFilter[0], future.join().getExceptionBreakpointFilters());
        assertArrayEquals(new String[]{"."}, future.join().getCompletionTriggerCharacters());
    }

    @Test
    void ignoresAnEventWithoutCapabilities() {
        var initial = setVariable(true);
        initial.setSupportsLogPoints(false);
        var expected = copy(initial);
        var future = initialized(initial);

        DAPClient.updateCapabilities(future, null);
        DAPClient.updateCapabilities(future, new CapabilitiesEventArguments());
        DAPClient.updateCapabilities(future, event(new Capabilities()));

        assertEquals(expected, future.join());
    }

    @Test
    void updatesTheCapabilitiesObjectWhichTheReadersKeep() {
        var initial = new Capabilities();
        var future = initialized(initial);

        DAPClient.updateCapabilities(future, event(setVariable(true)));

        // the breakpoint handlers keep the capabilities of the 'initialize' response
        assertSame(initial, future.join());
        assertEquals(Boolean.TRUE, initial.getSupportsSetVariable());
    }

    @Test
    void mergesAnEventWhichArrivesBeforeTheInitializeResponseIsStored() {
        var future = new CompletableFuture<Capabilities>();

        DAPClient.updateCapabilities(future, event(setVariable(true)));
        // before the response is stored, a reader gets default capabilities
        assertFalse(DAPClient.isSupportsSetVariable(future.getNow(new Capabilities())));

        var response = setVariable(false);
        response.setSupportsLogPoints(true);
        future.complete(response);

        // the event is newer than the 'initialize' response: the response does not overwrite it
        assertEquals(Boolean.TRUE, future.join().getSupportsSetVariable());
        assertEquals(Boolean.TRUE, future.join().getSupportsLogPoints());
    }

    @Test
    void theConfigurationAfterTheInitializeResponseSeesAnEarlierEvent() {
        var future = new CompletableFuture<Capabilities>();
        DAPClient.updateCapabilities(future, event(setVariable(true)));

        // as in DAPClient.initialize(): the response is stored, then the configuration reads the capabilities
        var seen = CompletableFuture
                .supplyAsync(() -> future.complete(new Capabilities()))
                .thenApply(unused -> DAPClient.isSupportsSetVariable(future.getNow(new Capabilities())))
                .join();

        assertTrue(seen);
    }

    @Test
    void ignoresAnEventWhenTheInitializeRequestFailed() {
        var future = new CompletableFuture<Capabilities>();
        DAPClient.updateCapabilities(future, event(setVariable(true)));

        // the pending update does not hide the failure, nor fails on its own
        assertDoesNotThrow(() -> future.completeExceptionally(new RuntimeException("initialize failed")));
        assertTrue(future.isCompletedExceptionally());
        // an event after the failure is ignored as well
        assertDoesNotThrow(() -> DAPClient.updateCapabilities(future, event(setVariable(false))));
    }

    @Test
    void mergesEveryCapabilityOfLsp4j() throws ReflectiveOperationException {
        // each getter of Capabilities is a capability with a setter: none is left out of the merge
        long getters = Arrays.stream(Capabilities.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("get") && m.getParameterCount() == 0)
                .count();
        assertEquals(getters, DAPClient.CAPABILITY_ACCESSORS.size());

        var update = new Capabilities();
        for (var accessor : DAPClient.CAPABILITY_ACCESSORS) {
            accessor.setter().invoke(update, sample(accessor.getter()));
        }
        var capabilities = new Capabilities();
        DAPClient.mergeCapabilities(capabilities, update);

        assertEquals(update, capabilities);
    }

    private static Object sample(Method getter) {
        Class<?> type = getter.getReturnType();
        if (type == Boolean.class) {
            return Boolean.TRUE;
        }
        assertTrue(type.isArray(), () -> "Unexpected capability type " + type + " of " + getter.getName());
        return Array.newInstance(type.getComponentType(), 0);
    }

    private static CompletableFuture<Capabilities> initialized(Capabilities capabilities) {
        return CompletableFuture.completedFuture(capabilities);
    }

    private static Capabilities setVariable(boolean supported) {
        var capabilities = new Capabilities();
        capabilities.setSupportsSetVariable(supported);
        return capabilities;
    }

    private static Capabilities copy(Capabilities capabilities) {
        var copy = new Capabilities();
        DAPClient.mergeCapabilities(copy, capabilities);
        return copy;
    }

    private static CapabilitiesEventArguments event(Capabilities capabilities) {
        var args = new CapabilitiesEventArguments();
        args.setCapabilities(capabilities);
        return args;
    }
}

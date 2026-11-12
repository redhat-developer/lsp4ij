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

import static org.junit.jupiter.api.Assertions.*;

/**
 * A DAP 'capabilities' event updates the capabilities of the debug adapter: it sends only the changed ones, all
 * others keep their value.
 */
public class DAPClientCapabilitiesTest {

    private final DAPCapabilities capabilities = new DAPCapabilities();

    @Test
    void enablesACapabilityWhichTheInitializeResponseLeftOut() {
        capabilities.initialize(new Capabilities());

        capabilities.update(event(setVariable(true)));

        assertEquals(Boolean.TRUE, capabilities.get().getSupportsSetVariable());
    }

    @Test
    void enablesACapabilityWhichTheInitializeResponseDisabled() {
        capabilities.initialize(setVariable(false));

        capabilities.update(event(setVariable(true)));

        assertEquals(Boolean.TRUE, capabilities.get().getSupportsSetVariable());
    }

    @Test
    void disablesACapabilityWithAnExplicitFalse() {
        capabilities.initialize(setVariable(true));

        capabilities.update(event(setVariable(false)));

        assertEquals(Boolean.FALSE, capabilities.get().getSupportsSetVariable());
    }

    @Test
    void keepsTheCapabilitiesWhichTheEventLeavesOut() {
        var initial = setVariable(true);
        initial.setSupportsConditionalBreakpoints(true);
        initial.setSupportsTerminateRequest(false);
        var filter = new ExceptionBreakpointsFilter();
        filter.setFilter("uncaught");
        initial.setExceptionBreakpointFilters(new ExceptionBreakpointsFilter[]{filter});
        capabilities.initialize(initial);

        var update = new Capabilities();
        update.setSupportsLogPoints(true);
        capabilities.update(event(update));

        var current = capabilities.get();
        assertEquals(Boolean.TRUE, current.getSupportsLogPoints());
        assertEquals(Boolean.TRUE, current.getSupportsSetVariable());
        assertEquals(Boolean.TRUE, current.getSupportsConditionalBreakpoints());
        assertEquals(Boolean.FALSE, current.getSupportsTerminateRequest());
        assertArrayEquals(new ExceptionBreakpointsFilter[]{filter}, current.getExceptionBreakpointFilters());
        assertNull(current.getSupportsStepBack());
    }

    @Test
    void appliesSuccessiveEventsInTheirOrder() {
        capabilities.initialize(new Capabilities());

        var first = setVariable(true);
        first.setSupportsLogPoints(true);
        capabilities.update(event(first));
        capabilities.update(event(setVariable(false)));

        assertEquals(Boolean.FALSE, capabilities.get().getSupportsSetVariable());
        assertEquals(Boolean.TRUE, capabilities.get().getSupportsLogPoints());
    }

    @Test
    void replacesAnArrayCapabilityWhichTheEventSends() {
        var initial = new Capabilities();
        var filter = new ExceptionBreakpointsFilter();
        filter.setFilter("uncaught");
        initial.setExceptionBreakpointFilters(new ExceptionBreakpointsFilter[]{filter});
        initial.setCompletionTriggerCharacters(new String[]{"."});
        capabilities.initialize(initial);

        // an empty array is sent explicitly: the adapter has no filter anymore
        var update = new Capabilities();
        update.setExceptionBreakpointFilters(new ExceptionBreakpointsFilter[0]);
        capabilities.update(event(update));

        assertArrayEquals(new ExceptionBreakpointsFilter[0], capabilities.get().getExceptionBreakpointFilters());
        assertArrayEquals(new String[]{"."}, capabilities.get().getCompletionTriggerCharacters());
    }

    @Test
    void ignoresAnEventWithoutCapabilities() {
        var initial = setVariable(true);
        initial.setSupportsLogPoints(false);
        capabilities.initialize(initial);
        var expected = capabilities.get();

        capabilities.update(null);
        capabilities.update(new CapabilitiesEventArguments());
        capabilities.update(event(new Capabilities()));

        assertEquals(expected, capabilities.get());
    }

    @Test
    void readersSeeTheUpdateAsANewSnapshot() {
        var response = new Capabilities();
        capabilities.initialize(response);
        var before = capabilities.get();

        capabilities.update(event(setVariable(true)));

        // a reader never sees a snapshot change while it reads it: an update publishes a new one
        assertNull(before.getSupportsSetVariable());
        assertNull(response.getSupportsSetVariable());
        assertEquals(Boolean.TRUE, capabilities.get().getSupportsSetVariable());
    }

    @Test
    void readsDefaultCapabilitiesBeforeTheInitializeResponse() {
        capabilities.update(event(setVariable(true)));

        assertNull(capabilities.get().getSupportsSetVariable());
    }

    @Test
    void mergesAnEventWhichArrivesBeforeTheInitializeResponse() {
        capabilities.update(event(setVariable(true)));

        var response = setVariable(false);
        response.setSupportsLogPoints(true);
        var initialized = capabilities.initialize(response);

        // the event is newer than the 'initialize' response: the response does not overwrite it
        assertEquals(Boolean.TRUE, capabilities.get().getSupportsSetVariable());
        assertEquals(Boolean.TRUE, capabilities.get().getSupportsLogPoints());
        // the configuration of the debug adapter goes on with these capabilities
        assertEquals(capabilities.get(), initialized);
    }

    @Test
    void appliesEventsBeforeTheInitializeResponseInTheirOrder() {
        var first = setVariable(true);
        first.setSupportsLogPoints(true);
        capabilities.update(event(first));
        capabilities.update(event(setVariable(false)));

        capabilities.initialize(new Capabilities());

        assertEquals(Boolean.FALSE, capabilities.get().getSupportsSetVariable());
        assertEquals(Boolean.TRUE, capabilities.get().getSupportsLogPoints());
    }

    @Test
    void mergesEveryCapabilityOfLsp4j() throws ReflectiveOperationException {
        // each getter of Capabilities is a capability with a setter: none is left out of the merge
        long getters = Arrays.stream(Capabilities.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("get") && m.getParameterCount() == 0)
                .count();
        assertEquals(getters, DAPCapabilities.CAPABILITY_ACCESSORS.size());

        var update = new Capabilities();
        for (var accessor : DAPCapabilities.CAPABILITY_ACCESSORS) {
            accessor.setter().invoke(update, sample(accessor.getter()));
        }
        capabilities.initialize(new Capabilities());
        capabilities.update(event(update));

        assertEquals(update, capabilities.get());
    }

    private static Object sample(Method getter) {
        Class<?> type = getter.getReturnType();
        if (type == Boolean.class) {
            return Boolean.TRUE;
        }
        assertTrue(type.isArray(), () -> "Unexpected capability type " + type + " of " + getter.getName());
        return Array.newInstance(type.getComponentType(), 0);
    }

    private static Capabilities setVariable(boolean supported) {
        var capabilities = new Capabilities();
        capabilities.setSupportsSetVariable(supported);
        return capabilities;
    }

    private static CapabilitiesEventArguments event(Capabilities capabilities) {
        var args = new CapabilitiesEventArguments();
        args.setCapabilities(capabilities);
        return args;
    }
}

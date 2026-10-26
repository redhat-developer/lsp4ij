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

import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stop is a stop at a breakpoint only when the debug adapter says that a breakpoint stopped the program, not
 * whenever the program stops on the line of a breakpoint; and at the breakpoints it names, when it names them.
 */
public class DAPClientBreakpointStopTest {

    @Test
    void aBreakpointStopsTheProgram() {
        assertTrue(DAPClient.isBreakpointStop(stop(StoppedEventArgumentsReason.BREAKPOINT)));
    }

    @Test
    void theBreakpointsWhichStoppedTheProgramAreNamed() {
        assertTrue(DAPClient.isBreakpointStop(stop(StoppedEventArgumentsReason.STEP, 3)));
    }

    @Test
    void aStepAPauseOrAnExceptionDoesNotStopAtABreakpoint() {
        for (String reason : List.of(StoppedEventArgumentsReason.STEP, StoppedEventArgumentsReason.PAUSE,
                StoppedEventArgumentsReason.EXCEPTION, StoppedEventArgumentsReason.ENTRY)) {
            assertFalse(DAPClient.isBreakpointStop(stop(reason)), reason);
            var noBreakpoints = stop(reason);
            noBreakpoints.setHitBreakpointIds(new Integer[0]);
            assertFalse(DAPClient.isBreakpointStop(noBreakpoints), reason + " with no breakpoints");
        }
    }

    @Test
    void theBreakpointOnTheLineStopsTheProgramWhenNoneIsNamed() {
        var onLine = breakpoint("on line", SuspendPolicy.NONE);

        assertSame(onLine, DAPClient.getStopBreakpoint(stop(StoppedEventArgumentsReason.BREAKPOINT), breakpoints(), () -> onLine));
    }

    @Test
    void aStepDoesNotStopAtTheBreakpointOnTheLine() {
        var onLine = breakpoint("on line", SuspendPolicy.NONE);

        assertNull(DAPClient.getStopBreakpoint(stop(StoppedEventArgumentsReason.STEP), breakpoints(), () -> onLine));
    }

    @Test
    void theNamedBreakpointStopsTheProgram() {
        var named = breakpoint("named", SuspendPolicy.NONE);
        var onLine = breakpoint("on line", SuspendPolicy.ALL);

        assertSame(named, DAPClient.getStopBreakpoint(stop(StoppedEventArgumentsReason.BREAKPOINT, 1), breakpoints(named), () -> onLine));
    }

    @Test
    void aNamedBreakpointWhichSuspendsIsPreferred() {
        var logs = breakpoint("logs", SuspendPolicy.NONE);
        var suspends = breakpoint("suspends", SuspendPolicy.ALL);

        assertSame(suspends, DAPClient.getStopBreakpoint(stop(StoppedEventArgumentsReason.BREAKPOINT, 1, 2, 3), breakpoints(logs, suspends), () -> logs));
    }

    @Test
    void aNamedBreakpointWhichIsNotAnIntelliJBreakpointKeepsTheProgramStopped() {
        // as the temporary breakpoint of "Run to Cursor", on the line of a breakpoint which doesn't suspend
        var logs = breakpoint("logs", SuspendPolicy.NONE);

        assertNull(DAPClient.getStopBreakpoint(stop(StoppedEventArgumentsReason.BREAKPOINT, 1, 2), breakpoints(logs), () -> logs));
    }

    /**
     * Returns the breakpoints by DAP id: the first has the id 1, the second 2, and so on.
     */
    private static Function<Integer, XBreakpoint<?>> breakpoints(XBreakpoint<?>... breakpoints) {
        return id -> id >= 1 && id <= breakpoints.length ? breakpoints[id - 1] : null;
    }

    private static XBreakpoint<?> breakpoint(String name, SuspendPolicy suspendPolicy) {
        return (XBreakpoint<?>) Proxy.newProxyInstance(XBreakpoint.class.getClassLoader(), new Class[]{XBreakpoint.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSuspendPolicy" -> suspendPolicy;
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static StoppedEventArguments stop(String reason, Integer... hitBreakpointIds) {
        var args = new StoppedEventArguments();
        args.setReason(reason);
        args.setThreadId(7);
        if (hitBreakpointIds.length > 0) {
            args.setHitBreakpointIds(hitBreakpointIds);
        }
        return args;
    }
}

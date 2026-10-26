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

import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stop is a stop at a breakpoint only when the debug adapter says that a breakpoint stopped the program, not
 * whenever the program stops on the line of a breakpoint.
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

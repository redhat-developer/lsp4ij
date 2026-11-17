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

import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.redhat.devtools.lsp4ij.dap.breakpoints.BreakpointInternalSupport;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * A stop which a breakpoint of any kind caused brings the debugger to the front, as an IntelliJ breakpoint does; a
 * step, a pause, a stop on entry and an unknown reason don't.
 */
public class DAPClientAttractOnStopTest extends BasePlatformTestCase {

    private final List<String> calls = new ArrayList<>();

    public void testABreakpointOfAnyKindIsABreakpointReason() {
        for (String reason : List.of(StoppedEventArgumentsReason.BREAKPOINT, StoppedEventArgumentsReason.EXCEPTION,
                StoppedEventArgumentsReason.FUNCTION_BREAKPOINT, StoppedEventArgumentsReason.DATA_BREAKPOINT,
                StoppedEventArgumentsReason.INSTRUCTION_BREAKPOINT)) {
            assertTrue(reason, DAPClient.isBreakpointReason(reason));
        }
    }

    public void testAStepAPauseAnEntryOrAnUnknownReasonIsNoBreakpointReason() {
        for (String reason : List.of(StoppedEventArgumentsReason.STEP, StoppedEventArgumentsReason.GOTO,
                StoppedEventArgumentsReason.PAUSE, StoppedEventArgumentsReason.ENTRY, "signal", "")) {
            assertFalse(reason, DAPClient.isBreakpointReason(reason));
        }
        assertFalse(DAPClient.isBreakpointReason(null));
    }

    public void testAnExceptionStopSelectsTheDebuggerTab() {
        DAPClient.positionReached(session(), new XSuspendContext() {
        }, StoppedEventArgumentsReason.EXCEPTION);
        PlatformTestUtil.waitWithEventsDispatching("The debugger tab is not selected", () -> calls.size() == 2, 10);

        assertEquals(List.of("positionReached", "attractBy breakpoint"), calls);
    }

    public void testAStepOrAPauseLeavesTheTabs() {
        for (String reason : List.of(StoppedEventArgumentsReason.STEP, StoppedEventArgumentsReason.PAUSE,
                StoppedEventArgumentsReason.ENTRY)) {
            calls.clear();
            DAPClient.positionReached(session(), new XSuspendContext() {
            }, reason);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

            assertEquals(reason, List.of("positionReached"), calls);
        }
    }

    public void testTheInternalPositionReachedIsFound() throws Exception {
        // BreakpointInternalSupport calls it by reflection to attract the user as at a breakpoint
        Class.forName("com.intellij.xdebugger.impl.XDebugSessionImpl")
                .getMethod("positionReached", XSuspendContext.class, boolean.class);
    }

    public void testASessionWhichIsNotTheInternalOneIsLeftToTheCaller() {
        assertFalse(BreakpointInternalSupport.positionReached(session(), new XSuspendContext() {
        }, true));
        assertEmpty(calls);
    }

    private XDebugSession session() {
        var ui = (RunnerLayoutUi) Proxy.newProxyInstance(RunnerLayoutUi.class.getClassLoader(),
                new Class<?>[]{RunnerLayoutUi.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "attractBy" -> {
                        calls.add("attractBy " + args[0]);
                        yield proxy;
                    }
                    default -> throw new AssertionError(method.getName());
                });
        return (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(),
                new Class<?>[]{XDebugSession.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "positionReached" -> {
                        calls.add("positionReached");
                        yield null;
                    }
                    case "getUI" -> ui;
                    case "isStopped" -> false;
                    case "getProject" -> getProject();
                    default -> throw new AssertionError(method.getName());
                });
    }
}

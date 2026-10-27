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

import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A data breakpoint stop shows what changed, as the stopped event describes it, where the program stopped.
 */
public class DAPClientDataBreakpointHintTest extends BasePlatformTestCase {

    private record Hint(Editor editor, String text, int offset) {
    }

    private final List<Hint> shown = new ArrayList<>();

    public void testShowsWhatChangedWhereTheProgramStopped() {
        myFixture.configureByText("Counter.txt", """
                void bump() {
                    count += 2;
                    log(count);
                }
                """);
        var position = XDebuggerUtil.getInstance().createPosition(myFixture.getFile().getVirtualFile(), 2);

        DAPClient.showDataBreakpointHint(getProject(), stoppedAt(position),
                stopped(StoppedEventArgumentsReason.DATA_BREAKPOINT, "Counter.count changed from 0 to 2"), this::show).join();
        PlatformTestUtil.waitWithEventsDispatching("The hint is not shown", () -> !shown.isEmpty(), 10);

        assertEquals(List.of(new Hint(myFixture.getEditor(), "Counter.count changed from 0 to 2", position.getOffset())), shown);
    }

    public void testShowsNothingForAnotherStopOrWithoutADescription() {
        myFixture.configureByText("Counter.txt", "void bump() {\n}\n");
        var position = XDebuggerUtil.getInstance().createPosition(myFixture.getFile().getVirtualFile(), 0);

        DAPClient.showDataBreakpointHint(getProject(), stoppedAt(position),
                stopped(StoppedEventArgumentsReason.BREAKPOINT, "Paused on breakpoint"), this::show).join();
        DAPClient.showDataBreakpointHint(getProject(), stoppedAt(position),
                stopped(StoppedEventArgumentsReason.DATA_BREAKPOINT, " "), this::show).join();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertEmpty(shown);
    }

    private void show(@NotNull Editor editor, @NotNull String text, int offset) {
        shown.add(new Hint(editor, text, offset));
    }

    private static XSuspendContext stoppedAt(@NotNull XSourcePosition position) {
        var frame = new XStackFrame() {
            @Override
            public XSourcePosition getSourcePosition() {
                return position;
            }
        };
        var stack = new XExecutionStack("thread 1") {
            @Override
            public XStackFrame getTopFrame() {
                return frame;
            }

            @Override
            public void computeStackFrames(int firstFrameIndex, XStackFrameContainer container) {
            }
        };
        return new XSuspendContext() {
            @Override
            public XExecutionStack getActiveExecutionStack() {
                return stack;
            }
        };
    }

    private static StoppedEventArguments stopped(String reason, String description) {
        var args = new StoppedEventArguments();
        args.setReason(reason);
        args.setThreadId(1);
        args.setDescription(description);
        args.setHitBreakpointIds(new Integer[]{32});
        return args;
    }
}

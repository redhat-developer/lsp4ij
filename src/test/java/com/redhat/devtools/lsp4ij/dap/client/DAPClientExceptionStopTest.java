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

import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ExceptionInfoArguments;
import org.eclipse.lsp4j.debug.ExceptionInfoResponse;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * An exception stop asks the debug adapter for the 'exceptionInfo' of the stopped thread, and shows what it answers
 * as an error hint where the program stopped.
 */
public class DAPClientExceptionStopTest extends BasePlatformTestCase {

    private record Hint(Editor editor, String text, int offset) {
    }

    private final List<Integer> asked = new ArrayList<>();
    private final List<Hint> shown = new ArrayList<>();

    public void testShowsTheExceptionInfoWhereTheProgramStopped() {
        myFixture.configureByText("DebugThrows.txt", """
                void run() {
                    check(5);
                }
                """);
        var position = XDebuggerUtil.getInstance().createPosition(myFixture.getFile().getVirtualFile(), 1);

        DAPClient.showExceptionHint(getProject(), stoppedAt(position), server(), capabilities(), stopped(), this::show).join();
        PlatformTestUtil.waitWithEventsDispatching("The error hint is not shown", () -> !shown.isEmpty(), 10);

        assertEquals(List.of(7), asked);
        // The hint is the one of the exceptionInfo answer, not of the stopped event
        assertEquals(List.of(new Hint(myFixture.getEditor(), "Exception has occurred: IllegalArgument\nbad 5", position.getOffset())), shown);
    }

    public void testAsksNothingWhenNoEditorShowsTheFile() {
        var file = myFixture.addFileToProject("DebugThrows.txt", "void run() {\n}\n").getVirtualFile();
        var position = XDebuggerUtil.getInstance().createPosition(file, 0);

        DAPClient.showExceptionHint(getProject(), stoppedAt(position), server(), capabilities(), stopped(), this::show).join();

        assertEmpty(asked);
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
        var stack = new XExecutionStack("thread 7") {
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

    private static StoppedEventArguments stopped() {
        var args = new StoppedEventArguments();
        args.setReason("exception");
        args.setThreadId(7);
        args.setText("IllegalArgument: bad 5");
        args.setDescription("Uncaught");
        return args;
    }

    private static Capabilities capabilities() {
        var capabilities = new Capabilities();
        capabilities.setSupportsExceptionInfoRequest(true);
        return capabilities;
    }

    private IDebugProtocolServer server() {
        var info = new ExceptionInfoResponse();
        info.setExceptionId("IllegalArgument");
        info.setDescription("bad 5");
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),
                new Class[]{IDebugProtocolServer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "exceptionInfo" -> {
                        asked.add(((ExceptionInfoArguments) args[0]).getThreadId());
                        yield CompletableFuture.completedFuture(info);
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new AssertionError(method.getName());
                });
    }
}

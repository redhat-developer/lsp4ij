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
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.FunctionBreakpoint;
import org.eclipse.lsp4j.debug.SetFunctionBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetFunctionBreakpointsResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Function breakpoints are sent with 'setFunctionBreakpoints', all of them at each change, to a DAP server which
 * supports them, and each is shown verified or not as the DAP server answers.
 */
public class DAPFunctionBreakpointHandlerTest extends HeavyPlatformTestCase {

    private final List<SetFunctionBreakpointsArguments> requests = new ArrayList<>();
    // What the IJ breakpoints were shown as, in order
    private final List<String> shown = new ArrayList<>();

    public void testNothingIsSentToADebugAdapterWithoutFunctionBreakpoints() throws Exception {
        assertFalse(DAPFunctionBreakpointHandler.isSupportsFunctionBreakpoints(new Capabilities()));
        var unsupported = new Capabilities();
        unsupported.setSupportsFunctionBreakpoints(false);
        assertFalse(DAPFunctionBreakpointHandler.isSupportsFunctionBreakpoints(unsupported));
        assertTrue(DAPFunctionBreakpointHandler.isSupportsFunctionBreakpoints(supported()));

        var handler = handler();
        try {
            var twice = breakpoint("twice");
            handler.registerBreakpoint(twice);
            handler.initialize(server(arguments -> new Breakpoint[0]), unsupported).join();
            handler.registerBreakpoint(breakpoint("Point.move"));
            handler.unregisterBreakpoint(twice, false);

            assertEmpty(requests);
        } finally {
            handler.dispose();
        }
    }

    public void testNamesConditionsAndHitConditionsAreSent() throws Exception {
        var twice = breakpoint("twice");
        var move = breakpoint("Point.move");
        var disabledCondition = breakpoint("main");
        var disabled = breakpoint("unused");
        WriteAction.run(() -> {
            twice.setConditionExpression(XDebuggerUtil.getInstance().createExpression("n > 1", null, null, EvaluationMode.EXPRESSION));
            move.getProperties().setHitCondition(">= 3");
            disabledCondition.setConditionExpression(XDebuggerUtil.getInstance().createExpression("false", null, null, EvaluationMode.EXPRESSION));
            ((XBreakpointBase<?, ?, ?>) disabledCondition).setConditionEnabled(false);
            disabled.setEnabled(false);
        });

        // the helper maps each IJ breakpoint, in order
        assertEquals(List.of("twice if n > 1", "Point.move hit >= 3", "main"),
                describe(DAPFunctionBreakpointHandler.createSetFunctionBreakpointsArguments(List.of(twice, move, disabledCondition))));

        var handler = handler();
        try {
            for (var breakpoint : List.of(twice, move, disabledCondition, disabled)) {
                handler.registerBreakpoint(breakpoint);
            }
            handler.initialize(server(arguments -> new Breakpoint[0]), supported()).join();

            // one request with every enabled function breakpoint
            assertEquals(1, requests.size());
            assertEquals(List.of("twice if n > 1", "Point.move hit >= 3", "main"), describe(requests.get(0)));
        } finally {
            handler.dispose();
        }
    }

    public void testEachBreakpointIsShownAsTheDebugAdapterAnswers() throws Exception {
        var twice = breakpoint("twice");
        var nope = breakpoint("nope");
        var handler = handler();
        try {
            handler.registerBreakpoint(twice);
            handler.registerBreakpoint(nope);
            handler.initialize(server(arguments -> new Breakpoint[]{
                    answer(11, true, null),
                    answer(12, false, "No method named 'nope'")}), supported()).join();

            assertEquals(List.of("verified twice", "invalid nope: No method named 'nope'"), shown);
            assertSame(twice, handler.findBreakpointById(11));
            assertSame(nope, handler.findBreakpointById(12));
            shown.clear();

            // a 'breakpoint' event with the reason 'changed', as when the program loads the method
            handler.updateBreakpoint(answer(12, true, null), null);
            // another breakpoint's id is not one of these
            handler.updateBreakpoint(answer(99, false, "a line breakpoint"), null);
            assertEquals(List.of("verified nope"), shown);
        } finally {
            handler.dispose();
        }
    }

    public void testAFailedRequestShowsTheBreakpointsInvalid() throws Exception {
        var twice = breakpoint("twice");
        var handler = handler();
        try {
            handler.registerBreakpoint(twice);
            // the failure doesn't keep the session from starting
            handler.initialize(server(arguments -> {
                throw new IllegalStateException("cannot set function breakpoints");
            }), supported()).join();

            assertEquals(List.of("invalid twice: cannot set function breakpoints"), shown);
        } finally {
            handler.dispose();
        }
    }

    public void testEachChangeSendsAllTheBreakpoints() throws Exception {
        var twice = breakpoint("twice");
        var move = breakpoint("Point.move");
        var main = breakpoint("main");
        var handler = handler();
        try {
            handler.registerBreakpoint(twice);
            handler.registerBreakpoint(move);
            handler.initialize(server(arguments -> new Breakpoint[0]), supported()).join();
            requests.clear();

            // one added while the session runs is sent with the others
            handler.registerBreakpoint(main);
            assertEquals(List.of(List.of("twice", "Point.move", "main")), names());
            requests.clear();

            // a removal sends the remaining ones
            handler.unregisterBreakpoint(twice, false);
            handler.unregisterBreakpoint(main, false);
            assertEquals(List.of(List.of("Point.move", "main"), List.of("Point.move")), names());
            requests.clear();

            // the last one is removed with an empty request
            handler.unregisterBreakpoint(move, true);
            assertEquals(List.of(List.of()), names());
            requests.clear();

            // one which is not registered, such as a disabled one, sends nothing
            handler.unregisterBreakpoint(move, false);
            assertEmpty(requests);
        } finally {
            handler.dispose();
        }
    }

    private static Capabilities supported() {
        var capabilities = new Capabilities();
        capabilities.setSupportsFunctionBreakpoints(true);
        return capabilities;
    }

    private static Breakpoint answer(int id, boolean verified, String message) {
        var breakpoint = new Breakpoint();
        breakpoint.setId(id);
        breakpoint.setVerified(verified);
        breakpoint.setMessage(message);
        return breakpoint;
    }

    private static List<String> describe(SetFunctionBreakpointsArguments arguments) {
        return Arrays.stream(arguments.getBreakpoints())
                .map(DAPFunctionBreakpointHandlerTest::describe)
                .toList();
    }

    private static String describe(FunctionBreakpoint breakpoint) {
        var description = new StringBuilder(breakpoint.getName());
        if (breakpoint.getCondition() != null) {
            description.append(" if ").append(breakpoint.getCondition());
        }
        if (breakpoint.getHitCondition() != null) {
            description.append(" hit ").append(breakpoint.getHitCondition());
        }
        return description.toString();
    }

    /**
     * The function names of each request, in order.
     */
    private List<List<String>> names() {
        return requests.stream()
                .map(request -> Arrays.stream(request.getBreakpoints()).map(FunctionBreakpoint::getName).toList())
                .toList();
    }

    private XBreakpoint<DAPFunctionBreakpointProperties> breakpoint(String name) {
        var type = XDebuggerUtil.getInstance().findBreakpointType(DAPFunctionBreakpointType.class);
        return WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                .addBreakpoint(type, new DAPFunctionBreakpointProperties(name)));
    }

    private DAPFunctionBreakpointHandler handler() throws Exception {
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var config = type.getConfigurationFactories()[0].createTemplateConfiguration(getProject());
        var environment = ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), config).build();
        var descriptor = new DebugAdapterDescriptor(new DAPRunConfigurationOptions(), environment, null) {
            @Override
            public ProcessHandler startServer() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getId() {
                return "function-breakpoints";
            }

            @Override
            public Map<String, Object> getDapParameters() {
                return Map.of();
            }

            @Override
            public FileType getFileType() {
                return PlainTextFileType.INSTANCE;
            }

            @Override
            public boolean isDebuggableFile(VirtualFile file, Project project) {
                return true;
            }
        };
        var session = (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class[]{XDebugSession.class}, (p, m, a) -> null);
        return new DAPFunctionBreakpointHandler(session, descriptor, getProject()) {
            @Override
            protected void setBreakpointVerified(XBreakpoint<DAPFunctionBreakpointProperties> ijBreakpoint) {
                shown.add("verified " + ijBreakpoint.getProperties().getName());
            }

            @Override
            protected void setBreakpointInvalid(XBreakpoint<DAPFunctionBreakpointProperties> ijBreakpoint, String message) {
                shown.add("invalid " + ijBreakpoint.getProperties().getName() + ": " + message);
            }
        };
    }

    private IDebugProtocolServer server(Function<SetFunctionBreakpointsArguments, Breakpoint[]> answers) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(), new Class[]{IDebugProtocolServer.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "setFunctionBreakpoints" -> {
                    var arguments = (SetFunctionBreakpointsArguments) a[0];
                    requests.add(arguments);
                    Breakpoint[] breakpoints;
                    try {
                        breakpoints = answers.apply(arguments);
                    } catch (IllegalStateException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                    var response = new SetFunctionBreakpointsResponse();
                    response.setBreakpoints(breakpoints);
                    return CompletableFuture.completedFuture(response);
                }
                case "equals" -> {
                    return p == a[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(p);
                }
                default -> throw new AssertionError(m.getName());
            }
        });
    }
}

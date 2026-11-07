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
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPFunctionBreakpointHandler;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPFunctionBreakpointProperties;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPFunctionBreakpointType;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.SetFunctionBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetFunctionBreakpointsResponse;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * A stop at a function breakpoint reaches the IntelliJ function breakpoint which the debug adapter names by its id, as
 * a stop at a line breakpoint does.
 */
public class DAPClientFunctionBreakpointTest extends HeavyPlatformTestCase {

    public void testAStopIsMatchedToItsFunctionBreakpointById() throws Exception {
        var twice = breakpoint("twice");
        var move = breakpoint("Point.move");
        var handler = handler();
        try {
            handler.registerBreakpoint(twice);
            handler.registerBreakpoint(move);
            var capabilities = new Capabilities();
            capabilities.setSupportsFunctionBreakpoints(true);
            // the debug adapter gives the function breakpoints the ids 21 and 22
            handler.initialize(server(), capabilities).join();

            var stop = stop(22);
            assertTrue(DAPClient.isBreakpointStop(stop));
            assertSame(move, DAPClient.getStopBreakpoint(stop, handler::findBreakpointById, () -> {
                throw new AssertionError("the debug adapter names the breakpoint");
            }));

            // an id which is no IntelliJ breakpoint's, such as that of a breakpoint of the debug adapter's own
            assertNull(DAPClient.getStopBreakpoint(stop(23), handler::findBreakpointById, () -> twice));
        } finally {
            handler.dispose();
        }
    }

    public void testAFunctionBreakpointStopWhichNamesNoBreakpointIsNotABreakpointStop() {
        var stop = stop();
        assertFalse(DAPClient.isBreakpointStop(stop));
        assertNull(DAPClient.getStopBreakpoint(stop, id -> {
            throw new AssertionError("the debug adapter names no breakpoint");
        }, () -> {
            throw new AssertionError("a function breakpoint is not on a line");
        }));
    }

    private static StoppedEventArguments stop(Integer... hitBreakpointIds) {
        var stop = new StoppedEventArguments();
        stop.setReason(StoppedEventArgumentsReason.FUNCTION_BREAKPOINT);
        stop.setThreadId(1);
        if (hitBreakpointIds.length > 0) {
            stop.setHitBreakpointIds(hitBreakpointIds);
        }
        return stop;
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
                return "function-breakpoint-stops";
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
        return new DAPFunctionBreakpointHandler(session, descriptor, getProject());
    }

    /**
     * Returns a debug adapter which verifies each function breakpoint, with the ids 21, 22, ...
     */
    private static IDebugProtocolServer server() {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(), new Class[]{IDebugProtocolServer.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "setFunctionBreakpoints" -> {
                    var arguments = (SetFunctionBreakpointsArguments) a[0];
                    var response = new SetFunctionBreakpointsResponse();
                    response.setBreakpoints(IntStream.range(0, arguments.getBreakpoints().length)
                            .mapToObj(i -> {
                                var breakpoint = new Breakpoint();
                                breakpoint.setId(21 + i);
                                breakpoint.setVerified(true);
                                return breakpoint;
                            })
                            .toArray(Breakpoint[]::new));
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

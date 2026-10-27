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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * "Run to Cursor" keeps its temporary breakpoint until the program next stops, and the session's end forgets it.
 */
public class RunToCursorBreakpointTest extends HeavyPlatformTestCase {

    private final List<SetBreakpointsArguments> requests = new ArrayList<>();

    public void testTheBreakpointStaysUntilTheProgramStops() throws Exception {
        var dir = Files.createTempDirectory("dap-run-to-cursor-");
        var path = dir.resolve("a.lua");
        var handler = handler();
        try {
            var breakpoint = breakpoint(path, 0);
            handler.registerBreakpoint(breakpoint);
            handler.initialize(server(), new Capabilities()).join();
            var runToCursor = new RunToCursorBreakpoint(handler);
            requests.clear();

            // the temporary breakpoint beside the user's: lines are 1-based in DAP
            runToCursor.set(position(path, 2)).join();
            assertEquals(List.of(List.of(1, 3)), lines(path));
            requests.clear();

            // the program stops: only the user's breakpoint stays, and a stop after it sends nothing
            runToCursor.stopped();
            assertEquals(List.of(List.of(1)), lines(path));
            requests.clear();
            runToCursor.stopped();
            assertEmpty(requests);
        } finally {
            handler.dispose();
            FileUtil.delete(dir.toFile());
        }
    }

    public void testAnotherRunToCursorReplacesOneTheProgramDidNotStopAtAndTheEndForgetsIt() throws Exception {
        var dir = Files.createTempDirectory("dap-run-to-cursor-");
        var path = dir.resolve("a.lua");
        var handler = handler();
        try {
            file(path);
            handler.initialize(server(), new Capabilities()).join();
            var runToCursor = new RunToCursorBreakpoint(handler);

            runToCursor.set(position(path, 1)).join();
            requests.clear();
            // the first is removed, then the second set
            runToCursor.set(position(path, 2)).join();
            assertEquals(List.of(List.of(), List.of(3)), lines(path));
            requests.clear();

            // the session ended: nothing is sent, and a stop after it has nothing to remove
            runToCursor.ended();
            runToCursor.stopped();
            assertEmpty(requests);
        } finally {
            handler.dispose();
            FileUtil.delete(dir.toFile());
        }
    }

    private DAPBreakpointHandlerBase<XLineBreakpoint<DAPBreakpointProperties>> handler() throws Exception {
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
                return "run-to-cursor";
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
        return new DAPBreakpointHandlerBase<>(DAPBreakpointType.class, session, descriptor, getProject()) {
        };
    }

    private IDebugProtocolServer server() {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(), new Class[]{IDebugProtocolServer.class}, (p, m, a) -> {
            if (m.getName().equals("setBreakpoints")) {
                requests.add((SetBreakpointsArguments) a[0]);
                var response = new SetBreakpointsResponse();
                response.setBreakpoints(new Breakpoint[0]);
                return CompletableFuture.completedFuture(response);
            }
            if (m.getName().equals("equals")) {
                return p == a[0];
            }
            if (m.getName().equals("hashCode")) {
                return System.identityHashCode(p);
            }
            throw new AssertionError(m.getName());
        });
    }

    private XLineBreakpoint<DAPBreakpointProperties> breakpoint(Path path, int line) throws Exception {
        var file = file(path);
        return WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                .addLineBreakpoint(new DAPBreakpointType(), file.getUrl(), line, new DAPBreakpointProperties()));
    }

    private XSourcePosition position(Path path, int line) throws Exception {
        return XDebuggerUtil.getInstance().createPosition(file(path), line);
    }

    private static VirtualFile file(Path path) throws Exception {
        if (!Files.exists(path)) {
            Files.writeString(path, "local a = 1\nlocal b = 2\nlocal c = 3\n");
        }
        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(file);
        return file;
    }

    /**
     * The breakpoint lines of each request for the file, in order.
     */
    private List<List<Integer>> lines(Path path) throws Exception {
        var lines = new ArrayList<List<Integer>>();
        for (var request : requests) {
            if (Files.isSameFile(path, Path.of(request.getSource().getPath()))) {
                lines.add(Arrays.stream(request.getBreakpoints()).map(SourceBreakpoint::getLine).toList());
            }
        }
        return lines;
    }
}

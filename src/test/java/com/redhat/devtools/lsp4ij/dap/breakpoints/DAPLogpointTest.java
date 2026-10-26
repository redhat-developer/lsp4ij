// SPDX-License-Identifier: EPL-2.0
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
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
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
import java.util.function.Function;
import java.util.stream.IntStream;

/** A breakpoint which doesn't suspend is sent as a logpoint with what IntelliJ would log. */
public class DAPLogpointTest extends HeavyPlatformTestCase {

    public void testBreakpointsWhichDoNotSuspendAreLogpoints() throws Exception {
        var dir = Files.createTempDirectory("dap-logpoints-");
        var suspends = breakpoint(dir.resolve("a.lua"), 0);
        var logsValue = breakpoint(dir.resolve("a.lua"), 1);
        var logsHit = breakpoint(dir.resolve("a.lua"), 2);
        var logsBoth = breakpoint(dir.resolve("a.lua"), 3);
        var logsNothing = breakpoint(dir.resolve("a.lua"), 4);
        WriteAction.run(() -> {
            // a suspending breakpoint logs at its stop, so it is not a logpoint
            suspends.setLogExpression("n");
            logsValue.setSuspendPolicy(SuspendPolicy.NONE);
            logsValue.setLogExpression("n");
            logsHit.setSuspendPolicy(SuspendPolicy.NONE);
            logsHit.setLogMessage(true);
            logsBoth.setSuspendPolicy(SuspendPolicy.NONE);
            logsBoth.setLogMessage(true);
            logsBoth.setLogExpression("n + 1");
            logsNothing.setSuspendPolicy(SuspendPolicy.NONE);
        });

        var requests = new ArrayList<SetBreakpointsArguments>();
        var handler = handler();
        try {
            for (var breakpoint : List.of(suspends, logsValue, logsHit, logsBoth, logsNothing)) {
                handler.registerBreakpoint(breakpoint);
            }
            handler.initialize(server(requests), new Capabilities()).join();

            assertEquals(1, requests.size());
            var sent = Arrays.stream(requests.get(0).getBreakpoints()).map(SourceBreakpoint::getLogMessage).toList();
            assertEquals(Arrays.asList(null, "{n}", "Breakpoint reached at a.lua:3", "Breakpoint reached at a.lua:4\n{n + 1}", null), sent);
        } finally {
            handler.dispose();
            FileUtil.delete(dir.toFile());
        }
    }

    public void testRunToCursorOnALogpointKeepsItsId() throws Exception {
        var dir = Files.createTempDirectory("dap-logpoints-");
        var logs = breakpoint(dir.resolve("a.lua"), 1);
        WriteAction.run(() -> {
            logs.setSuspendPolicy(SuspendPolicy.NONE);
            logs.setLogExpression("n");
        });

        var requests = new ArrayList<SetBreakpointsArguments>();
        var handler = handler();
        try {
            handler.registerBreakpoint(logs);
            handler.initialize(server(requests, arguments -> numbered(requests.size(), arguments)), new Capabilities()).join();
            // "Run to Cursor" on the line of the logpoint: the debug adapter gets both, and gives each its own id
            handler.registerTemporaryBreakpoint(logs.getSourcePosition()).join();

            assertEquals(2, requests.get(1).getBreakpoints().length);
            assertSame(logs, handler.findBreakpointById(201));
            assertNull(handler.findBreakpointById(202));
        } finally {
            handler.dispose();
            FileUtil.delete(dir.toFile());
        }
    }

    /**
     * Returns a verified breakpoint per source breakpoint of the given request, with the ids 101, 102, ... for the
     * first request, 201, 202, ... for the second, and so on.
     */
    private static Breakpoint[] numbered(int request, SetBreakpointsArguments arguments) {
        return IntStream.range(0, arguments.getBreakpoints().length)
                .mapToObj(i -> {
                    var breakpoint = new Breakpoint();
                    breakpoint.setId(request * 100 + i + 1);
                    breakpoint.setVerified(true);
                    breakpoint.setLine(arguments.getBreakpoints()[i].getLine());
                    return breakpoint;
                })
                .toArray(Breakpoint[]::new);
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
                return "logpoints";
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

    private static IDebugProtocolServer server(List<SetBreakpointsArguments> requests) {
        return server(requests, arguments -> new Breakpoint[0]);
    }

    private static IDebugProtocolServer server(List<SetBreakpointsArguments> requests,
                                               Function<SetBreakpointsArguments, Breakpoint[]> breakpoints) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(), new Class[]{IDebugProtocolServer.class}, (p, m, a) -> {
            if (m.getName().equals("setBreakpoints")) {
                var arguments = (SetBreakpointsArguments) a[0];
                requests.add(arguments);
                var response = new SetBreakpointsResponse();
                response.setBreakpoints(breakpoints.apply(arguments));
                return CompletableFuture.completedFuture(response);
            }
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            throw new AssertionError(m.getName());
        });
    }

    private XLineBreakpoint<DAPBreakpointProperties> breakpoint(Path path, int line) throws Exception {
        if (!Files.exists(path)) {
            Files.writeString(path, "local a = 1\nlocal b = 2\nlocal c = 3\nlocal d = 4\nlocal e = 5\n");
        }
        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(file);
        return WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                .addLineBreakpoint(new DAPBreakpointType(), file.getUrl(), line, new DAPBreakpointProperties()));
    }
}

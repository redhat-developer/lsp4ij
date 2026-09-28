// SPDX-License-Identifier: EPL-2.0
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Exercises the actual LSP4IJ request conversion with real IntelliJ breakpoints. */
public class DAPBreakpointHandlerBaseTest extends HeavyPlatformTestCase {
    public void testRemovalReplacesBreakpointsPerSource() throws Exception {
        var type = Arrays.stream(com.intellij.execution.configurations.ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
            .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var config = type.getConfigurationFactories()[0].createTemplateConfiguration(getProject());
        var environment = ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), config).build();
        var descriptor = new com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor(new DAPRunConfigurationOptions(), environment, null) {
            @Override
            public com.intellij.execution.process.ProcessHandler startServer() {
                throw new UnsupportedOperationException();
            }
            @Override
            public String getId() {
                return "breakpoint-regression";
            }
            @Override
            public Map<String, Object> getDapParameters() {
                return Map.of();
            }
            @Override
            public com.intellij.openapi.fileTypes.FileType getFileType() {
                return com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE;
            }
            @Override
            public boolean isDebuggableFile(com.intellij.openapi.vfs.VirtualFile file, com.intellij.openapi.project.Project project) {
                return true;
            }
        };
        var session = (XDebugSession)Proxy.newProxyInstance(XDebugSession.class.getClassLoader(),new Class[]{XDebugSession.class}, (p, m, a) -> null);
        var handler = new DAPBreakpointHandlerBase<XLineBreakpoint<DAPBreakpointProperties>>(DAPBreakpointType.class, session, descriptor, getProject()) {};
        var requests = new ArrayList<SetBreakpointsArguments>();
        var server = (IDebugProtocolServer)Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),new Class[]{IDebugProtocolServer.class}, (p, m, a) -> {
            if (m.getName().equals("setBreakpoints")) {
                requests.add((SetBreakpointsArguments) a[0]);
                var response = new SetBreakpointsResponse();
                response.setBreakpoints(new Breakpoint[0]);
                return CompletableFuture.completedFuture(response);
            }
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            throw new AssertionError(m.getName());
        });
        var dir = Files.createTempDirectory("dap-breakpoints-");
        var a = breakpoint(dir.resolve("a.lua"), 0);
        var b = breakpoint(dir.resolve("b.lua"), 0);
        var a2 = breakpoint(dir.resolve("a.lua"), 1);
        try {
            handler.registerBreakpoint(a);
            handler.registerBreakpoint(b);
            handler.registerBreakpoint(a2);
            handler.initialize(server, new Capabilities()).join();
            requests.clear();
            handler.unregisterBreakpoint(a2, false);
            assertLines(requests, dir.resolve("a.lua"), 1);
            requests.clear();
            // The final breakpoint in A must clear A even while B remains installed.
            handler.unregisterBreakpoint(a, false);
            assertLines(requests, dir.resolve("a.lua"));
            assertLines(requests, dir.resolve("b.lua"), 1);
            requests.clear();
            handler.unregisterBreakpoint(b, false);
            assertLines(requests, dir.resolve("b.lua"));
            requests.clear();
            handler.registerBreakpoint(a);
            assertLines(requests, dir.resolve("a.lua"), 1);
            requests.clear();
            // IntelliJ uses the same unregister path when muting/disabling breakpoints.
            handler.unregisterBreakpoint(a, true);
            assertLines(requests, dir.resolve("a.lua"));
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }
    private XLineBreakpoint<DAPBreakpointProperties> breakpoint(Path path, int line) throws Exception {
        Files.writeString(path, "local a = 1\nlocal b = 2\n");
        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(file);
        return WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
            .addLineBreakpoint(new DAPBreakpointType(), file.getUrl(), line, new DAPBreakpointProperties()));
    }
    private void assertLines(List<SetBreakpointsArguments> requests, Path path, int... lines) {
        var matches = requests.stream().filter(r -> path.toString().equals(r.getSource().getPath())).toList();
        assertEquals("Expected a replacement request for " + path, 1, matches.size());
        assertTrue(Arrays.equals(lines, Arrays.stream(matches.get(0).getBreakpoints()).mapToInt(SourceBreakpoint::getLine).toArray()));
    }
}

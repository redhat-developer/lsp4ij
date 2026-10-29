// SPDX-License-Identifier: EPL-2.0
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * The tooltip of a breakpoint is refreshed each time its presentation in the session changes (see
 * {@link BreakpointHandlerBase#refreshTooltip}).
 * <p>
 * The tests run on IntelliJ 2024.2, where the refresh itself does nothing, so the handler records the breakpoints
 * whose tooltip it refreshes. This test is only about that workaround: remove it together with
 * {@code refreshTooltip()} once IntelliJ refreshes the tooltip itself.
 */
public class DAPBreakpointTooltipRefreshTest extends HeavyPlatformTestCase {

    /** The tooltip of a breakpoint in a file which the debug adapter can't debug is refreshed. */
    public void testTooltipIsRefreshedForAnUnsupportedBreakpoint() throws Exception {
        var refreshed = new ArrayList<XBreakpoint<?>>();
        var handler = handler(false, refreshed);
        var dir = Files.createTempDirectory("dap-breakpoints-");
        try {
            var file = file(dir.resolve("a.lua"));
            XLineBreakpoint<DAPBreakpointProperties> breakpoint = WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                    .addLineBreakpoint(new DAPBreakpointType(), file.getUrl(), 0, new DAPBreakpointProperties()));

            handler.registerBreakpoint(breakpoint);
            assertEquals(List.of(breakpoint), refreshed);
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    /** The tooltip of a native breakpoint is refreshed when a DAP breakpoint on the same line shadows it, and when it doesn't anymore. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testTooltipIsRefreshedWhenANativeBreakpointIsShadowed() throws Exception {
        var nativeType = new DAPShadowedBreakpointTest.NativeLineBreakpointType();
        XBreakpointType.EXTENSION_POINT_NAME.getPoint().registerExtension(nativeType, getTestRootDisposable());

        var refreshed = new ArrayList<XBreakpoint<?>>();
        var handler = (BreakpointHandlerBase) handler(true, refreshed);
        var dir = Files.createTempDirectory("dap-breakpoints-");
        try {
            var file = file(dir.resolve("a.lua"));
            XLineBreakpoint<?> nativeBreakpoint = WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                    .addLineBreakpoint((XLineBreakpointType) nativeType, file.getUrl(), 0, null));
            XLineBreakpoint<?> dapBreakpoint = WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                    .addLineBreakpoint(new DAPBreakpointType(), file.getUrl(), 0, new DAPBreakpointProperties()));

            // The debug adapter answers without breakpoints: nothing changes the presentation
            handler.registerBreakpoint(nativeBreakpoint);
            handler.initialize(server(null), new Capabilities()).join();
            assertTrue(refreshed.isEmpty());

            handler.registerBreakpoint(dapBreakpoint);
            assertEquals(List.of(nativeBreakpoint), refreshed);
            refreshed.clear();

            handler.unregisterBreakpoint(dapBreakpoint, false);
            assertEquals(List.of(nativeBreakpoint), refreshed);
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    /** The tooltip of a breakpoint is refreshed when the debug adapter reports it as pending, and when it verifies it later. */
    public void testTooltipIsRefreshedWhenTheDebugAdapterReportsTheBreakpoint() throws Exception {
        var refreshed = new ArrayList<XBreakpoint<?>>();
        var handler = handler(true, refreshed);
        var dir = Files.createTempDirectory("dap-breakpoints-");
        try {
            var file = file(dir.resolve("a.lua"));
            XLineBreakpoint<DAPBreakpointProperties> breakpoint = WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                    .addLineBreakpoint(new DAPBreakpointType(), file.getUrl(), 0, new DAPBreakpointProperties()));

            // The 'setBreakpoints' response: pending
            handler.registerBreakpoint(breakpoint);
            handler.initialize(server("Pending"), new Capabilities()).join();
            assertEquals(List.of(breakpoint), refreshed);
            refreshed.clear();

            // The 'breakpoint' event: verified
            var changed = new Breakpoint();
            changed.setId(1);
            changed.setVerified(true);
            changed.setLine(1);
            handler.updateBreakpoint(changed, null);
            assertEquals(List.of(breakpoint), refreshed);
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    /**
     * Returns a handler which adds each breakpoint whose tooltip it refreshes to the given list.
     *
     * @param debuggable true if the debug adapter can debug the files, false otherwise.
     * @param refreshed  the breakpoints whose tooltip is refreshed.
     */
    private DAPBreakpointHandlerBase<XLineBreakpoint<DAPBreakpointProperties>> handler(boolean debuggable,
                                                                                     List<XBreakpoint<?>> refreshed) throws Exception {
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
                return "tooltip-refresh";
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
            public boolean isDebuggableFile(VirtualFile file, com.intellij.openapi.project.Project project) {
                return debuggable;
            }
        };
        // The presentation itself is checked by the other breakpoint tests
        var session = (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class[]{XDebugSession.class}, (p, m, a) -> null);
        return new DAPBreakpointHandlerBase<>(DAPBreakpointType.class, session, descriptor, getProject()) {
            @Override
            protected void refreshTooltip(@NotNull XBreakpoint<?> ijBreakpoint) {
                refreshed.add(ijBreakpoint);
            }
        };
    }

    /**
     * Returns a debug adapter which answers each 'setBreakpoints' request with unverified breakpoints (id 1) and the
     * given message, or with no breakpoints if the message is null.
     */
    private static IDebugProtocolServer server(@Nullable String pendingMessage) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(), new Class[]{IDebugProtocolServer.class}, (p, m, a) -> {
            if (m.getName().equals("setBreakpoints")) {
                var arguments = (SetBreakpointsArguments) a[0];
                var response = new SetBreakpointsResponse();
                response.setBreakpoints(pendingMessage == null ? new Breakpoint[0] : Arrays.stream(arguments.getBreakpoints()).map(sourceBreakpoint -> {
                    var breakpoint = new Breakpoint();
                    breakpoint.setId(1);
                    breakpoint.setVerified(false);
                    breakpoint.setMessage(pendingMessage);
                    breakpoint.setLine(sourceBreakpoint.getLine());
                    return breakpoint;
                }).toArray(Breakpoint[]::new));
                return CompletableFuture.completedFuture(response);
            }
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            throw new AssertionError(m.getName());
        });
    }

    private static VirtualFile file(Path path) throws Exception {
        Files.writeString(path, "local a = 1\nlocal b = 2\n");
        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(file);
        return file;
    }
}

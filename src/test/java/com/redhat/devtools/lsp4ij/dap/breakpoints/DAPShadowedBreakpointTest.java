// SPDX-License-Identifier: EPL-2.0
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
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

/** A forwarded native line breakpoint is not sent while a DAP breakpoint is on the same line. */
public class DAPShadowedBreakpointTest extends HeavyPlatformTestCase {

    /** A native line breakpoint type, like the JavaScript one. */
    public static class NativeLineBreakpointType extends XLineBreakpointType<XBreakpointProperties<?>> {
        public NativeLineBreakpointType() {
            super("lsp4ij-test-native-line", "Test native line breakpoint");
        }

        @Override
        public @Nullable XBreakpointProperties<?> createBreakpointProperties(@NotNull VirtualFile file, int line) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testNativeBreakpointIsShadowedByDAPBreakpointOnTheSameLine() throws Exception {
        var nativeType = new NativeLineBreakpointType();
        XBreakpointType.EXTENSION_POINT_NAME.getPoint().registerExtension(nativeType, getTestRootDisposable());

        var presentations = new ArrayList<String>();
        var session = (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class[]{XDebugSession.class}, (p, m, a) -> {
            if (m.getName().equals("setBreakpointInvalid")) {
                presentations.add("invalid:" + a[1]);
            } else if (m.getName().equals("updateBreakpointPresentation")) {
                presentations.add(presentation(a));
            }
            return null;
        });
        var handler = (BreakpointHandlerBase) handler(session);
        var requests = new ArrayList<SetBreakpointsArguments>();
        var server = server(requests);

        var dir = Files.createTempDirectory("dap-breakpoints-");
        var path = dir.resolve("a.lua");
        try {
            var file = file(path);
            XLineBreakpoint<?> nativeBreakpoint = WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                    .addLineBreakpoint((XLineBreakpointType) nativeType, file.getUrl(), 0, null));
            nativeBreakpoint.setCondition("a == 1");
            XLineBreakpoint<?> dapBreakpoint = WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                    .addLineBreakpoint(new DAPBreakpointType(), file.getUrl(), 0, new DAPBreakpointProperties()));

            // Only the native breakpoint: it is forwarded
            handler.registerBreakpoint(nativeBreakpoint);
            handler.initialize(server, new Capabilities()).join();
            assertEquals("a == 1", single(requests).getCondition());
            assertTrue(presentations.isEmpty());
            requests.clear();

            // A DAP breakpoint on the same line: only the DAP breakpoint is sent, the native one is marked
            handler.registerBreakpoint(dapBreakpoint);
            assertNull(single(requests).getCondition());
            assertEquals(List.of("inactive:" + DAPBreakpointHandlerBase.SHADOWED_BREAKPOINT_MESSAGE), presentations);
            requests.clear();
            presentations.clear();

            // DAP breakpoint removed: the native breakpoint is forwarded again and its presentation reset
            handler.unregisterBreakpoint(dapBreakpoint, false);
            assertEquals("a == 1", single(requests).getCondition());
            assertEquals(List.of("reset:null"), presentations);
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    /**
     * A disabled DAP breakpoint doesn't shadow the native breakpoint: the native breakpoint is sent, shown as
     * invalid while the debug adapter reports it as pending, and as verified once the adapter verifies it.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testNativeBreakpointIsVerifiedWhenDAPBreakpointOnTheSameLineIsDisabled() throws Exception {
        var nativeType = new NativeLineBreakpointType();
        XBreakpointType.EXTENSION_POINT_NAME.getPoint().registerExtension(nativeType, getTestRootDisposable());

        var presentations = new ArrayList<String>();
        var session = (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class[]{XDebugSession.class}, (p, m, a) -> {
            String target = a != null && a.length > 0 && a[0] instanceof XLineBreakpoint<?> b
                    ? (b.getType() instanceof DAPBreakpointType ? "dap " : "native ") : "";
            switch (m.getName()) {
                case "setBreakpointInvalid" -> presentations.add(target + "invalid:" + a[1]);
                case "setBreakpointVerified" -> presentations.add(target + "verified");
                case "updateBreakpointPresentation" -> presentations.add(target + presentation(a));
                default -> {
                }
            }
            return null;
        });
        var handler = (BreakpointHandlerBase) handler(session);
        var requests = new ArrayList<SetBreakpointsArguments>();
        // Like the Fule adapter: each request installs the breakpoints with a new id, pending until the script is loaded
        var server = server(requests, "pending native script resolution");

        var dir = Files.createTempDirectory("dap-breakpoints-");
        var path = dir.resolve("a.lua");
        try {
            var file = file(path);
            XLineBreakpoint<?> nativeBreakpoint = WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                    .addLineBreakpoint((XLineBreakpointType) nativeType, file.getUrl(), 0, null));
            nativeBreakpoint.setCondition("a == 1");
            XLineBreakpoint<?> dapBreakpoint = WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                    .addLineBreakpoint(new DAPBreakpointType(), file.getUrl(), 0, new DAPBreakpointProperties()));

            handler.registerBreakpoint(nativeBreakpoint);
            handler.registerBreakpoint(dapBreakpoint);
            handler.initialize(server, new Capabilities()).join();
            assertNull(single(requests).getCondition());
            assertEquals(List.of("native inactive:" + DAPBreakpointHandlerBase.SHADOWED_BREAKPOINT_MESSAGE,
                    "dap invalid:pending native script resolution"), presentations);
            requests.clear();
            presentations.clear();

            // The DAP breakpoint is disabled in the IDE: IntelliJ unregisters it, then registers it again (ignored as disabled)
            dapBreakpoint.setEnabled(false);
            handler.unregisterBreakpoint(dapBreakpoint, false);
            handler.registerBreakpoint(dapBreakpoint);
            assertEquals("a == 1", single(requests).getCondition());
            assertEquals(List.of("native reset:null", "native invalid:pending native script resolution"), presentations);
            presentations.clear();

            // The script is loaded: the debug adapter verifies the native breakpoint by its id (DAP 'breakpoint' event)
            var changed = new Breakpoint();
            changed.setId(2);
            changed.setVerified(true);
            changed.setLine(1);
            handler.updateBreakpoint(changed, null);
            assertEquals(List.of("native verified"), presentations);
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    /** Describes an 'updateBreakpointPresentation' call: a reset (no icon) or the inactive dependent icon. */
    private static String presentation(Object[] args) {
        if (args[1] == null) {
            return "reset:" + args[2];
        }
        assertSame(AllIcons.Debugger.Db_dep_line_breakpoint, args[1]);
        return "inactive:" + args[2];
    }

    private static SourceBreakpoint single(List<SetBreakpointsArguments> requests) {
        assertEquals(1, requests.size());
        assertEquals(1, requests.get(0).getBreakpoints().length);
        return requests.get(0).getBreakpoints()[0];
    }

    private DAPBreakpointHandlerBase<XLineBreakpoint<DAPBreakpointProperties>> handler(XDebugSession session) throws Exception {
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
                return "shadowed-breakpoint";
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
                return true;
            }
        };
        return new DAPBreakpointHandlerBase<>(DAPBreakpointType.class, session, descriptor, getProject()) {};
    }

    private static IDebugProtocolServer server(List<SetBreakpointsArguments> requests) {
        return server(requests, null);
    }

    /**
     * Returns a debug adapter which answers each 'setBreakpoints' request with unverified breakpoints (id = request
     * number) and the given message, or with no breakpoints if the message is null.
     */
    private static IDebugProtocolServer server(List<SetBreakpointsArguments> requests, @Nullable String pendingMessage) {
        var requestCount = new java.util.concurrent.atomic.AtomicInteger();
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(), new Class[]{IDebugProtocolServer.class}, (p, m, a) -> {
            if (m.getName().equals("setBreakpoints")) {
                var arguments = (SetBreakpointsArguments) a[0];
                requests.add(arguments);
                int id = requestCount.incrementAndGet();
                var response = new SetBreakpointsResponse();
                if (pendingMessage == null) {
                    response.setBreakpoints(new Breakpoint[0]);
                } else {
                    response.setBreakpoints(Arrays.stream(arguments.getBreakpoints()).map(sourceBreakpoint -> {
                        var breakpoint = new Breakpoint();
                        breakpoint.setId(id);
                        breakpoint.setVerified(false);
                        breakpoint.setMessage(pendingMessage);
                        breakpoint.setLine(sourceBreakpoint.getLine());
                        return breakpoint;
                    }).toArray(Breakpoint[]::new));
                }
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

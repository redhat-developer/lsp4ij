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
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * A forwarded native breakpoint whose file is renamed or moved is removed from the source where it was sent,
 * also when its new file is not mapped to the debug adapter.
 */
public class DAPMovedFileBreakpointTest extends HeavyPlatformTestCase {

    private final List<SetBreakpointsArguments> requests = new ArrayList<>();
    private boolean failClears;
    private Path dir;
    private DAPBreakpointHandlerBase<?> sharedHandler;
    private DAPForwardedLineBreakpointHandler forwardedHandler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        dir = Files.createTempDirectory("dap-breakpoints-");
        var nativeType = new DAPShadowedBreakpointTest.NativeLineBreakpointType();
        XBreakpointType.EXTENSION_POINT_NAME.getPoint().registerExtension(nativeType, getTestRootDisposable());
        createHandlers();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            sharedHandler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        } finally {
            super.tearDown();
        }
    }

    public void testBreakpointIsClearedWhenItsFileIsNotMappedAnymore() throws Exception {
        var file = file("a.lua");
        var breakpoint = forwardedBreakpoint(file);

        // The file is renamed to a file which is not mapped to the debug adapter
        WriteAction.runAndWait(() -> file.rename(this, "a.txt"));
        forwardedHandler.unregisterBreakpoint(breakpoint, false);
        forwardedHandler.registerBreakpoint(breakpoint);

        Map<String, Integer> sent = sentBreakpoints();
        assertEquals(Integer.valueOf(0), sent.get("a.lua"));
        assertFalse(sent.containsKey("a.txt") && sent.get("a.txt") > 0);
    }

    public void testBreakpointIsMovedWithItsFile() throws Exception {
        var file = file("a.lua");
        var breakpoint = forwardedBreakpoint(file);

        // The file is renamed to another file which is mapped to the debug adapter
        WriteAction.runAndWait(() -> file.rename(this, "b.lua"));
        forwardedHandler.unregisterBreakpoint(breakpoint, false);
        forwardedHandler.registerBreakpoint(breakpoint);

        Map<String, Integer> sent = sentBreakpoints();
        assertEquals(Integer.valueOf(0), sent.get("a.lua"));
        assertEquals(Integer.valueOf(1), sent.get("b.lua"));
    }

    public void testFailedClearIsSentAgain() throws Exception {
        var file = file("a.lua");
        var breakpoint = forwardedBreakpoint(file);

        // The debug adapter fails to clear the breakpoints of the renamed file
        failClears = true;
        WriteAction.runAndWait(() -> file.rename(this, "a.txt"));
        forwardedHandler.unregisterBreakpoint(breakpoint, false);
        forwardedHandler.registerBreakpoint(breakpoint);
        assertEquals(Integer.valueOf(0), sentBreakpoints().get("a.lua"));
        assertTrue(sharedHandler.hasSentBreakpoints());

        // The next update sends the clear again
        failClears = false;
        requests.clear();
        var other = lineBreakpoint(file("c.lua"));
        forwardedHandler.registerBreakpoint(other);
        Map<String, Integer> sent = sentBreakpoints();
        assertEquals(Integer.valueOf(0), sent.get("a.lua"));
        assertEquals(Integer.valueOf(1), sent.get("c.lua"));

        // Once cleared, the file is not sent anymore
        requests.clear();
        forwardedHandler.unregisterBreakpoint(other, false);
        sent = sentBreakpoints();
        assertFalse(sent.containsKey("a.lua"));
        assertEquals(Integer.valueOf(0), sent.get("c.lua"));
        assertFalse(sharedHandler.hasSentBreakpoints());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private XLineBreakpoint<?> lineBreakpoint(VirtualFile file) {
        var nativeType = XBreakpointType.EXTENSION_POINT_NAME.findExtension(DAPShadowedBreakpointTest.NativeLineBreakpointType.class);
        return WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                .addLineBreakpoint((XLineBreakpointType) nativeType, file.getUrl(), 0, null));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private XLineBreakpoint<?> forwardedBreakpoint(VirtualFile file) {
        var nativeType = XBreakpointType.EXTENSION_POINT_NAME.findExtension(DAPShadowedBreakpointTest.NativeLineBreakpointType.class);
        XLineBreakpoint<?> breakpoint = WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                .addLineBreakpoint((XLineBreakpointType) nativeType, file.getUrl(), 0, null));
        forwardedHandler.registerBreakpoint(breakpoint);
        sharedHandler.initialize(server(), new Capabilities()).join();
        assertEquals(Map.of("a.lua", 1), sentBreakpoints());
        requests.clear();
        return breakpoint;
    }

    /**
     * Returns the number of breakpoints sent per file name, by the last request of each file.
     */
    private Map<String, Integer> sentBreakpoints() {
        Map<String, Integer> sent = new HashMap<>();
        for (var request : requests) {
            sent.put(Path.of(request.getSource().getPath()).getFileName().toString(), request.getBreakpoints().length);
        }
        return sent;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void createHandlers() {
        var type = Arrays.stream(com.intellij.execution.configurations.ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var config = type.getConfigurationFactories()[0].createTemplateConfiguration(getProject());
        com.intellij.execution.runners.ExecutionEnvironment environment;
        try {
            environment = ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), config).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        var descriptor = new com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor(new DAPRunConfigurationOptions(), environment, null) {
            @Override
            public com.intellij.execution.process.ProcessHandler startServer() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getId() {
                return "moved-file-breakpoint";
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
                // Only the Lua files are mapped to the debug adapter
                return file.getName().endsWith(".lua");
            }
        };
        var session = (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class[]{XDebugSession.class}, (p, m, a) -> null);
        sharedHandler = new DAPBreakpointHandlerBase<>(DAPBreakpointType.class, session, descriptor, getProject()) {
        };
        forwardedHandler = new DAPForwardedLineBreakpointHandler((Class) DAPShadowedBreakpointTest.NativeLineBreakpointType.class,
                sharedHandler, descriptor, getProject());
    }

    private IDebugProtocolServer server() {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(), new Class[]{IDebugProtocolServer.class}, (p, m, a) -> {
            if (m.getName().equals("setBreakpoints")) {
                var arguments = (SetBreakpointsArguments) a[0];
                requests.add(arguments);
                if (failClears && arguments.getBreakpoints().length == 0) {
                    return CompletableFuture.failedFuture(new IllegalStateException("setBreakpoints failed"));
                }
                var response = new SetBreakpointsResponse();
                response.setBreakpoints(new Breakpoint[0]);
                return CompletableFuture.completedFuture(response);
            }
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            throw new AssertionError(m.getName());
        });
    }

    private VirtualFile file(String name) throws Exception {
        var path = dir.resolve(name);
        Files.writeString(path, "local a = 1\nlocal b = 2\n");
        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(file);
        return file;
    }
}

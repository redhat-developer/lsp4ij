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
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
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
        var handler = handler();
        var requests = new ArrayList<SetBreakpointsArguments>();
        var server = server(requests);
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
    /**
     * A debug adapter which runs a tree of sessions, such as vscode-js-debug with a child session per target, sends
     * every breakpoint to every session, and only the session which owns the target binds it: the answers of the
     * other sessions must not show a bound breakpoint as invalid.
     */
    public void testBreakpointBoundByOneSessionStaysVerified() throws Exception {
        var states = new ArrayList<String>();
        var handler = handler(recordingSession(states));
        var dir = Files.createTempDirectory("dap-breakpoints-");
        var a = breakpoint(dir.resolve("a.lua"), 0);
        try {
            handler.registerBreakpoint(a);
            handler.initialize(server(false), new Capabilities()).join();
            assertEquals(List.of("invalid: Unbound breakpoint"), states);
            handler.initialize(server(true), new Capabilities()).join();
            assertEquals("verified", states.get(states.size() - 1));
            // A later session which cannot bind the breakpoint answers last
            handler.initialize(server(false), new Capabilities()).join();
            assertEquals("verified", states.get(states.size() - 1));
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    /**
     * Once the only session which bound a breakpoint has terminated, the breakpoint is shown as invalid, and a session
     * which cannot bind it decides.
     */
    public void testAForgottenSessionNoLongerKeepsTheBreakpointVerified() throws Exception {
        var states = new ArrayList<String>();
        var handler = handler(recordingSession(states));
        var dir = Files.createTempDirectory("dap-breakpoints-");
        var a = breakpoint(dir.resolve("a.lua"), 0);
        try {
            handler.registerBreakpoint(a);
            var bound = server(true);
            handler.initialize(bound, new Capabilities()).join();
            assertEquals(List.of("verified"), states);
            handler.forgetSession(bound);
            assertEquals(List.of("verified", "invalid: " + DAPBundle.message("dap.breakpoints.session.ended")), states);
            handler.initialize(server(false), new Capabilities()).join();
            assertEquals("invalid: Unbound breakpoint", states.get(states.size() - 1));
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    /** An answer which arrives after its breakpoint was unregistered changes nothing. */
    public void testALateAnswerForAnUnregisteredBreakpointIsIgnored() throws Exception {
        var states = new ArrayList<String>();
        var handler = handler(recordingSession(states));
        var dir = Files.createTempDirectory("dap-breakpoints-");
        var a = breakpoint(dir.resolve("a.lua"), 0);
        try {
            handler.registerBreakpoint(a);
            var server = server(false);
            handler.initialize(server, new Capabilities()).join();
            handler.unregisterBreakpoint(a, false);
            var before = List.copyOf(states);
            var late = new Breakpoint();
            late.setVerified(true);
            handler.updateBreakpoint(server, late, a);
            assertEquals(before, states);
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    public void testTheBreakpointsAreSentAgainToAnInitializedDebugAdapterOnly() throws Exception {
        var handler = handler();
        var requests = new ArrayList<SetBreakpointsArguments>();
        var server = server(requests);
        var dir = Files.createTempDirectory("dap-breakpoints-");
        try {
            handler.registerBreakpoint(breakpoint(dir.resolve("a.lua"), 0));
            handler.initialize(server, new Capabilities()).join();
            requests.clear();
            // As after a 'capabilities' event of the DAP server.
            handler.sendBreakpoints(server).join();
            assertLines(requests, dir.resolve("a.lua"), 1);
            // A DAP server which the handler was not initialized for gets them when it is.
            var others = new ArrayList<SetBreakpointsArguments>();
            handler.sendBreakpoints(server(others)).join();
            assertEmpty(others);
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    /**
     * Each session of a tree numbers its breakpoints itself, as vscode-js-debug does: a breakpoint has an id per
     * session, and two breakpoints can have the same id in two sessions. A 'breakpoint' event reaches the breakpoint
     * by the id which the session that sends it gave it.
     */
    public void testEachSessionNamesABreakpointByTheIdItGaveIt() throws Exception {
        var states = new ArrayList<String>();
        var handler = handler(recordingSessionByFile(states));
        var dir = Files.createTempDirectory("dap-breakpoints-");
        var a = breakpoint(dir.resolve("a.lua"), 0);
        var b = breakpoint(dir.resolve("b.lua"), 0);
        try {
            handler.registerBreakpoint(a);
            handler.registerBreakpoint(b);
            // The page session binds neither breakpoint, and the worker session binds both, with the ids swapped
            var page = server(false, Map.of("a.lua", 1, "b.lua", 2));
            var worker = server(true, Map.of("a.lua", 2, "b.lua", 1));
            handler.initialize(page, new Capabilities()).join();
            handler.initialize(worker, new Capabilities()).join();
            assertSame(a, handler.findBreakpointById(page, 1));
            assertSame(b, handler.findBreakpointById(page, 2));
            assertSame(b, handler.findBreakpointById(worker, 1));
            assertSame(a, handler.findBreakpointById(worker, 2));
            states.clear();

            // The worker unbinds its breakpoint 1, which is b: the page doesn't bind it either
            handler.updateBreakpoint(worker, answer(1, false, "Unbound breakpoint"), null);
            assertEquals(List.of("b.lua invalid: Unbound breakpoint"), states);
            states.clear();

            // The page binds its breakpoint 1, which is a, not the worker's breakpoint 1
            handler.updateBreakpoint(page, answer(1, true, null), null);
            assertEquals(List.of("a.lua verified"), states);
            states.clear();

            // Once the worker's session has ended, its ids name nothing, and the page still keeps a verified
            handler.forgetSession(worker);
            assertNull(handler.findBreakpointById(worker, 1));
            assertNull(handler.findBreakpointById(worker, 2));
            assertSame(a, handler.findBreakpointById(page, 1));
            assertEquals(List.of(), states);
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    /**
     * The answer to a request which a session got before it terminated changes nothing when it arrives after: it
     * neither shows the breakpoint as bound by that session, nor gives it an id of that session.
     */
    public void testALateAnswerOfASessionWhichEndedIsIgnored() throws Exception {
        var states = new ArrayList<String>();
        var handler = handler(recordingSession(states));
        var dir = Files.createTempDirectory("dap-breakpoints-");
        var a = breakpoint(dir.resolve("a.lua"), 0);
        try {
            handler.registerBreakpoint(a);
            var ended = server(true);
            handler.initialize(ended, new Capabilities()).join();
            handler.initialize(server(false), new Capabilities()).join();
            handler.forgetSession(ended);
            assertEquals("invalid: " + DAPBundle.message("dap.breakpoints.session.ended"), states.get(states.size() - 1));
            var before = List.copyOf(states);

            handler.setBreakpointId(ended, a, 7);
            handler.updateBreakpoint(ended, answer(7, true, null), a);

            assertEquals(before, states);
            assertNull(handler.getBreakpointId(ended, a));
            assertNull(handler.findBreakpointById(ended, 7));
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    /**
     * An unregistered breakpoint forgets the ids which the sessions gave it, and a late answer doesn't give it one back,
     * so that a late 'breakpoint' event doesn't reach it once it is registered again.
     */
    public void testAnUnregisteredBreakpointForgetsItsIds() throws Exception {
        var handler = handler(recordingSession(new ArrayList<>()));
        var dir = Files.createTempDirectory("dap-breakpoints-");
        var a = breakpoint(dir.resolve("a.lua"), 0);
        try {
            handler.registerBreakpoint(a);
            var server = server(true, Map.of("a.lua", 4));
            handler.initialize(server, new Capabilities()).join();
            assertSame(a, handler.findBreakpointById(server, 4));

            handler.unregisterBreakpoint(a, false);
            assertNull(handler.getBreakpointId(server, a));

            handler.setBreakpointId(server, a, 4);
            assertNull(handler.getBreakpointId(server, a));
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    /** Returns a debug session which records, in the given list, each state the breakpoints are shown in. */
    private static XDebugSession recordingSession(List<String> states) {
        return (XDebugSession)Proxy.newProxyInstance(XDebugSession.class.getClassLoader(),new Class<?>[]{XDebugSession.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "setBreakpointVerified" -> states.add("verified");
                case "setBreakpointInvalid" -> states.add("invalid: " + a[1]);
                default -> { }
            }
            return null;
        });
    }

    /**
     * Returns a debug session which records, in the given list, each state each breakpoint is shown in, after the name
     * of its file.
     */
    private static XDebugSession recordingSessionByFile(List<String> states) {
        return (XDebugSession)Proxy.newProxyInstance(XDebugSession.class.getClassLoader(),new Class<?>[]{XDebugSession.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "setBreakpointVerified" -> states.add(fileName(a[0]) + " verified");
                case "setBreakpointInvalid" -> states.add(fileName(a[0]) + " invalid: " + a[1]);
                default -> { }
            }
            return null;
        });
    }

    private static String fileName(Object breakpoint) {
        String url = ((XLineBreakpoint<?>) breakpoint).getFileUrl();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static Breakpoint answer(int id, boolean verified, String message) {
        var breakpoint = new Breakpoint();
        breakpoint.setId(id);
        breakpoint.setVerified(verified);
        breakpoint.setMessage(message);
        return breakpoint;
    }

    private DAPBreakpointHandlerBase<XLineBreakpoint<DAPBreakpointProperties>> handler() throws Exception {
        return handler((XDebugSession)Proxy.newProxyInstance(XDebugSession.class.getClassLoader(),new Class<?>[]{XDebugSession.class}, (p, m, a) -> null));
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
        return new DAPBreakpointHandlerBase<>(DAPBreakpointType.class, session, descriptor, getProject()) {};
    }

    /**
     * Returns a DAP server which answers each 'setBreakpoints' request with the given 'verified' flag, as a session of
     * vscode-js-debug which owns the target (true) or not (false).
     */
    private static IDebugProtocolServer server(boolean verified) {
        return server(verified, Map.of());
    }

    /**
     * Returns a DAP server which answers each 'setBreakpoints' request with the given 'verified' flag, and gives the
     * breakpoints of a file the id which the given map has for the name of that file, and 1 otherwise, as a session
     * which numbers its breakpoints itself.
     */
    private static IDebugProtocolServer server(boolean verified, Map<String, Integer> idsByFile) {
        return (IDebugProtocolServer)Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),new Class<?>[]{IDebugProtocolServer.class}, (p, m, a) -> {
            if (m.getName().equals("setBreakpoints")) {
                var arguments = (SetBreakpointsArguments) a[0];
                int id = idsByFile.getOrDefault(Path.of(arguments.getSource().getPath()).getFileName().toString(), 1);
                var breakpoints = Arrays.stream(arguments.getBreakpoints()).map(source -> {
                    var breakpoint = new Breakpoint();
                    breakpoint.setId(id);
                    breakpoint.setLine(source.getLine());
                    breakpoint.setVerified(verified);
                    breakpoint.setMessage(verified ? null : "Unbound breakpoint");
                    return breakpoint;
                }).toArray(Breakpoint[]::new);
                var response = new SetBreakpointsResponse();
                response.setBreakpoints(breakpoints);
                return CompletableFuture.completedFuture(response);
            }
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            throw new AssertionError(m.getName());
        });
    }

    private static IDebugProtocolServer server(List<SetBreakpointsArguments> requests) {
        return (IDebugProtocolServer)Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),new Class<?>[]{IDebugProtocolServer.class}, (p, m, a) -> {
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
    }

    private XLineBreakpoint<DAPBreakpointProperties> breakpoint(Path path, int line) throws Exception {
        Files.writeString(path, "local a = 1\nlocal b = 2\n");
        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(file);
        return WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
            .addLineBreakpoint(new DAPBreakpointType(), file.getUrl(), line, new DAPBreakpointProperties()));
    }
    private void assertLines(List<SetBreakpointsArguments> requests, Path path, int... lines) throws Exception {
        // IntelliJ may canonicalize Windows paths (including short directory names).
        // Match the source file rather than its platform-dependent path spelling.
        var matches = new ArrayList<SetBreakpointsArguments>();
        for (var request : requests) {
            if (Files.isSameFile(path, Path.of(request.getSource().getPath()))) {
                matches.add(request);
            }
        }
        assertEquals("Expected a replacement request for " + path, 1, matches.size());
        assertTrue(Arrays.equals(lines, Arrays.stream(matches.get(0).getBreakpoints()).mapToInt(SourceBreakpoint::getLine).toArray()));
    }
}

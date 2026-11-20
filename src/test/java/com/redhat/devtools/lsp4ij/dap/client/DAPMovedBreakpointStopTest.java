// SPDX-License-Identifier: EPL-2.0
package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointHandlerBase;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointProperties;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointType;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A stop without 'hitBreakpointIds' at a breakpoint which the debug adapter moved to another line is a stop at that
 * breakpoint (see redhat-developer/lsp4ij#1999).
 */
public class DAPMovedBreakpointStopTest extends HeavyPlatformTestCase {

    // The example of the issue: debugpy moves a breakpoint on the blank line 3 or the comment on line 4 to line 2
    private static final String CONTENT = """
            def run():
                total = 0

                # comment
                for i in range(3):
                    total += i
            """;

    private Path dir;
    private VirtualFile file;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        dir = Files.createTempDirectory("dap-moved-breakpoint-");
        Path path = dir.resolve("moved.py");
        Files.writeString(path, CONTENT);
        file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(file);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        } finally {
            super.tearDown();
        }
    }

    /**
     * Recorded with the debugpy bundled in IntelliJ IDEA 2026.3: the breakpoint requested on line 3 is answered on
     * line 2, and the 'stopped' event on line 2 has reason 'breakpoint' and no 'hitBreakpointIds'.
     */
    public void testDebugpyStopWithoutHitBreakpointIdsIsAtTheMovedBreakpoint() throws Exception {
        var blankLine = breakpoint(3);
        var handler = handler();
        try {
            handler.registerBreakpoint(blankLine);
            handler.initialize(server(lines -> 2), new Capabilities()).join();

            assertSame(blankLine, stopBreakpoint(handler, frame(2)));
        } finally {
            handler.dispose();
        }
    }

    /**
     * The breakpoints on the blank line 3 and on the comment on line 4 are both answered on line 2: a stop there is
     * at one of them, and a stop on their own lines, which the debug adapter moved them away from, is at none.
     */
    public void testStopIsNotAtTheLineWhichTheBreakpointWasMovedAwayFrom() throws Exception {
        var blankLine = breakpoint(3);
        var comment = breakpoint(4);
        var handler = handler();
        try {
            handler.registerBreakpoint(blankLine);
            handler.registerBreakpoint(comment);
            var server = server(lines -> 2);
            handler.initialize(server, new Capabilities()).join();

            var stopped = stopBreakpoint(handler, server, frame(2));
            assertTrue(String.valueOf(stopped), stopped == blankLine || stopped == comment);
            assertNull(stopBreakpoint(handler, server, frame(3)));
            assertNull(stopBreakpoint(handler, server, frame(4)));
        } finally {
            handler.dispose();
        }
    }

    /** Line 2 has a breakpoint, and the one of the blank line 3 is moved to line 2: the one of line 2 stops. */
    public void testBreakpointWhichWasNotMovedWins() throws Exception {
        var blankLine = breakpoint(3);
        var statement = breakpoint(2);
        var handler = handler();
        try {
            // The moved breakpoint first, so that it is not found first by chance
            handler.registerBreakpoint(blankLine);
            handler.registerBreakpoint(statement);
            var server = server(lines -> 2);
            handler.initialize(server, new Capabilities()).join();

            assertSame(statement, stopBreakpoint(handler, server, frame(2)));
        } finally {
            handler.dispose();
        }
    }

    /**
     * In a tree of sessions ('startDebugging'), each session installs the breakpoint itself: one moves it from line 3
     * to line 2, the other keeps it on line 3. A stop is compared with the line of the session which stopped.
     */
    public void testStopIsComparedWithTheLineOfTheSessionWhichStopped() throws Exception {
        var blankLine = breakpoint(3);
        var handler = handler();
        try {
            handler.registerBreakpoint(blankLine);
            var moves = server(lines -> 2);
            var keeps = server(lines -> lines);
            handler.initialize(moves, new Capabilities()).join();
            handler.initialize(keeps, new Capabilities()).join();

            assertSame(blankLine, stopBreakpoint(handler, moves, frame(2)));
            assertNull(stopBreakpoint(handler, moves, frame(3)));
            assertSame(blankLine, stopBreakpoint(handler, keeps, frame(3)));
            assertNull(stopBreakpoint(handler, keeps, frame(2)));

            // Once the session which moved it has ended, a stop of the other one is compared with its own line only
            handler.forgetSession(moves);
            assertNull(stopBreakpoint(handler, frame(2)));
            assertSame(blankLine, stopBreakpoint(handler, frame(3)));
        } finally {
            handler.dispose();
        }
    }

    /**
     * A session which starts while the 'setBreakpoints' request of another one is not answered yet doesn't make
     * that answer stale: each session answers its own requests, so the moved line of the other one is kept.
     */
    public void testAnswerOfASessionIsKeptWhenAnotherSessionStartsMeanwhile() throws Exception {
        var blankLine = breakpoint(3);
        var handler = handler();
        try {
            handler.registerBreakpoint(blankLine);
            var pending = new CompletableFuture<SetBreakpointsResponse>();
            var moves = serverAnswering(arguments -> pending);
            var keeps = server(lines -> lines);
            // The first session sends its breakpoints, and answers only after the second session sent its own
            var movesSent = handler.initialize(moves, new Capabilities());
            handler.initialize(keeps, new Capabilities()).join();
            pending.complete(response(2));
            movesSent.join();

            assertSame(blankLine, stopBreakpoint(handler, moves, frame(2)));
            assertSame(blankLine, stopBreakpoint(handler, keeps, frame(3)));
        } finally {
            handler.dispose();
        }
    }

    /** A later answer which reports no line forgets the line of an earlier one: the IntelliJ line counts again. */
    public void testLaterAnswerWithoutLineForgetsTheMovedLine() throws Exception {
        var blankLine = breakpoint(3);
        var statement = breakpoint(5);
        var handler = handler();
        try {
            handler.registerBreakpoint(blankLine);
            var answeredLines = new java.util.concurrent.atomic.AtomicReference<Function<Integer, Integer>>(lines -> lines == 3 ? 2 : lines);
            var server = server(lines -> answeredLines.get().apply(lines));
            handler.initialize(server, new Capabilities()).join();
            assertSame(blankLine, stopBreakpoint(handler, server, frame(2)));

            // The next 'setBreakpoints' request, sent when a breakpoint is added, is answered without lines
            answeredLines.set(lines -> null);
            handler.registerBreakpoint(statement);
            assertNull(stopBreakpoint(handler, server, frame(2)));
            assertSame(blankLine, stopBreakpoint(handler, server, frame(3)));
        } finally {
            handler.dispose();
        }
    }

    /** A 'changed' breakpoint event which moves the breakpoint to another line moves its stop too. */
    public void testChangedBreakpointEventMovesTheStop() throws Exception {
        var blankLine = breakpoint(3);
        var handler = handler();
        try {
            handler.registerBreakpoint(blankLine);
            var server = server(lines -> lines);
            handler.initialize(server, new Capabilities()).join();
            assertSame(blankLine, stopBreakpoint(handler, server, frame(3)));

            // The id of the breakpoint is the line of its request (see server())
            var changed = new Breakpoint();
            changed.setId(3);
            changed.setVerified(true);
            changed.setLine(5);
            handler.updateBreakpoint(server, changed, null);
            assertSame(blankLine, stopBreakpoint(handler, server, frame(5)));
            assertNull(stopBreakpoint(handler, server, frame(3)));
        } finally {
            handler.dispose();
        }
    }

    // Line numbers are 1-based, as in DAP
    private XLineBreakpoint<DAPBreakpointProperties> breakpoint(int line) {
        return WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                .addLineBreakpoint(new DAPBreakpointType(), file.getUrl(), line - 1, new DAPBreakpointProperties()));
    }

    private StackFrame frame(int line) {
        var source = new Source();
        source.setPath(file.toNioPath().toString());
        var frame = new StackFrame();
        frame.setSource(source);
        frame.setLine(line);
        return frame;
    }

    /**
     * Returns the breakpoint of a debugpy 'stopped' event at the given frame as DAPClient finds it: reason
     * 'breakpoint', no 'hitBreakpointIds'.
     */
    private static XBreakpoint<?> stopBreakpoint(DAPBreakpointHandlerBase<?> handler, StackFrame topFrame) {
        var stopped = new StoppedEventArguments();
        stopped.setReason(StoppedEventArgumentsReason.BREAKPOINT);
        return DAPClient.getStopBreakpoint(stopped, handler::findBreakpointById, () -> handler.findBreakpoint(topFrame));
    }

    /** As {@link #stopBreakpoint(DAPBreakpointHandlerBase, StackFrame)}, for a stop of the given DAP server. */
    private static XBreakpoint<?> stopBreakpoint(DAPBreakpointHandlerBase<?> handler, IDebugProtocolServer server, StackFrame topFrame) {
        var stopped = new StoppedEventArguments();
        stopped.setReason(StoppedEventArgumentsReason.BREAKPOINT);
        return DAPClient.getStopBreakpoint(stopped, id -> handler.findBreakpointById(server, id), () -> handler.findBreakpoint(server, topFrame));
    }

    /**
     * Returns a debug adapter which answers each source breakpoint as verified, with the id of its line in the
     * request and the line which the given function gives for that line.
     */
    private static IDebugProtocolServer server(Function<Integer, Integer> answeredLine) {
        return serverAnswering(arguments -> {
            var response = new SetBreakpointsResponse();
            response.setBreakpoints(Arrays.stream(arguments.getBreakpoints()).map(sourceBreakpoint -> {
                var breakpoint = new Breakpoint();
                breakpoint.setId(sourceBreakpoint.getLine());
                breakpoint.setVerified(true);
                breakpoint.setLine(answeredLine.apply(sourceBreakpoint.getLine()));
                return breakpoint;
            }).toArray(Breakpoint[]::new));
            return CompletableFuture.completedFuture(response);
        });
    }

    /** Returns a verified breakpoint answer for one source breakpoint, with id 3 and the given line. */
    private static SetBreakpointsResponse response(int line) {
        var breakpoint = new Breakpoint();
        breakpoint.setId(3);
        breakpoint.setVerified(true);
        breakpoint.setLine(line);
        var response = new SetBreakpointsResponse();
        response.setBreakpoints(new Breakpoint[]{breakpoint});
        return response;
    }

    /** Returns a debug adapter which answers each 'setBreakpoints' request with the given function. */
    private static IDebugProtocolServer serverAnswering(Function<SetBreakpointsArguments, CompletableFuture<SetBreakpointsResponse>> setBreakpoints) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(), new Class<?>[]{IDebugProtocolServer.class}, (p, m, a) -> {
            if (m.getName().equals("setBreakpoints")) {
                return setBreakpoints.apply((SetBreakpointsArguments) a[0]);
            }
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("toString")) return "debug adapter";
            throw new AssertionError(m.getName());
        });
    }

    private DAPBreakpointHandlerBase<XLineBreakpoint<DAPBreakpointProperties>> handler() throws Exception {
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
                return "moved-breakpoint-stop";
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
        var session = (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class<?>[]{XDebugSession.class}, (p, m, a) -> null);
        return new DAPBreakpointHandlerBase<>(DAPBreakpointType.class, session, descriptor, getProject()) {
        };
    }
}

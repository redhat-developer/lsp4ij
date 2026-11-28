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
package com.redhat.devtools.lsp4ij.dap.threads;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CustomizedDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.ui.PopupHandler;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.client.DAPExecutionStack;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.StartDebuggingRequestArguments;
import org.eclipse.lsp4j.debug.StartDebuggingRequestArgumentsType;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.TerminateThreadsArguments;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.eclipse.lsp4j.debug.ThreadEventArgumentsReason;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

/**
 * "Terminate Thread" in the context menu of the "Threads" panel, and in the one of IntelliJ's Frames view, against a
 * DAP server in the test's process which the session attaches to: it is shown when the DAP server supports
 * 'terminateThreads', and sends it with the selected threads, or the thread of the selected frame, to the DAP session
 * they are threads of, without the EDT waiting for the answer.
 */
public class DAPTerminateThreadSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    /**
     * How long a test waits for a request which must not come.
     */
    private static final int NO_REQUEST_SECONDS = 2;
    /**
     * The context menu of IntelliJ's Frames view (XDebuggerActions.FRAMES_TREE_POPUP_GROUP).
     */
    private static final String FRAMES_VIEW_POPUP_GROUP_ID = "XDebugger.Frames.Tree.Popup";

    private SessionServer server;
    private XDebugSession session;

    @Override
    protected void tearDown() throws Exception {
        try {
            if (session != null && !session.isStopped()) {
                session.stop();
            }
            if (server != null) {
                server.close();
            }
        } finally {
            super.tearDown();
        }
    }

    public void testTheThreadsPanelHasAContextMenuWithTerminateThread() throws Exception {
        debug(true);

        var table = getTable();
        assertTrue("The table of the Threads panel has a context menu",
                Arrays.stream(table.getMouseListeners()).anyMatch(PopupHandler.class::isInstance));
        var actionManager = ActionManager.getInstance();
        var group = (DefaultActionGroup) actionManager.getAction(ThreadsPanel.POPUP_GROUP_ID);
        assertEquals(List.of(DAPTerminateThreadAction.ID),
                Arrays.stream(group.getChildActionsOrStubs()).map(actionManager::getId).toList());
        assertEquals("Terminate Thread", terminateThread().getTemplatePresentation().getText());
    }

    public void testTerminateThreadSendsTerminateThreadsWithTheSelectedThreads() throws Exception {
        var root = debug(true);
        var table = getTable();
        table.setRowSelectionInterval(1, 1);

        var event = update();
        assertTrue(event.getPresentation().isEnabledAndVisible());
        // The DAP server answers once the test tells it to: the EDT doesn't wait for the answer
        terminateThread().actionPerformed(event);

        var args = root.terminateThreads.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'terminateThreads'", args);
        assertEquals(List.of(Session.WORKER), Arrays.stream(args.getThreadIds()).boxed().toList());
        assertFalse("The DAP server hasn't answered yet", root.terminateAnswer.isDone());
        root.answerTerminateThreads();
    }

    public void testTerminateThreadSendsEachSelectedThread() throws Exception {
        var root = debug(true);
        getTable().setRowSelectionInterval(0, 1);

        terminateThread().actionPerformed(update());

        var args = root.terminateThreads.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'terminateThreads'", args);
        assertEquals(List.of(Session.MAIN, Session.WORKER), Arrays.stream(args.getThreadIds()).boxed().toList());
        root.answerTerminateThreads();
    }

    public void testTerminateThreadIsDisabledWithoutASelectedThread() throws Exception {
        debug(true);

        var event = update();
        assertTrue(event.getPresentation().isVisible());
        assertFalse(event.getPresentation().isEnabled());
    }

    public void testTerminateThreadIsHiddenWhenTheDAPServerDoesNotSupportTerminateThreads() throws Exception {
        var root = debug(false);
        getTable().setRowSelectionInterval(1, 1);

        var event = update();
        assertFalse(event.getPresentation().isVisible());
        assertFalse(event.getPresentation().isEnabled());

        // Even when it is performed, as from a shortcut, the DAP server gets no request it doesn't support
        terminateThread().actionPerformed(event);
        assertNull(root.terminateThreads.poll(NO_REQUEST_SECONDS, TimeUnit.SECONDS));
    }

    public void testTerminateThreadTerminatesTheThreadsOfTheSessionWhichTheyBelongTo() throws Exception {
        var root = debug(true);
        var child = startChildSession(root);

        // The child session numbers its threads itself, and the panel shows them once it stops
        child.stop(Session.WORKER);
        PlatformTestUtil.waitWithEventsDispatching("the Threads panel shows the threads of the child session",
                () -> getTable().getRowCount() == Session.CHILD_THREADS.size(), BOUND_SECONDS);
        getTable().setRowSelectionInterval(1, 1);
        terminateThread().actionPerformed(update());

        var args = child.terminateThreads.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server of the child session receives 'terminateThreads'", args);
        assertEquals(List.of(Session.WORKER), Arrays.stream(args.getThreadIds()).boxed().toList());
        assertNull("The root session gets no request", root.terminateThreads.poll(NO_REQUEST_SECONDS, TimeUnit.SECONDS));
        child.answerTerminateThreads();
    }

    public void testTheRowsOfAChildSessionAreItsOwnThreads() throws Exception {
        var root = debug(true);
        var child = startChildSession(root);

        // Thread 1 is 'main' in the root session, and 'page' in the child session, where it stopped
        showThreadsOf(child);
        assertEquals(List.of("page [stopped]", "worker [running]", "service worker [running]"), getRows());
        getTable().setRowSelectionInterval(0, 0);
        terminateThread().actionPerformed(update());

        var args = child.terminateThreads.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server of the child session receives 'terminateThreads'", args);
        assertEquals(List.of(Session.MAIN), Arrays.stream(args.getThreadIds()).boxed().toList());
        assertNull("The root session gets no request", root.terminateThreads.poll(NO_REQUEST_SECONDS, TimeUnit.SECONDS));
        child.answerTerminateThreads();
    }

    public void testTheRowsOfTheRootSessionAreItsOwnThreadsAgainAfterThoseOfAChildSession() throws Exception {
        var root = debug(true);
        var child = startChildSession(root);
        showThreadsOf(child);
        child.sendThreadEvent(ThreadEventArgumentsReason.EXITED, Session.MAIN);
        PlatformTestUtil.waitWithEventsDispatching(() -> "the Threads panel shows that 'page' exited, not " + getRows(),
                () -> getRows().get(0).equals("page [exited]"), BOUND_SECONDS);

        // The run state of the child session's thread 1 is not that of the root session's thread 1
        showThreadsOf(root);
        assertEquals(List.of("main [stopped]", "worker [running]"), getRows());
        getTable().setRowSelectionInterval(0, 0);
        terminateThread().actionPerformed(update());

        var args = root.terminateThreads.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server of the root session receives 'terminateThreads'", args);
        assertEquals(List.of(Session.MAIN), Arrays.stream(args.getThreadIds()).boxed().toList());
        assertNull("The child session gets no request", child.terminateThreads.poll(NO_REQUEST_SECONDS, TimeUnit.SECONDS));
        root.answerTerminateThreads();
    }

    public void testAThreadEventOfAnotherSessionDoesNotChangeTheRows() throws Exception {
        var root = debug(true);
        var child = startChildSession(root);

        // The panel shows the threads of the root session, and the child session's thread 1 exits
        child.sendThreadEvent(ThreadEventArgumentsReason.EXITED, Session.MAIN);
        waitUntilHandled(child);

        assertEquals(List.of("main [running]", "worker [running]"), getRows());
    }

    public void testTheContextMenuOfTheFramesViewHasTerminateThread() {
        var actionManager = ActionManager.getInstance();
        var group = actionManager.getAction(FRAMES_VIEW_POPUP_GROUP_ID);
        assertInstanceOf(group, DefaultActionGroup.class);
        assertContainsElements(Arrays.stream(((DefaultActionGroup) group).getChildActionsOrStubs()).map(actionManager::getId).toList(),
                DAPTerminateThreadAction.ID);
    }

    public void testTerminateThreadInTheFramesViewSendsTerminateThreadsWithTheThreadOfTheSelectedFrame() throws Exception {
        var root = debug(true);
        // The program stops in main, and the user right-clicks a frame of the worker in the Frames view
        stopIn(root, Session.MAIN);
        selectTopFrameOf(Session.WORKER);

        var event = updateInFramesView();
        assertTrue("The Frames view offers Terminate Thread for the frame of the worker", event.getPresentation().isEnabledAndVisible());
        terminateThread().actionPerformed(event);

        var args = root.terminateThreads.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'terminateThreads'", args);
        assertEquals(List.of(Session.WORKER), Arrays.stream(args.getThreadIds()).boxed().toList());
        root.answerTerminateThreads();
    }

    public void testTerminateThreadInTheFramesViewTerminatesTheThreadInTheSessionOfTheFrame() throws Exception {
        var root = debug(true);
        var child = startChildSession(root);
        // The sessions of a tree share the debug session, which shows the frames of the child session once it stops
        stopIn(child, Session.MAIN);
        selectTopFrameOf(Session.WORKER);

        terminateThread().actionPerformed(updateInFramesView());

        var args = child.terminateThreads.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server of the child session receives 'terminateThreads'", args);
        assertEquals(List.of(Session.WORKER), Arrays.stream(args.getThreadIds()).boxed().toList());
        assertNull("The root session gets no request", root.terminateThreads.poll(NO_REQUEST_SECONDS, TimeUnit.SECONDS));
        child.answerTerminateThreads();
    }

    public void testTerminateThreadIsNotInTheFramesViewWhenTheDAPServerDoesNotSupportTerminateThreads() throws Exception {
        var root = debug(false);
        stopIn(root, Session.MAIN);
        selectTopFrameOf(Session.WORKER);

        assertNotOfferedInFramesView(root);
    }

    public void testTerminateThreadIsNotInTheFramesViewForAFrameOfAnotherDebugger() throws Exception {
        var root = debug(true);
        stopIn(root, Session.MAIN);
        // As the frames of a debugger other than DAP's, which the Frames view shows with the same context menu
        session.setCurrentStackFrame(executionStackOf(Session.WORKER), new XStackFrame() {
        }, true);

        assertNotOfferedInFramesView(root);
    }

    public void testTerminateThreadIsNotInTheFramesViewForAFrameWhichDoesNotKnowItsThread() throws Exception {
        var root = debug(true);
        stopIn(root, Session.MAIN);
        var stack = executionStackOf(Session.WORKER);
        var frame = (DAPStackFrame) framesOf(stack).get(0);
        // As a frame which code that extends LSP4IJ creates with a constructor which doesn't take the thread
        session.setCurrentStackFrame(stack, new DAPStackFrame(frame.getClient(), frame.getStackFrame(), frame.getExecutionEpoch()), true);

        assertNotOfferedInFramesView(root);
    }

    public void testAThreadWhichThePanelDoesNotShowReloadsTheThreadsOfItsSession() throws Exception {
        var root = debug(true);
        var child = startChildSession(root);
        showThreadsOf(child);

        child.startThread("web worker");

        PlatformTestUtil.waitWithEventsDispatching(() -> "the Threads panel shows the new thread of the child session, not " + getRows(),
                () -> getRows().equals(List.of("page [stopped]", "worker [running]", "service worker [running]", "web worker [running]")),
                BOUND_SECONDS);
    }

    /**
     * Starts debugging, attached to a new {@link SessionServer}, and returns the root session once the Threads panel
     * shows its threads.
     */
    private @NotNull Session debug(boolean supportsTerminateThreads) throws Exception {
        server = new SessionServer(supportsTerminateThreads);
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("terminate threads", type.getConfigurationFactories()[0]);
        var configuration = (DAPRunConfiguration) settings.getConfiguration();
        configuration.setDebugMode(DebugMode.ATTACH);
        configuration.setAttachAddress(InetAddress.getLoopbackAddress().getHostAddress());
        configuration.setAttachPort(String.valueOf(server.getPort()));
        configuration.setAttachConfiguration("{}");
        // the debug process connects to the DAP server in the background, as it does outside tests
        PlatformTestUtil.withSystemProperty("intellij.progress.task.ignoreHeadless", "true", () ->
                ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), configuration)
                        .buildAndExecute());
        var root = nextSession("the debug session connects");
        PlatformTestUtil.waitWithEventsDispatching("the Threads panel shows the threads of the root session", () -> {
            // no session tab is selected in tests, so there is no current session
            var sessions = XDebuggerManager.getInstance(getProject()).getDebugSessions();
            session = sessions.length == 1 ? sessions[0] : null;
            return session != null && root.configured.isDone() && getTable().getRowCount() == Session.ROOT_THREADS.size();
        }, BOUND_SECONDS);
        return root;
    }

    /**
     * Asks the client of the given session to start a child session, and returns it once it is configured.
     */
    private @NotNull Session startChildSession(@NotNull Session parent) {
        parent.startChildSession();
        var child = nextSession("the child session connects");
        PlatformTestUtil.waitWithEventsDispatching("the child session is configured", child.configured::isDone, BOUND_SECONDS);
        return child;
    }

    private @NotNull Session nextSession(@NotNull String what) {
        PlatformTestUtil.waitWithEventsDispatching(what, () -> !server.sessions.isEmpty(), BOUND_SECONDS);
        return server.sessions.remove();
    }

    private @NotNull JTable getTable() {
        var debugProcess = (DAPDebugProcess) session.getDebugProcess();
        return (JTable) debugProcess.getThreadsPanel().getDefaultFocusedComponent();
    }

    /**
     * Makes the given session stop, and waits for the Threads panel to show its threads, as it does at each stop.
     */
    private void showThreadsOf(@NotNull Session stopped) {
        stopped.stop(Session.MAIN);
        PlatformTestUtil.waitWithEventsDispatching("the Threads panel shows the threads of the session which stopped",
                () -> getTable().getRowCount() == stopped.threads.size(), BOUND_SECONDS);
    }

    /**
     * Returns the text of each row of the Threads panel, as it shows it: the name of the thread and its run state.
     */
    private @NotNull List<String> getRows() {
        var table = getTable();
        return IntStream.range(0, table.getRowCount())
                .mapToObj(row -> table.prepareRenderer(table.getCellRenderer(row, 0), row, 0).toString())
                .toList();
    }

    /**
     * Waits until the client has handled the messages which the given session sent, and the EDT what they asked of
     * it: the client handles the messages of a connection one after the other, so once it has answered a request which
     * the session sends after them, it has handled them.
     */
    private static void waitUntilHandled(@NotNull Session sender) {
        var answer = sender.ping();
        PlatformTestUtil.waitWithEventsDispatching("the client handles the messages of the session", answer::isDone, BOUND_SECONDS);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private static @NotNull AnAction terminateThread() {
        var action = ActionManager.getInstance().getAction(DAPTerminateThreadAction.ID);
        assertInstanceOf(action, DAPTerminateThreadAction.class);
        return action;
    }

    /**
     * Updates "Terminate Thread" as the context menu of the Threads panel's table does, with the data of the panel,
     * which the IDE gets from it as a UiDataProvider above the table. The headless DataManager of the tests gives no
     * data of a component.
     */
    private @NotNull AnActionEvent update() {
        var panel = ((DAPDebugProcess) session.getDebugProcess()).getThreadsPanel();
        assertTrue(SwingUtilities.isDescendingFrom(getTable(), panel));
        var action = terminateThread();
        var event = TestActionEvent.createTestEvent(action, CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT, panel::uiDataSnapshot));
        action.update(event);
        return event;
    }

    /**
     * Makes the given session stop in the given thread, and waits until the debug session is suspended in the top frame
     * of that thread, as the Frames view then shows it.
     */
    private void stopIn(@NotNull Session stopped, int threadId) {
        stopped.stop(threadId);
        PlatformTestUtil.waitWithEventsDispatching(
                () -> "the debug session is suspended in the frame of thread " + threadId + ", not in " + session.getCurrentStackFrame(),
                () -> session.isSuspended()
                        && session.getCurrentStackFrame() instanceof DAPStackFrame frame
                        && frame.getFrameId() == Session.frameId(threadId),
                BOUND_SECONDS);
    }

    /**
     * Returns the execution stack of the given thread at the stop where the debug session is suspended.
     */
    private @NotNull DAPExecutionStack executionStackOf(int threadId) {
        var context = session.getSuspendContext();
        assertNotNull("the debug session is suspended", context);
        return Arrays.stream(context.getExecutionStacks())
                .map(DAPExecutionStack.class::cast)
                .filter(stack -> stack.getThreadId() == threadId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the stop has no execution stack of thread " + threadId));
    }

    /**
     * Returns the frames of the given execution stack, which asks the DAP server for them when the stack is of a thread
     * other than the one which stopped, as when the Frames view shows that thread.
     */
    private static @NotNull List<XStackFrame> framesOf(@NotNull XExecutionStack stack) {
        var frames = new ArrayList<XStackFrame>();
        var loaded = new CompletableFuture<List<XStackFrame>>();
        stack.computeStackFrames(0, new XExecutionStack.XStackFrameContainer() {
            @Override
            public void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, boolean last) {
                frames.addAll(stackFrames);
                if (last) {
                    loaded.complete(List.copyOf(frames));
                }
            }

            @Override
            public void errorOccurred(@NotNull String errorMessage) {
                loaded.completeExceptionally(new AssertionError(errorMessage));
            }

            @Override
            public boolean isObsolete() {
                return false;
            }
        });
        PlatformTestUtil.waitWithEventsDispatching("the frames of " + stack.getDisplayName() + " load", loaded::isDone, BOUND_SECONDS);
        var result = loaded.join();
        assertFalse("the DAP server sent frames of " + stack.getDisplayName(), result.isEmpty());
        return result;
    }

    /**
     * Selects the top frame of the given thread in the Frames view, as a right-click on it does. The Frames view
     * (XFramesView) selects the frame under the mouse before it shows its context menu, then makes the selected frame
     * the session's current frame, with the execution stack of its thread, with XDebugSession#setCurrentStackFrame,
     * which this calls. In the split debugger (2026.x), the backend session does the same when the frontend tells it
     * the frame.
     */
    private void selectTopFrameOf(int threadId) {
        var stack = executionStackOf(threadId);
        var frame = framesOf(stack).get(0);
        session.setCurrentStackFrame(stack, frame, true);
        assertSame("the selected frame is the current frame of the debug session", frame, session.getCurrentStackFrame());
    }

    /**
     * Updates "Terminate Thread" as the context menu of IntelliJ's Frames view does, in the background as the action
     * asks, with the data which the menu has there:
     * <ul>
     *     <li>The frames list (XDebuggerFramesList), the component of the menu, gives no data of the frame: only the
     *     list itself, under a private key, and the navigatable and the file of the selected frame. Unlike the Threads
     *     panel, it gives no {@link ThreadsPanel#SELECTED_THREADS}.</li>
     *     <li>The tab of the debug session above it (XDebugSessionTab) gives {@link XDebugSession#DATA_KEY}, in
     *     2025.x. In the split debugger (2026.x), the tab gives an internal session proxy instead, and the action takes
     *     the project's current session, which tests don't have, as no session tab is selected in tests.</li>
     *     <li>The IDE gives the project.</li>
     * </ul>
     * The EDT waits for the update without dispatching events, so that the frame which the test selected stays the
     * session's current frame until the action is performed. In the split debugger (2026.x) of the tests, the backend
     * session tells the frontend each frame which it is given, and the frontend's Frames view selects one of its own
     * frames in turn, on the EDT, which replaces a frame that the view doesn't show.
     */
    private @NotNull AnActionEvent updateInFramesView() throws Exception {
        var action = terminateThread();
        var context = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, getProject())
                .add(XDebugSession.DATA_KEY, session)
                .build();
        var event = TestActionEvent.createTestEvent(action, context);
        assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());
        var updated = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.run(() -> action.update(event)));
        try {
            updated.get(BOUND_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("the update of Terminate Thread in the background doesn't end", e);
        }
        return event;
    }

    /**
     * Asserts that the context menu of the Frames view doesn't offer "Terminate Thread", and that performing it anyway,
     * as from a shortcut, sends the given session's DAP server no 'terminateThreads'.
     */
    private void assertNotOfferedInFramesView(@NotNull Session root) throws Exception {
        var event = updateInFramesView();
        assertFalse("The Frames view doesn't show Terminate Thread", event.getPresentation().isVisible());
        assertFalse("Terminate Thread is disabled in the Frames view", event.getPresentation().isEnabled());

        terminateThread().actionPerformed(event);
        assertNull("The DAP server gets no 'terminateThreads'", root.terminateThreads.poll(NO_REQUEST_SECONDS, TimeUnit.SECONDS));
    }

    /**
     * A DAP server which accepts a connection per DAP session, as a debug adapter that starts child sessions with
     * 'startDebugging' does.
     */
    private static class SessionServer {

        private final ServerSocket serverSocket;
        // Runs the loop which accepts the connections and the message loop of each session, so that close() can stop
        // them all: the test framework reports the threads which a test leaves running
        private final ExecutorService executor = Executors.newCachedThreadPool();
        // The sessions which no test has taken yet, and all of them
        private final LinkedBlockingQueue<Session> sessions = new LinkedBlockingQueue<>();
        private final List<Session> all = new CopyOnWriteArrayList<>();
        private final Future<?> accepting;

        SessionServer(boolean supportsTerminateThreads) throws IOException {
            this.serverSocket = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
            this.accepting = executor.submit(() -> {
                try {
                    while (!serverSocket.isClosed()) {
                        // The first session is the root one, the next ones its children
                        var threads = all.isEmpty() ? Session.ROOT_THREADS : Session.CHILD_THREADS;
                        var session = new Session(serverSocket.accept(), executor, supportsTerminateThreads, threads);
                        all.add(session);
                        sessions.add(session);
                    }
                } catch (IOException e) {
                    // closed by the test
                }
            });
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        /**
         * Waits until the client has closed the connection of each session, as it does once the debug session has
         * stopped, then closes the server and its connections, and stops its threads.
         */
        void close() throws Exception {
            try {
                for (var session : all) {
                    // A request which a test left unanswered doesn't keep the client from disconnecting
                    session.answerTerminateThreads();
                }
                PlatformTestUtil.waitWithEventsDispatching("the client closes the connection of each session",
                        () -> all.stream().allMatch(session -> session.listening.isDone()), BOUND_SECONDS);
            } finally {
                serverSocket.close();
                // A connection accepted just before is registered by the loop: let the loop end before closing them
                accepting.get(BOUND_SECONDS, TimeUnit.SECONDS);
                for (var session : all) {
                    session.socket.close();
                }
                executor.shutdownNow();
                assertTrue("the threads of the DAP server stop", executor.awaitTermination(BOUND_SECONDS, TimeUnit.SECONDS));
            }
        }
    }

    /**
     * A DAP session of a {@link SessionServer}, over its own connection. It answers 'terminateThreads' when the test
     * tells it to, and then reports that the threads exited.
     */
    private static class Session implements IDebugProtocolServer {

        private static final int MAIN = 1;
        private static final int WORKER = 2;
        private static final List<String> ROOT_THREADS = List.of("main", "worker");
        private static final List<String> CHILD_THREADS = List.of("page", "worker", "service worker");

        private final Socket socket;
        private final IDebugProtocolClient client;
        private final boolean supportsTerminateThreads;
        private final List<String> threads;
        private final RemoteEndpoint remoteEndpoint;
        // Done once the client has closed the connection
        private final Future<Void> listening;
        private final CompletableFuture<Void> configured = new CompletableFuture<>();
        private final LinkedBlockingQueue<TerminateThreadsArguments> terminateThreads = new LinkedBlockingQueue<>();
        private final CompletableFuture<Void> terminateAnswer = new CompletableFuture<>();

        Session(@NotNull Socket socket,
                @NotNull ExecutorService executor,
                boolean supportsTerminateThreads,
                @NotNull List<String> threads) throws IOException {
            this.socket = socket;
            this.supportsTerminateThreads = supportsTerminateThreads;
            this.threads = new CopyOnWriteArrayList<>(threads);
            var launcher = DSPLauncher.createServerLauncher(this, socket.getInputStream(), socket.getOutputStream(),
                    executor, consumer -> consumer);
            this.client = launcher.getRemoteProxy();
            this.remoteEndpoint = launcher.getRemoteEndpoint();
            this.listening = launcher.startListening();
        }

        /**
         * Asks the client to start a child session, which connects to the server on its own connection.
         */
        void startChildSession() {
            var args = new StartDebuggingRequestArguments();
            args.setConfiguration(Map.of());
            args.setRequest(StartDebuggingRequestArgumentsType.ATTACH);
            client.startDebugging(args);
        }

        /**
         * Sends that the given thread stopped at a breakpoint.
         */
        void stop(int threadId) {
            var stopped = new StoppedEventArguments();
            stopped.setReason(StoppedEventArgumentsReason.BREAKPOINT);
            stopped.setThreadId(threadId);
            client.stopped(stopped);
        }

        /**
         * Sends a 'thread' event with the given reason for the given thread.
         */
        void sendThreadEvent(@NotNull String reason, int threadId) {
            var event = new ThreadEventArguments();
            event.setReason(reason);
            event.setThreadId(threadId);
            client.thread(event);
        }

        /**
         * Starts a thread with the given name, which 'threads' reports from then on, and sends that it started.
         */
        void startThread(@NotNull String name) {
            threads.add(name);
            sendThreadEvent(ThreadEventArgumentsReason.STARTED, threads.size());
        }

        /**
         * Sends the client a request which it doesn't know, and returns the future of its answer, an error: the client
         * answers it once it has handled the messages which this session sent before.
         */
        CompletableFuture<Object> ping() {
            return remoteEndpoint.request("lsp4ij/test/ping", null);
        }

        /**
         * Answers the 'terminateThreads' requests, the one received and those to come.
         */
        void answerTerminateThreads() {
            terminateAnswer.complete(null);
        }

        @Override
        public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
            var capabilities = new Capabilities();
            capabilities.setSupportsConfigurationDoneRequest(true);
            if (supportsTerminateThreads) {
                capabilities.setSupportsTerminateThreadsRequest(true);
            }
            return CompletableFuture.completedFuture(capabilities);
        }

        @Override
        public CompletableFuture<Void> attach(Map<String, Object> args) {
            return CompletableFuture.completedFuture(null).thenRun(client::initialized);
        }

        @Override
        public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> configured.complete(null));
        }

        @Override
        public CompletableFuture<ThreadsResponse> threads() {
            var response = new ThreadsResponse();
            response.setThreads(IntStream.range(0, threads.size())
                    .mapToObj(index -> thread(index + 1, threads.get(index)))
                    .toArray(Thread[]::new));
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> terminateThreads(TerminateThreadsArguments args) {
            terminateThreads.add(args);
            return terminateAnswer.thenRun(() -> {
                // As a DAP server does once the threads ended
                for (int threadId : args.getThreadIds()) {
                    var exited = new ThreadEventArguments();
                    exited.setReason(ThreadEventArgumentsReason.EXITED);
                    exited.setThreadId(threadId);
                    client.thread(exited);
                }
            });
        }

        @Override
        public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
            var frame = new StackFrame();
            frame.setId(frameId(args.getThreadId()));
            frame.setName("run");
            frame.setLine(5);
            var response = new StackTraceResponse();
            response.setStackFrames(new StackFrame[]{frame});
            response.setTotalFrames(1);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
            var response = new ScopesResponse();
            response.setScopes(new Scope[0]);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> disconnect(DisconnectArguments args) {
            return CompletableFuture.completedFuture(null);
        }

        /**
         * Returns the ID of the only frame of the given thread, which 'stackTrace' answers.
         */
        static int frameId(int threadId) {
            return 10 + threadId;
        }

        private static Thread thread(int id, String name) {
            var thread = new Thread();
            thread.setId(id);
            thread.setName(name);
            return thread;
        }
    }
}

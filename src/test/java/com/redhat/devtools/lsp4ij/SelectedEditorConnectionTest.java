/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Selecting the editor tab of a file connects the file to its language servers, which then receive its didOpen.
 * Code vision is disabled, as it also asks for the servers of a file whose editor opens, so only the editor events
 * of the project that reach {@link ConnectDocumentToLanguageServerSetupParticipant} can connect the file.
 */
public class SelectedEditorConnectionTest extends LSPCodeInsightFixtureTestCase {

    // Where the stop of a language server waits for the notifications that its dispatcher holds, before the shutdown
    private static final String WAIT_FOR_THE_NOTIFICATIONS = "LanguageServerWrapper.waitForTheNotifications";

    private boolean codeVisionEnabled;

    // Completed, with its thread, as the stop of the language server checks its connection, which it does right before
    // it waits for the notifications that its dispatcher holds; null for nothing
    private volatile @Nullable CompletableFuture<Thread> stopChecksTheConnection;

    public SelectedEditorConnectionTest() {
        super("*.ts");
    }

    @Override
    protected @NotNull MockLanguageServerDefinition createServerDefinition(boolean clientConfigurable) {
        return new MockLanguageServerDefinition(clientConfigurable, true) {
            @Override
            public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
                return new MockConnectionProvider(getServer()) {
                    @Override
                    public boolean isAlive() {
                        var checked = stopChecksTheConnection;
                        if (checked != null) {
                            checked.complete(Thread.currentThread());
                        }
                        return super.isAlive();
                    }
                };
            }
        };
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        codeVisionEnabled = CodeVisionSettings.getInstance().getCodeVisionEnabled();
        CodeVisionSettings.getInstance().setCodeVisionEnabled(false);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            stopChecksTheConnection = null;
            CodeVisionSettings.getInstance().setCodeVisionEnabled(codeVisionEnabled);
        } finally {
            super.tearDown();
        }
    }

    public void testSelectingTheTabOfAFileConnectsIt() {
        var file = myFixture.addFileToProject("selected.ts", "const value=1;");
        var opened = expectDidOpen("selected.ts");

        FileEditorManager.getInstance(myFixture.getProject()).openFile(file.getVirtualFile(), true);

        PlatformTestUtil.waitWithEventsDispatching("selecting the tab of the file must send its didOpen",
                opened::isDone, 10);
    }

    public void testSelectingTheTabOfASecondFileConnectsItToTheRunningServer() {
        var first = myFixture.addFileToProject("first.ts", "const first=1;");
        var second = myFixture.addFileToProject("second.ts", "const second=1;");
        var editors = FileEditorManager.getInstance(myFixture.getProject());
        var firstOpened = expectDidOpen("first.ts");
        editors.openFile(first.getVirtualFile(), true);
        PlatformTestUtil.waitWithEventsDispatching("selecting the first tab must send its didOpen",
                firstOpened::isDone, 10);

        var secondOpened = expectDidOpen("second.ts");
        editors.openFile(second.getVirtualFile(), true);

        PlatformTestUtil.waitWithEventsDispatching("selecting the second tab must send its didOpen",
                secondOpened::isDone, 10);
    }

    public void testASelectionWhoseConnectionRunsAfterItsTabClosedLeavesTheFileDisconnected() throws Exception {
        var running = myFixture.addFileToProject("running.ts", "const running=1;");
        var closed = myFixture.addFileToProject("closed.ts", "const closed=1;");
        var later = myFixture.addFileToProject("later.ts", "const later=1;");
        var editors = FileEditorManager.getInstance(myFixture.getProject());
        // the server runs, so a connection of the closed file would reach it at once
        var runningOpened = expectDidOpen("running.ts");
        editors.openFile(running.getVirtualFile(), true);
        PlatformTestUtil.waitWithEventsDispatching("selecting the first tab must send its didOpen",
                runningOpened::isDone, 10);
        var closedOpened = expectDidOpen("closed.ts");
        editors.openFile(closed.getVirtualFile(), true);
        PlatformTestUtil.waitWithEventsDispatching("selecting the second tab must send its didOpen",
                closedOpened::isDone, 10);
        editors.closeFile(closed.getVirtualFile());
        // as the platform tells the listeners of the project, which closeFile does not do here
        projectEditorListeners().fileClosed(editors, closed.getVirtualFile());
        PlatformTestUtil.waitWithEventsDispatching("closing the second tab must send its didClose",
                () -> MockLanguageServer.INSTANCE.getTextDocumentService().getOpenCloseNotifications().stream()
                        .anyMatch(notification -> !notification.open()
                                && notification.uri().getPath().endsWith("/closed.ts")), 10);
        // the close releases the file's LSP support in a read action of its own
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

        // The selection of the closed tab, as a connection that runs after the close sees it, on a slow machine.
        // Its connection waits for indexing to end, and then checks the file in a read action. The test holds that
        // wait, so that the read action is submitted as the test ends it, and is waited for.
        IndexingTestUtil.waitUntilIndexesAreReady(myFixture.getProject());
        var reopened = expectDidOpen("closed.ts");
        var indexed = holdTheWaitsForIndexing();
        try {
            projectEditorListeners().selectionChanged(
                    new FileEditorManagerEvent(editors, null, null, null, closed.getVirtualFile(), null, null));
        } finally {
            indexed.complete(null);
        }
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

        // A connection of the closed file, which the read action would have started, would send its didOpen before a
        // connection of another file that starts after the read action, as the selection's does
        var connection = new DocumentConnectionScope();
        LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServersForScope(later, null, null, connection)
                .whenComplete((servers, error) -> connection.close());
        PlatformTestUtil.waitWithEventsDispatching("a later connection must send its didOpen",
                () -> MockLanguageServer.INSTANCE.getNotifications().contains("didOpen:later.ts"), 30);
        assertFalse("the closed file must stay disconnected", reopened.isDone());
    }

    public void testATabThatClosesWhileItsSelectionWaitsForTheServerLeavesTheFileDisconnected() throws Exception {
        // The selection's connection waits for the server's initialize, after the check that its tab is open
        var initialize = MockLanguageServer.INSTANCE.holdInitialize();
        var waiting = myFixture.addFileToProject("waiting.ts", "const waiting=1;").getVirtualFile();
        var editors = FileEditorManager.getInstance(myFixture.getProject());
        var opened = expectDidOpen("waiting.ts");
        try {
            editors.openFile(waiting, true);
            PlatformTestUtil.waitWithEventsDispatching("the selection must start the server",
                    () -> MockLanguageServer.INSTANCE.getNotifications().contains("initialize"), 10);
            editors.closeFile(waiting);
            projectEditorListeners().fileClosed(editors, waiting);
        } finally {
            initialize.complete(null);
        }
        PlatformTestUtil.waitWithEventsDispatching("the server must be initialized",
                () -> MockLanguageServer.INSTANCE.getNotifications().contains("initialized"), 10);

        // The connection is made now, and its release, on the EDT, disconnects the file. Both are waited for, rather
        // than a time, which a busy machine can exceed between the didOpen and the didClose that the server reads.
        // The file is the server's last, so the release also stops the server, whose shutdown follows the didClose.
        PlatformTestUtil.waitWithEventsDispatching("the selection's connection must be made", opened::isDone, 10);
        PlatformTestUtil.waitWithEventsDispatching(() -> "the closed file must end disconnected; the server read, of "
                + "waiting.ts, " + openCloseNotificationsOf("waiting.ts"), () -> !isConnected("waiting.ts"), 10);
    }

    public void testTheServerReadsTheDidCloseOfItsLastFileBeforeItStops() throws Exception {
        var last = myFixture.addFileToProject("last.ts", "const last=1;").getVirtualFile();
        var editors = FileEditorManager.getInstance(myFixture.getProject());
        var opened = expectDidOpen("last.ts");
        editors.openFile(last, true);
        PlatformTestUtil.waitWithEventsDispatching("selecting the tab must send its didOpen", opened::isDone, 10);
        var wrapper = ContainerUtil.getOnlyItem(LanguageServiceAccessor.getInstance(myFixture.getProject()).getStartedServers());
        // Once freed, the dispatcher has as long as a slow runner takes to hand over the didClose, instead of what is
        // left of the second for a user
        wrapper.setStopWaitTimeout(Duration.ofSeconds(IN_MEMORY_TIMEOUT_SECONDS));

        // The dispatcher is still busy, as on a slow machine, when the close of the last file queues its didClose and
        // stops the server at once, as in tests. The stop checks the connection of the server, and then waits for the
        // notifications that the dispatcher holds, 10 s at most here, before the shutdown. The dispatcher is freed by
        // the stop itself, once its thread waits there, whenever the test thread runs: the stop gets the didClose
        // then. A stop that does not wait there never frees it, and sends the shutdown first, after which no didClose
        // is sent.
        var stopChecked = new CompletableFuture<Thread>();
        stopChecksTheConnection = stopChecked;
        var stopWaited = new CompletableFuture<Void>();
        var released = new CompletableFuture<Void>();
        wrapper.sendNotification(server -> {
            holdTheDispatcherUntilTheStopWaits(stopChecked, stopWaited, released);
            return server;
        });
        try {
            editors.closeFile(last);
            projectEditorListeners().fileClosed(editors, last);
            PlatformTestUtil.waitWithEventsDispatching("the server must stop",
                    () -> wrapper.getServerStatus() == ServerStatus.stopped, 10);
        } finally {
            released.complete(null);
        }

        assertTrue("the stop must check the connection of the server before it waits for the notifications",
                stopChecked.isDone());
        assertTrue("the stop must wait in " + WAIT_FOR_THE_NOTIFICATIONS + " for the notifications that the "
                + "dispatcher holds, within 10 s; the server read, of last.ts, " + openCloseNotificationsOf("last.ts"),
                stopWaited.isDone());
        assertEquals("the server must read the didOpen of its last file, and then its didClose, before it stops",
                List.of("didOpen", "didClose"), openCloseNotificationsOf("last.ts"));
    }

    public void testAFeatureThatAsksWhileItsEditorIsOpenDoesNotConnectTheFileAfterTheEditorCloses() throws Exception {
        assertAFeatureConnectionAfterTheCloseLeavesTheFileDisconnected("open.ts", false);
    }

    public void testAFeatureThatAsksAsItsEditorClosesDoesNotConnectTheFileAfterTheClose() throws Exception {
        // The editor is closed, and the close not yet told, as when the feature asks while the editor closes
        assertAFeatureConnectionAfterTheCloseLeavesTheFileDisconnected("closing.ts", true);
    }

    /**
     * A feature of the file's editor asks for the file's servers, as code vision does when the editor opens, and the
     * connection runs after the close of the editor, which disconnected the file: it must not connect it again.
     */
    private void assertAFeatureConnectionAfterTheCloseLeavesTheFileDisconnected(String fileName,
                                                                                boolean askAsTheEditorCloses)
            throws Exception {
        var project = myFixture.getProject();
        var psiFile = myFixture.addFileToProject(fileName, "const value=1;");
        var file = psiFile.getVirtualFile();
        var editors = FileEditorManager.getInstance(project);
        var opened = expectDidOpen(fileName);
        editors.openFile(file, true);
        PlatformTestUtil.waitWithEventsDispatching("selecting the tab must send its didOpen", opened::isDone, 10);
        var wrapper = ContainerUtil.getOnlyItem(LanguageServiceAccessor.getInstance(project).getStartedServers());
        // The server stops, which disconnects the file, so that the next connection of the file starts it again and
        // waits for its initialize. No selection follows, so only the feature connects the file.
        var stopped = wrapper.stop();
        PlatformTestUtil.waitWithEventsDispatching("the server must stop", stopped::isDone, 10);
        var initialize = MockLanguageServer.INSTANCE.holdInitialize();
        CompletableFuture<List<LanguageServerItem>> servers;
        try {
            if (askAsTheEditorCloses) {
                editors.closeFile(file);
            }
            servers = ReadAction.compute(() -> LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(psiFile, null, null));
            if (!askAsTheEditorCloses) {
                editors.closeFile(file);
            }
            projectEditorListeners().fileClosed(editors, file);
        } finally {
            initialize.complete(null);
        }

        // The EDT is held until the connection has run, so nothing can disconnect the file again before the check
        servers.get(10, TimeUnit.SECONDS);
        assertNull("the file whose editor closed must stay disconnected", wrapper.getOpenedDocument(wrapper.toUri(file)));
    }

    private FileEditorManagerListener projectEditorListeners() {
        return myFixture.getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER);
    }

    /**
     * Holds what waits for indexing to end, as the connection of a selected tab does, until the returned future is
     * completed, which then runs it. The caller must complete the future, also when the test fails.
     */
    private static CompletableFuture<Void> holdTheWaitsForIndexing() throws ReflectiveOperationException {
        // ProjectIndexingManager.waitForIndexingAll() gives the future of the current wait until it is done
        var current = ProjectIndexingManager.class.getDeclaredField("waitForIndexingAllFuture");
        current.setAccessible(true);
        var held = new CompletableFuture<Void>();
        current.set(null, held);
        return held;
    }

    /**
     * Holds the calling thread, the dispatcher of the language server, until the thread which stops the language server
     * has checked its connection and then waits in {@link #WAIT_FOR_THE_NOTIFICATIONS}, for the notifications that the
     * dispatcher holds, before the shutdown. The hold ends without that once the given release is done, once the
     * thread of the stop has ended, or after 10 s.
     *
     * @param stopChecksTheConnection completed with the thread of the stop as it checks the connection.
     * @param stopWaited              completed as the hold sees the thread of the stop wait for the notifications.
     * @param release                 completed by the test as it ends.
     */
    private static void holdTheDispatcherUntilTheStopWaits(CompletableFuture<Thread> stopChecksTheConnection,
                                                           CompletableFuture<Void> stopWaited,
                                                           CompletableFuture<Void> release) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        try {
            CompletableFuture.anyOf(stopChecksTheConnection, release).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (ExecutionException | TimeoutException e) {
            // no stop checked the connection in time, which the test reports
            return;
        }
        var stop = stopChecksTheConnection.getNow(null);
        // A thread tells no other that it starts to wait, so the thread of the stop is looked at, for the moment between
        // the check and the wait that follows it. Its state says that it waits, but not for what: it could wait for a
        // lock, a class or a log on the way, and a didClose sent then could come before the shutdown of a stop that
        // doesn't wait for it. So the wait is recognized by the stack of that thread, which shows at once that it parks,
        // at its top, and that it parks in the wait for the notifications, below.
        while (stop != null && !release.isDone() && System.nanoTime() - deadline < 0) {
            var state = stop.getState();
            if (state == Thread.State.TERMINATED) {
                return;
            }
            if ((state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING)
                    && parksInTheWaitForTheNotifications(stop.getStackTrace())) {
                stopWaited.complete(null);
                return;
            }
            Thread.onSpinWait();
        }
    }

    /**
     * True if the given stack of a thread parks it, at its top, in {@link #WAIT_FOR_THE_NOTIFICATIONS}, below.
     */
    private static boolean parksInTheWaitForTheNotifications(StackTraceElement[] stack) {
        return stack.length > 0
                && stack[0].getClassName().equals("jdk.internal.misc.Unsafe")
                && stack[0].getMethodName().equals("park")
                && Arrays.stream(stack).anyMatch(frame ->
                        frame.getClassName().equals(LanguageServerWrapper.class.getName())
                                && frame.getMethodName().equals("waitForTheNotifications"));
    }

    /** True if the last didOpen or didClose of the file that the server read is a didOpen. */
    private static boolean isConnected(String fileName) {
        boolean connected = false;
        for (var notification : MockLanguageServer.INSTANCE.getTextDocumentService().getOpenCloseNotifications()) {
            if (notification.uri().getPath().endsWith("/" + fileName)) {
                connected = notification.open();
            }
        }
        return connected;
    }

    /** The didOpen and didClose notifications of the file that the server read, in the order it read them. */
    private static List<String> openCloseNotificationsOf(String fileName) {
        return MockLanguageServer.INSTANCE.getTextDocumentService().getOpenCloseNotifications().stream()
                .filter(notification -> notification.uri().getPath().endsWith("/" + fileName))
                .map(notification -> notification.open() ? "didOpen" : "didClose")
                .toList();
    }

    private static CompletableFuture<DidOpenTextDocumentParams> expectDidOpen(String fileName) {
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened,
                params -> params.getTextDocument().getUri().endsWith("/" + fileName));
        return opened;
    }
}

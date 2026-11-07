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
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Selecting the editor tab of a file connects the file to its language servers, which then receive its didOpen.
 * Code vision is disabled, as it also asks for the servers of a file whose editor opens, so only the editor events
 * of the project that reach {@link ConnectDocumentToLanguageServerSetupParticipant} can connect the file.
 */
public class SelectedEditorConnectionTest extends LSPCodeInsightFixtureTestCase {

    private boolean codeVisionEnabled;

    public SelectedEditorConnectionTest() {
        super("*.ts");
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
        // Its read action waits for indexing, which must not outlast the time checked below.
        IndexingTestUtil.waitUntilIndexesAreReady(myFixture.getProject());
        var reopened = expectDidOpen("closed.ts");
        projectEditorListeners().selectionChanged(
                new FileEditorManagerEvent(editors, null, null, null, closed.getVirtualFile(), null, null));
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

        // a connection would send the didOpen within the time the others take
        dispatchEventsFor(2);
        assertFalse("the closed file must stay disconnected", reopened.isDone());
    }

    public void testATabThatClosesWhileItsSelectionWaitsForTheServerLeavesTheFileDisconnected() throws Exception {
        // The selection's connection waits for the server's initialize, after the check that its tab is open
        var initialize = MockLanguageServer.INSTANCE.holdInitialize();
        var waiting = myFixture.addFileToProject("waiting.ts", "const waiting=1;").getVirtualFile();
        var editors = FileEditorManager.getInstance(myFixture.getProject());
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

        // the connection is made now, and its release, on the EDT, disconnects the file
        dispatchEventsFor(2);
        assertFalse("the closed file must end disconnected", isConnected("waiting.ts"));
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

    private static void dispatchEventsFor(int seconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            Thread.sleep(20);
        }
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

    private static CompletableFuture<DidOpenTextDocumentParams> expectDidOpen(String fileName) {
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened,
                params -> params.getTextDocument().getUri().endsWith("/" + fileName));
        return opened;
    }
}

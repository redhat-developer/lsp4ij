/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;

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
        var bus = myFixture.getProject().getMessageBus()
                .syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER);
        bus.fileClosed(editors, closed.getVirtualFile());
        PlatformTestUtil.waitWithEventsDispatching("closing the second tab must send its didClose",
                () -> MockLanguageServer.INSTANCE.getTextDocumentService().getOpenCloseNotifications().stream()
                        .anyMatch(notification -> !notification.open()
                                && notification.uri().getPath().endsWith("/closed.ts")), 10);
        // the close releases the file's LSP support in a read action of its own
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

        // The selection of the closed tab, as a connection that runs after the close sees it, on a slow machine
        var reopened = expectDidOpen("closed.ts");
        bus.selectionChanged(
                new FileEditorManagerEvent(editors, null, null, null, closed.getVirtualFile(), null, null));
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

        // A connection would send the didOpen within the time the others take, while the editor's events dispatch
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            assertFalse("the closed file must stay disconnected", reopened.isDone());
            Thread.sleep(20);
        }
    }

    private static CompletableFuture<DidOpenTextDocumentParams> expectDidOpen(String fileName) {
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened,
                params -> params.getTextDocument().getUri().endsWith("/" + fileName));
        return opened;
    }
}

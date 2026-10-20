/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A save sends textDocument/willSave and textDocument/willSaveWaitUntil to a server that asks for them, before the
 * document is saved, and saves the edits that willSaveWaitUntil returns in time with the document.
 */
public class WillSaveTest extends LSPCodeInsightFixtureTestCase {

    public WillSaveTest() {
        super("*.foo");
    }

    public void testWillSaveIsSentBeforeTheSave() throws Exception {
        serverAsks(true, false);
        open("text");
        type("more ");

        save();

        PlatformTestUtil.waitWithEventsDispatching("the server must be told of the save",
                () -> received().contains("didSave"), 10);
        assertEquals(List.of("didChange", "willSave:Manual", "didSave"), received());
    }

    public void testTheEditsOfWillSaveWaitUntilAreSavedWithTheDocument() throws Exception {
        serverAsks(false, true);
        MockLanguageServer.INSTANCE.getTextDocumentService().setWillSaveWaitUntilCallback(
                List.of(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "formatted ")));
        var file = open("text");
        type("more ");

        save();

        assertEquals("formatted more text", VfsUtilCore.loadText(file.getVirtualFile()));
        PlatformTestUtil.waitWithEventsDispatching("the server must be told of the save",
                () -> received().contains("didSave"), 10);
        // the server has the typed text when it is asked, and learns of its own edits before the save
        assertEquals(List.of("didChange", "willSaveWaitUntil", "didChange", "didSave"), received());
    }

    public void testEditsThatComeTooLateAreDroppedAndTheRequestStopsAfterThreeTimeouts() throws Exception {
        serverAsks(false, true);
        MockLanguageServer.INSTANCE.getTextDocumentService().setWillSaveWaitUntilCallback(
                List.of(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "late ")));
        MockLanguageServer.INSTANCE.getTextDocumentService().setWillSaveWaitUntilDelayMs(5000);
        var file = open("text");

        for (int save = 1; save <= 4; save++) {
            type(" " + save);
            long start = System.nanoTime();
            save();
            assertTrue("the save must not wait much longer than the willSaveWaitUntil budget",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 4000);
            // ends the UI event, as between two saves by the user
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            assertFalse("edits that come too late must be dropped", VfsUtilCore.loadText(file.getVirtualFile()).startsWith("late"));
        }
        PlatformTestUtil.waitWithEventsDispatching("the server must be told of the saves",
                () -> received().stream().filter("didSave"::equals).count() == 4, 10);
        assertEquals("after three timeouts, willSaveWaitUntil is no longer sent for the document",
                3, received().stream().filter("willSaveWaitUntil"::equals).count());
    }

    public void testTheDocumentsSavedTogetherShareTheBudget() throws Exception {
        serverAsks(true, true);
        MockLanguageServer.INSTANCE.getTextDocumentService().setWillSaveWaitUntilDelayMs(5000);
        open("text");
        var other = myFixture.addFileToProject("other.foo", "other");
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(other, null, null).get(30, TimeUnit.SECONDS);
        MockLanguageServer.INSTANCE.getNotifications().clear();
        var otherDocument = FileDocumentManager.getInstance().getDocument(other.getVirtualFile());
        type("more ");
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> otherDocument.insertString(0, "more "));

        long start = System.nanoTime();
        FileDocumentManager.getInstance().saveAllDocuments();

        assertTrue("Save All must wait once for a slow server, not once per document",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2500);
        PlatformTestUtil.waitWithEventsDispatching("the server must be told of both saves",
                () -> received().stream().filter("didSave"::equals).count() == 2, 10);
        assertEquals(2, received().stream().filter("willSave:Manual"::equals).count());
        assertEquals("the second document finds the budget used up", 1,
                received().stream().filter("willSaveWaitUntil"::equals).count());
    }

    public void testAServerThatDoesNotAskGetsNeither() throws Exception {
        serverAsks(false, false);
        open("text");
        type("more ");

        save();

        PlatformTestUtil.waitWithEventsDispatching("the server must be told of the save",
                () -> received().contains("didSave"), 10);
        assertFalse(received().contains("willSave:Manual"));
        assertFalse(received().contains("willSaveWaitUntil"));
    }

    private static void serverAsks(boolean willSave, boolean willSaveWaitUntil) {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            var sync = new TextDocumentSyncOptions();
            sync.setOpenClose(true);
            sync.setChange(TextDocumentSyncKind.Full);
            sync.setSave(Either.forRight(new SaveOptions(false)));
            sync.setWillSave(willSave);
            sync.setWillSaveWaitUntil(willSaveWaitUntil);
            capabilities.setTextDocumentSync(sync);
            return capabilities;
        });
    }

    private PsiFile open(String text) throws Exception {
        var file = myFixture.configureByText("main.foo", text);
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
        // only what happens from here on
        MockLanguageServer.INSTANCE.getNotifications().clear();
        return file;
    }

    private void type(String text) {
        var document = myFixture.getEditor().getDocument();
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> document.insertString(0, text));
    }

    private void save() {
        FileDocumentManager.getInstance().saveDocument(myFixture.getEditor().getDocument());
    }

    private static List<String> received() {
        var wanted = List.of("didChange", "willSave:Manual", "willSaveWaitUntil", "didSave");
        return MockLanguageServer.INSTANCE.getNotifications().stream().filter(wanted::contains).toList();
    }
}

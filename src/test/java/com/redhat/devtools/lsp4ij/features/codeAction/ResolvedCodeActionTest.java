/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeActionFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A code action without an edit is resolved when it is invoked, and its edit arrives after the
 * intention's command has ended. It is applied in a command of its own, which Undo reverts, and only to
 * the text it was resolved for.
 */
public class ResolvedCodeActionTest extends LSPCodeActionFixtureTestCase {

    private static final String TITLE = "Insert a greeting";

    public ResolvedCodeActionTest() {
        super("*.greeting");
    }

    public void testUndoRevertsTheResolvedEditAlone() {
        var intention = greetingIntention();
        resolveTo("hello ");
        type("!");

        invoke(intention);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertEquals("hello world!", document().getText());

        // the typing before the code action is not undone with it
        myFixture.performEditorAction(IdeActions.ACTION_UNDO);
        assertEquals("world!", document().getText());
    }

    public void testAResolvedEditIsNotAppliedToTextThatChangedMeanwhile() {
        var intention = greetingIntention();
        resolveTo("hello ");

        invoke(intention);
        // typed after the code action was resolved, before its edit is applied
        type("!");
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals("world!", document().getText());
    }

    public void testAResolvedEditAppliesToAFileOpenedMeanwhileWhoseTextIsUnchanged() throws Exception {
        var intention = greetingIntention();
        var other = myFixture.addFileToProject("other.greeting", "world").getVirtualFile();
        MockLanguageServer.INSTANCE.setResolvedCodeAction(resolved(other, "hello "));
        assertFalse("the language server must not have the other file open yet", isOpenInTheServer(other));

        invoke(intention);
        // opened in an editor after the code action was resolved, before its edit is applied
        openInEditor(other);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals("hello world", LSPIJUtils.getDocument(other).getText());
    }

    public void testAResolvedEditIsNotAppliedToAFileOpenedAndChangedMeanwhile() throws Exception {
        var intention = greetingIntention();
        var other = myFixture.addFileToProject("other.greeting", "world").getVirtualFile();
        MockLanguageServer.INSTANCE.setResolvedCodeAction(resolved(other, "hello "));

        invoke(intention);
        openInEditor(other);
        // typed in the opened file before the edit is applied
        var document = LSPIJUtils.getDocument(other);
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), "Typing", null,
                () -> document.insertString(document.getTextLength(), "!"));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals("world!", document.getText());
    }

    public void testACodeActionWithACommandIsResolvedForItsEditWhichIsAppliedBeforeTheCommand() throws Exception {
        var actions = assertCodeActions("test.greeting", IntentionActionKind.INTENTION_ONLY, "world<caret>",
                // language=JSON
                """
                        [
                          {
                            "title": "%s",
                            "kind": "refactor",
                            "data": { "id": 2 },
                            "command": { "title": "After the greeting", "command": "%s" }
                          }
                        ]""".formatted(TITLE, MockLanguageServer.SUPPORTED_COMMAND_ID),
                TITLE);
        var intention = assertFindIntentionByText(TITLE, actions);
        var resolved = resolved("hello ");
        resolved.setCommand(new Command("After the greeting", MockLanguageServer.SUPPORTED_COMMAND_ID));
        MockLanguageServer.INSTANCE.setResolvedCodeAction(resolved);

        invoke(intention);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        // the edit which the language server computes lazily is applied, and then its command is executed
        assertEquals("hello world", document().getText());
        var executed = MockLanguageServer.INSTANCE.getWorkspaceService().getExecutedCommand()
                .get(10, TimeUnit.SECONDS);
        assertEquals(MockLanguageServer.SUPPORTED_COMMAND_ID, executed.getCommand());
    }

    private IntentionAction greetingIntention() {
        var actions = assertCodeActions("test.greeting", IntentionActionKind.INTENTION_ONLY, "world<caret>",
                // language=JSON
                """
                        [
                          {
                            "title": "%s",
                            "kind": "refactor",
                            "data": { "id": 1 }
                          }
                        ]""".formatted(TITLE),
                TITLE);
        return assertFindIntentionByText(TITLE, actions);
    }

    /** Invokes the intention as IntelliJ does for one that starts in a write action, in a command. */
    private void invoke(IntentionAction intention) {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), TITLE, null,
                () -> intention.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
    }

    /**
     * Opens the file in an editor, as a user does, and waits until its language server has received its didOpen.
     * The editor connects the file asynchronously, so its servers are asked for here too, which connects it if the
     * editor hasn't yet. Neither wait dispatches events, which would apply the resolved edit first.
     */
    private void openInEditor(VirtualFile file) throws Exception {
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened,
                params -> params.getTextDocument().getUri().endsWith("/" + file.getName()));
        FileEditorManager.getInstance(myFixture.getProject()).openFile(file, true);
        var psiFile = PsiManager.getInstance(myFixture.getProject()).findFile(file);
        try {
            LanguageServiceAccessor.getInstance(myFixture.getProject())
                    .getLanguageServers(psiFile, null, null).get(30, TimeUnit.SECONDS);
            opened.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("the language server didn't receive the didOpen of " + file.getName(), e);
        }
        assertTrue(isOpenInTheServer(file));
    }

    private boolean isOpenInTheServer(VirtualFile file) {
        return MockLanguageServer.INSTANCE.getTextDocumentService().getOpenCloseNotifications().stream()
                .anyMatch(notification -> notification.open()
                        && notification.uri().getPath().endsWith("/" + file.getName()));
    }

    private void type(String text) {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), "Typing", null,
                () -> document().insertString(document().getTextLength(), text));
    }

    private void resolveTo(String insertion) {
        MockLanguageServer.INSTANCE.setResolvedCodeAction(resolved(insertion));
    }

    /** Returns the resolved code action, whose edit inserts the given text at the start of the file. */
    private CodeAction resolved(String insertion) {
        return resolved(myFixture.getFile().getVirtualFile(), insertion);
    }

    /** Returns the resolved code action, whose edit inserts the given text at the start of the given file. */
    private static CodeAction resolved(VirtualFile file, String insertion) {
        var resolved = new CodeAction(TITLE);
        resolved.setEdit(new WorkspaceEdit(Map.of(LSPIJUtils.toUriAsString(file),
                List.of(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), insertion)))));
        return resolved;
    }

    private Document document() {
        return myFixture.getEditor().getDocument();
    }
}

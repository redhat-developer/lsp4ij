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
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeActionFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import java.util.List;
import java.util.Map;

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

    private void type(String text) {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), "Typing", null,
                () -> document().insertString(document().getTextLength(), text));
    }

    private void resolveTo(String insertion) {
        var resolved = new CodeAction(TITLE);
        resolved.setEdit(new WorkspaceEdit(Map.of(LSPIJUtils.toUriAsString(myFixture.getFile()),
                List.of(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), insertion)))));
        MockLanguageServer.INSTANCE.setResolvedCodeAction(resolved);
    }

    private Document document() {
        return myFixture.getEditor().getDocument();
    }
}

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeActionFixtureTestCase;
import com.redhat.devtools.lsp4ij.internal.capabilities.ClientCapabilitiesFactory;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SnippetTextEdit;
import org.eclipse.lsp4j.StringValue;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A code action whose edit has a snippet edit (SnippetTextEdit, LSP 3.18) inserts the text of the snippet, and starts
 * it as a live template in the selected editor, so that the user goes through its placeholders (#1909).
 */
public class SnippetTextEditCodeActionTest extends LSPCodeActionFixtureTestCase {

    private static final String TITLE = "Insert a greeting";

    public SnippetTextEditCodeActionTest() {
        super("*.greeting");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // A template stays active in a test, as for the user
        TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    }

    public void testLSP4IJSaysItSupportsSnippetEdits() {
        assertEquals(Boolean.TRUE, ClientCapabilitiesFactory.create(null).getWorkspace().getWorkspaceEdit().getSnippetEditSupport());
    }

    public void testASnippetEditStartsAsATemplateInTheSelectedEditor() {
        var intention = greetingIntention();
        resolveTo(myFixture.getFile(),
                Either.forRight(new SnippetTextEdit(range(0, 0), new StringValue("snippet", "hello ${1:name} "))));

        invoke(intention);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals("hello name world", document().getText());
        assertNotNull("the snippet must start as a template", TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
        // The first placeholder is selected, for the user to type over
        assertEquals("name", myFixture.getEditor().getSelectionModel().getSelectedText());
    }

    public void testATemplateStartsWhereTheSnippetIsOnceTheOtherEditsAreApplied() {
        var intention = greetingIntention();
        resolveTo(myFixture.getFile(),
                // Before the snippet, in the same document: the template starts after its text
                Either.forLeft(new TextEdit(range(0, 0), "// ")),
                Either.forRight(new SnippetTextEdit(range(0, 5), new StringValue("snippet", " and ${1:name}"))));

        invoke(intention);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals("// world and name", document().getText());
        assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
        assertEquals("name", myFixture.getEditor().getSelectionModel().getSelectedText());
        assertEquals("// world and ".length(), myFixture.getEditor().getSelectionModel().getSelectionStart());
    }

    public void testASnippetEditOfAFileWithoutTheSelectedEditorInsertsTheTextOfTheSnippet() {
        var other = myFixture.addFileToProject("other.txt", "world");
        var intention = greetingIntention();
        resolveTo(other, Either.forRight(new SnippetTextEdit(range(0, 0), new StringValue("snippet", "hello ${1:name} $0"))));

        invoke(intention);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        Document otherDocument = FileDocumentManager.getInstance().getDocument(other.getVirtualFile());
        assertNotNull(otherDocument);
        assertEquals("hello name world", otherDocument.getText());
        assertNull("no template starts in the selected editor of another file",
                TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
    }

    public void testATabStopRepeatsTheDefaultOfThePlaceholderOrTheChoiceWithItsIndex() {
        var other = myFixture.addFileToProject("other.txt", "world");
        var intention = greetingIntention();
        resolveTo(other, Either.forRight(new SnippetTextEdit(range(0, 0),
                // After its placeholder, and before its choice
                new StringValue("snippet", "${1:name} = $1, $2 ${2|one,two|} "))));

        invoke(intention);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        Document otherDocument = FileDocumentManager.getInstance().getDocument(other.getVirtualFile());
        assertNotNull(otherDocument);
        assertEquals("name = name, one one world", otherDocument.getText());
    }

    public void testATabStopInTheDefaultOfAPlaceholderIsRepeatedWithIt() {
        var other = myFixture.addFileToProject("other.txt", "world");
        var intention = greetingIntention();
        resolveTo(other, Either.forRight(new SnippetTextEdit(range(0, 0),
                // $1 repeats the whole default of placeholder 1, whose $2 repeats x; $3 repeats a later default
                new StringValue("snippet", "${1:${2:x}-$2}=$1 ${4:$3} ${3:y} "))));

        invoke(intention);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        Document otherDocument = FileDocumentManager.getInstance().getDocument(other.getVirtualFile());
        assertNotNull(otherDocument);
        assertEquals("x-x=x-x y y world", otherDocument.getText());
    }

    public void testAPlaceholderThatIsAVariableInsertsItsValue() {
        var other = myFixture.addFileToProject("other.txt", "world");
        var intention = greetingIntention();
        resolveTo(other, Either.forRight(new SnippetTextEdit(range(0, 0),
                new StringValue("snippet", "${1:$TM_FILENAME_BASE} = $1, ${2:${TM_FILENAME}} "))));

        invoke(intention);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        Document otherDocument = FileDocumentManager.getInstance().getDocument(other.getVirtualFile());
        assertNotNull(otherDocument);
        assertEquals("other = other, other.txt world", otherDocument.getText());
    }

    public void testAPlaceholderThatStartsWithAnEscapedDollarInsertsItsText() {
        var other = myFixture.addFileToProject("other.txt", "world");
        var intention = greetingIntention();
        resolveTo(other, Either.forRight(new SnippetTextEdit(range(0, 0),
                // The escaped $ starts a text, not a variable
                new StringValue("snippet", "${1:\\$TM_FILENAME_BASE} = $1 "))));

        invoke(intention);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        Document otherDocument = FileDocumentManager.getInstance().getDocument(other.getVirtualFile());
        assertNotNull(otherDocument);
        assertEquals("$TM_FILENAME_BASE = $TM_FILENAME_BASE world", otherDocument.getText());
    }

    public void testATabStopRepeatsTheTextOfANestedPlaceholderWithItsIndex() {
        var other = myFixture.addFileToProject("other.txt", "world");
        var intention = greetingIntention();
        resolveTo(other, Either.forRight(new SnippetTextEdit(range(0, 0),
                new StringValue("snippet", "${1:hello ${2:you}} = $1, $2 "))));

        invoke(intention);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        Document otherDocument = FileDocumentManager.getInstance().getDocument(other.getVirtualFile());
        assertNotNull(otherDocument);
        // The closing brace of the outer placeholder is no text (#2074)
        assertEquals("hello you = hello you, you world", otherDocument.getText());
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

    /**
     * Invokes the intention as IntelliJ does for one that starts in a write action, in a command.
     */
    private void invoke(IntentionAction intention) {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), TITLE, null,
                () -> intention.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
    }

    @SafeVarargs
    private static void resolveTo(@NotNull PsiFile file, Either<TextEdit, SnippetTextEdit>... edits) {
        var resolved = new CodeAction(TITLE);
        var documentEdit = new TextDocumentEdit(new VersionedTextDocumentIdentifier(LSPIJUtils.toUriAsString(file), null),
                List.of(edits));
        resolved.setEdit(new WorkspaceEdit(List.of(Either.forLeft(documentEdit))));
        MockLanguageServer.INSTANCE.setResolvedCodeAction(resolved);
    }

    private static Range range(int line, int character) {
        return new Range(new Position(line, character), new Position(line, character));
    }

    private Document document() {
        return myFixture.getEditor().getDocument();
    }
}

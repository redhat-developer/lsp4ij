/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.fixtures;

import com.google.gson.reflect.TypeToken;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Base class test case to test LSP 'textDocument/codeAction' feature.
 */
public abstract class LSPCodeActionFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    public LSPCodeActionFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    /**
     * Test LSP code actions.
     *
     * @param fileName          the file name used to match registered language servers.
     * @param editorContentText the editor content text.
     * @param codeActionsJson   the LSP CodeAction list as a JSON string.
     * @param expectedActions   the expected IntelliJ intention action text.
     */
    protected void assertCodeActions(@NotNull String fileName,
                                     @NotNull String editorContentText,
                                     @NotNull String codeActionsJson,
                                     @NotNull String... expectedActions) {
        List<CodeAction> codeActions = JSONUtils.getLsp4jGson()
                .fromJson(codeActionsJson, new TypeToken<List<CodeAction>>() {}.getType());

        assertCodeActions(fileName, editorContentText, codeActions, expectedActions);
    }

    /**
     * Test LSP code actions.
     *
     * @param fileName          the file name used to match registered language servers.
     * @param editorContentText the editor content text.
     * @param codeActions       the LSP CodeAction list.
     * @param expectedActions   the expected IntelliJ intention action text.
     */
    protected void assertCodeActions(@NotNull String fileName,
                                     @NotNull String editorContentText,
                                     @NotNull List<CodeAction> codeActions,
                                     @NotNull String... expectedActions) {
        List<Either<Command, CodeAction>> wrappedCodeActions = codeActions.stream()
                .distinct()
                .map(Either::<Command, CodeAction>forRight)
                .collect(Collectors.toList());

        myFixture.configureByText(fileName, editorContentText);

        final Editor editor = myFixture.getEditor();
        final PsiFile file = myFixture.getFile();
        int offset = editor.getCaretModel().getOffset();

        List<Diagnostic> diagnostics = codeActions.stream()
                .flatMap(codeAction -> Optional.ofNullable(codeAction.getDiagnostics()).stream().flatMap(List::stream))
                .distinct()
                .collect(Collectors.toList());

        MockLanguageServer.INSTANCE.setTimeToProceedQueries(200);
        MockLanguageServer.INSTANCE.setCodeActions(wrappedCodeActions);
        MockLanguageServer.INSTANCE.setDiagnostics(diagnostics);

        /*List<String> codeActionTitles = null;
        for (HighlightInfo info : myFixture.doHighlighting()) {
            HighlightInfo.IntentionActionDescriptor desc = info.findRegisteredQuickFix((descriptor, range) -> descriptor);
            final PsiElement someElement = file.findElementAt(offset);
            assertNotNull(someElement);
            for (IntentionAction option : desc.getOptions(someElement, editor)) {
                codeActionTitles.add(option.getText());
            }
        }
        for (String expectedAction : expectedActions) {
            assertContainsElements(codeActionTitles, expectedAction);
        }*/

        CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, new int[0], false);
        List<IntentionAction> actions = CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file);
        List<String> codeActionTitles = actions.stream()
                .map(IntentionAction::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        for (String expectedAction : expectedActions) {
            assertContainsElements(codeActionTitles, expectedAction);
        }
    }

    /**
     * Test applying a code action.
     *
     * @param actionText               the text of the action to be applied.
     * @param expectedEditorContentText the expected editor content text after applying the action.
     */
    public void assertApplyCodeAction(@NotNull String actionText,
                                      @NotNull String expectedEditorContentText) {
        String expected = expectedEditorContentText;
        int expectedCaretOffset = expectedEditorContentText.indexOf("<caret>");
        if (expectedCaretOffset != -1) {
            expected = expectedEditorContentText.substring(0, expectedCaretOffset) +
                    expectedEditorContentText.substring("<caret>".length() + expectedCaretOffset);
        }

        IntentionAction action = myFixture.getAvailableIntentions().stream()
                .filter(intentionAction -> intentionAction.getText().equals(actionText))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Action with text '" + actionText + "' not found."));

        myFixture.launchAction(action);

        var editor = myFixture.getEditor();
        assertEquals("After applying code action, editor content should be equal", expected, editor.getDocument().getText());

        if (expectedCaretOffset != -1) {
            assertEquals("After applying code action, caret offset should be equal", expectedCaretOffset, editor.getCaretModel().getOffset());
        }
    }
}

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
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        List<Diagnostic> diagnostics = codeActions.stream()
                .flatMap(codeAction -> Optional.ofNullable(codeAction.getDiagnostics()).stream().flatMap(List::stream))
                .distinct()
                .collect(Collectors.toList());

        MockLanguageServer.INSTANCE.setTimeToProceedQueries(200);
        MockLanguageServer.INSTANCE.setCodeActions(wrappedCodeActions);
        MockLanguageServer.INSTANCE.setDiagnostics(diagnostics);

        myFixture.configureByText(fileName, editorContentText);
        Collection<HighlightInfo> highlightInfos = myFixture.doHighlighting();

        List<IntentionAction> actions = highlightInfos.stream()
                .flatMap(info -> info.quickFixActionRanges != null
                        ? info.quickFixActionRanges.stream().map(pair -> pair.getFirst().getAction())
                        : Stream.empty())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<String> codeActionTitles = new ArrayList<>();
        for (IntentionAction action : actions) {
            if (action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile())) {
                codeActionTitles.add(action.getText());
            }
        }
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
    public void assertApplyCodeAction(@NotNull String actionText, @NotNull String expectedEditorContentText) {
        String expected = expectedEditorContentText;
        int expectedCaretOffset = expectedEditorContentText.indexOf("<caret>");
        if (expectedCaretOffset != -1) {
            expected = expectedEditorContentText.substring(0, expectedCaretOffset) +
                    expectedEditorContentText.substring("<caret>".length() + expectedCaretOffset);
        }

        IntentionAction action = myFixture.doHighlighting().stream()
                .flatMap(info -> {
                    if (info.quickFixActionRanges != null) {
                        return info.quickFixActionRanges.stream()
                                .map(pair -> pair.getFirst().getAction())
                                .filter(Objects::nonNull);
                    }
                    return Stream.empty();
                })
                .filter(intentionAction -> intentionAction.getText().equals(actionText))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Action with text '" + actionText + "' not found."));

        myFixture.launchAction(action);
        myFixture.checkResult(expected);

        if (expectedCaretOffset != -1) {
            assertEquals("After applying code action, caret offset should be equal", expectedCaretOffset, myFixture.getEditor().getCaretModel().getOffset());
        }
    }
}

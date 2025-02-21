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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Base class test case to test LSP 'textDocument/codeAction' feature.
 */
public abstract class LSPCodeActionFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    public enum IntentionActionKind {
        QUICK_FIX_ONLY,
        INTENTION_ONLY,
        QUICK_FIX_AND_INTENTION_BOTH
    }

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
     * @return
     */
    protected List<IntentionAction> assertCodeActions(@NotNull String fileName,
                                                      @NotNull IntentionActionKind kind,
                                                      @NotNull String editorContentText,
                                                      @NotNull String codeActionsJson,
                                                      @NotNull String... expectedActions) {
        List<CodeAction> codeActions = JSONUtils.getLsp4jGson()
                .fromJson(codeActionsJson, new TypeToken<List<CodeAction>>() {
                }.getType());

        return assertCodeActions(fileName, kind, editorContentText, codeActions, expectedActions);
    }

    /**
     * Test LSP code actions.
     *
     * @param fileName          the file name used to match registered language servers.
     * @param kind the intention action kind.
     * @param editorContentText the editor content text.
     * @param codeActions       the LSP CodeAction list.
     * @param expectedActions   the expected IntelliJ intention action text.
     */
    protected List<IntentionAction> assertCodeActions(@NotNull String fileName,
                                                      @NotNull IntentionActionKind kind,
                                                      @NotNull String editorContentText,
                                                      @NotNull List<CodeAction> codeActions,
                                                      @NotNull String... expectedActions) {
        List<Either<Command, CodeAction>> wrappedCodeActions = codeActions.stream()
                .distinct()
                .map(Either::<Command, CodeAction>forRight)
                .collect(Collectors.toList());

        List<Diagnostic> diagnostics = codeActions.stream()
                .flatMap(codeAction -> Optional.ofNullable(codeAction.getDiagnostics())
                        .stream()
                        .flatMap(List::stream))
                .distinct()
                .collect(Collectors.toList());

        MockLanguageServer.INSTANCE.setTimeToProceedQueries(200);
        MockLanguageServer.INSTANCE.setCodeActions(wrappedCodeActions);
        MockLanguageServer.INSTANCE.setDiagnostics(diagnostics);
        myFixture.configureByText(fileName, editorContentText);

        // Collect IntelliJ Quick fixes / Intention actions
        List<IntentionAction> actions = new ArrayList<>();
        switch (kind) {
            case QUICK_FIX_ONLY -> actions.addAll(getAllQuickFixes());
            case INTENTION_ONLY -> actions.addAll(myFixture.getAvailableIntentions());
            case QUICK_FIX_AND_INTENTION_BOTH -> {
                actions.addAll(getAllQuickFixes());
                actions.addAll(myFixture.getAvailableIntentions());
            }
        }

        if (expectedActions != null && expectedActions.length > 0) {
            assertNotEmpty(actions);
        }

        List<String> codeActionTitles = new ArrayList<>();
        for (IntentionAction action : actions) {
            if (action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile())) {
                codeActionTitles.add(action.getText());
            }
        }
        for (String expectedAction : expectedActions) {
            assertContainsElements(codeActionTitles, expectedAction);
        }
        return actions;
    }

    public void assertApplyCodeAction(@NotNull String expectedEditorContentText,
                                      @NotNull IntentionAction intentionActionToApply) {
        assertApplyCodeAction(expectedEditorContentText, null, intentionActionToApply);
    }

    public void assertApplyCodeAction(@NotNull String expectedEditorContentText,
                                      @Nullable String resolvedCodeActionJson,
                                      @NotNull IntentionAction intentionActionToApply) {

        if (resolvedCodeActionJson != null) {
            // Emulate resolve code action
            // Replace edit/document/uri with the file URI to update file correctly
            resolvedCodeActionJson = resolvedCodeActionJson
                    .formatted(LSPIJUtils.toUriAsString(myFixture.getFile()));
            // Load and update language server with the emulated resolved code action
            CodeAction resolvedCodeAction = JSONUtils.getLsp4jGson()
                    .fromJson(resolvedCodeActionJson, new TypeToken<CodeAction>() {
                    }.getType());
            MockLanguageServer.INSTANCE.setResolvedCodeAction(resolvedCodeAction);
        }

        // Apply the IntelliJ intention action
        myFixture.launchAction(intentionActionToApply);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

        ApplicationManager.getApplication().invokeAndWait(() -> {
                    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
                    // Check if the new content of PsiFile has been updated correctly with IntelliJ intention action.
                    myFixture.checkResult(expectedEditorContentText);
                });
    }


    @NotNull
    public static IntentionAction assertFindIntentionByText(@NotNull String text, @NotNull List<IntentionAction> actions) {
        var quickFixResult = CodeInsightTestUtil.findIntentionByText(actions, text);
        Assert.assertNotNull("Cannot find intention action fix with text '" + text + "'.", quickFixResult);
        return quickFixResult;
    }

    @NotNull
    private List<IntentionAction> getAllQuickFixes() {
        List<HighlightInfo> infos = myFixture.doHighlighting();
        List<IntentionAction> actions = new ArrayList<>();
        for (HighlightInfo info : infos) {
            info.findRegisteredQuickFix((descriptor, range) -> {
                actions.add(descriptor.getAction());
                return null;
            });
        }
        return actions;
    }

}
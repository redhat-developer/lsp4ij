/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.commands.CommandExecutor;
import com.redhat.devtools.lsp4ij.commands.LSPCommandContext;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The lazy IJ Quick fix / Intention.
 */
public class LSPLazyCodeActionIntentionAction implements IntentionAction {


    private LSPLazyCodeActionProvider lazyCodeActions;

    private final int index;
    private Either<CodeActionData, Boolean> action;
    private CodeAction codeAction;

    private String title;
    private Command command;
    private String familyName;

    public LSPLazyCodeActionIntentionAction(int index) {
        this.index = index;
        this.familyName = LanguageServerBundle.message("lsp.intention.code.action.kind.empty");
    }

    public void setLazyCodeActions(LSPLazyCodeActionProvider lazyCodeActions) {
        action = null;
        this.lazyCodeActions = lazyCodeActions;
    }

    @Override
    public @IntentionName @NotNull String getText() {
        loadCodeActionIfNeeded();
        return title != null? title : "";
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        loadCodeActionIfNeeded();
        return familyName != null ? familyName :  LanguageServerBundle.message("lsp.intention.code.action.kind.empty");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        loadCodeActionIfNeeded();
        return isValidCodeAction(this.action);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        var languageServer = getLanguageServer(this.action);
        if (languageServer == null) {
            return;
        }
        if (codeAction != null) {
            if (codeAction.getEdit() == null && codeAction.getCommand() == null
                    && languageServer.getClientFeatures().getCodeActionFeature().isResolveCodeActionSupported(file)) {
                // Unresolved code action "edit" property. Resolve it.
                languageServer
                        .getInitializedServer()
                        .thenApply(ls ->
                                ls.getTextDocumentService().resolveCodeAction(codeAction)
                                        .thenAccept(resolved -> {
                                            ApplicationManager.getApplication().invokeLater(() -> {
                                                DocumentUtil.writeInRunUndoTransparentAction(() -> {
                                                    apply(resolved != null ? resolved : codeAction, file, editor, languageServer);
                                                });
                                            });
                                        })
                        );
            } else {
                apply(codeAction, file, editor, languageServer);
            }
        } else if (command != null) {
            executeCommand(command, file, editor, languageServer);
        } else {
            // Should never get here
        }
    }

    private void apply(@Nullable CodeAction codeaction,
                       @NotNull PsiFile file,
                       @NotNull Editor editor,
                       @NotNull LanguageServerItem languageServerItem) {
        if (codeaction != null) {
            if (codeaction.getEdit() != null) {
                LSPIJUtils.applyWorkspaceEdit(codeaction.getEdit(), codeaction.getTitle());
            }
            if (codeaction.getCommand() != null) {
                executeCommand(codeaction.getCommand(), file, editor, languageServerItem);
            }
        }
    }

    private static void executeCommand(@NotNull Command command,
                                @NotNull PsiFile file,
                                @NotNull Editor editor,
                                @NotNull LanguageServerItem languageServer) {
        CommandExecutor.executeCommand(new LSPCommandContext(command, file, LSPCommandContext.ExecutedBy.CODE_ACTION, editor, languageServer));
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    private void loadCodeActionIfNeeded() {
        if (lazyCodeActions == null) {
            return;
        }
        if (action != null) {
            // The LSP code action has been already loaded.
            return;
        }
        // Try to get the LSP code action from the given index
        var currentAction = this.action = lazyCodeActions.getCodeActionAt(index);
        if (isValidCodeAction(currentAction)) {
            var codeActionFeature = getLanguageServer(currentAction).getClientFeatures().getCodeActionFeature();
            var action = currentAction.getLeft().codeAction();
            if (action.isRight()) {
                codeAction = action.getRight();
                title = codeActionFeature.getText(codeAction);
                if (title != null) {
                    familyName = codeActionFeature.getFamilyName(codeAction);
                }
            } else if (action.isLeft()) {
                command = action.getLeft();
                title = codeActionFeature.getText(command);
                if (title != null) {
                    familyName = codeActionFeature.getFamilyName(command);
                }
            }
            if (title == null) {
                // The LSP code action feature returns null, ignore the code action
                this.action = Either.forRight(Boolean.FALSE);
            }
        }
    }


    @Nullable
    private static LanguageServerItem getLanguageServer(@Nullable Either<CodeActionData, Boolean> action) {
        return action != null && action.isLeft() ? action.getLeft().languageServer() : null;
    }
    
    private static boolean isValidCodeAction(@Nullable Either<CodeActionData, Boolean> action) {
        return action != null && action.isLeft();
    }

}

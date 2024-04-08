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
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.commands.CommandExecutor;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

import static com.redhat.devtools.lsp4ij.LanguageServerItem.isCodeActionResolveSupported;

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
        return title;
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        loadCodeActionIfNeeded();
        return familyName;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        loadCodeActionIfNeeded();
        return isValidCodeAction();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        String serverId = getLanguageServerWrapper().getServerDefinition().getId();
        if (codeAction != null) {
            if (codeAction.getEdit() == null && codeAction.getCommand() == null
                    && isCodeActionResolveSupported(getLanguageServerWrapper().getServerCapabilities())) {
                // Unresolved code action "edit" property. Resolve it.
                getLanguageServerWrapper().getInitializedServer()
                        .thenApply(ls ->
                                ls.getTextDocumentService().resolveCodeAction(codeAction)
                                        .thenAccept(resolved -> {
                                            ApplicationManager.getApplication().invokeLater(() -> {
                                                DocumentUtil.writeInRunUndoTransparentAction(() -> {
                                                    apply(resolved != null ? resolved : codeAction, project, file, serverId);
                                                });
                                            });
                                        })
                        );
            } else {
                apply(codeAction, project, file, serverId);
            }
        } else if (command != null) {
            executeCommand(command, project, file, serverId);
        } else {
            // Should never get here
        }
    }

    private void apply(CodeAction codeaction, @NotNull Project project, PsiFile file, String serverId) {
        if (codeaction != null) {
            if (codeaction.getEdit() != null) {
                LSPIJUtils.applyWorkspaceEdit(codeaction.getEdit(), codeaction.getTitle());
            }
            if (codeaction.getCommand() != null) {
                executeCommand(codeaction.getCommand(), project, file, serverId);
            }
        }
    }

    private void executeCommand(Command command, @NotNull Project project, PsiFile file, String serverId) {
        CommandExecutor.executeCommand(command, LSPIJUtils.toUri(file), project, serverId);
    }

    private LanguageServerWrapper getLanguageServerWrapper() {
        return action.getLeft().languageServer();
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
        // Try to get the LSP code action from the given indes
        this.action = lazyCodeActions.getCodeActionAt(index);
        if (isValidCodeAction()) {
            var action = this.action.getLeft().codeAction();
            if (action.isRight()) {
                codeAction = action.getRight();
                title = action.getRight().getTitle();
                familyName = getFamilyName(codeAction);
            } else if (action.isLeft()) {
                command = action.getLeft();
                title = command.getTitle();
                familyName = "LSP Command";
            }
        }
    }

    @NotNull
    private static String getFamilyName(@NotNull CodeAction codeAction) {
        String kind = codeAction.getKind();
        if (StringUtils.isNotBlank(kind)) {
            switch (kind) {
                case CodeActionKind.QuickFix :
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.quickfix");
                case CodeActionKind.Refactor:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.refactor");
                case CodeActionKind.RefactorExtract:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.refactor.extract");
                case CodeActionKind.RefactorInline:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.refactor.inline");
                case CodeActionKind.RefactorRewrite:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.refactor.rewrite");
                case CodeActionKind.Source:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.source");
                case CodeActionKind.SourceFixAll:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.source.fixAll");
                case CodeActionKind.SourceOrganizeImports:
                    return LanguageServerBundle.message("lsp.intention.code.action.kind.source.organizeImports");
            }
        }
        return LanguageServerBundle.message("lsp.intention.code.action.kind.empty");
    }

    private boolean isValidCodeAction() {
        return action != null && action.isLeft();
    }

}

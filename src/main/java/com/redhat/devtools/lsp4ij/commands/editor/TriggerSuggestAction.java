/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.commands.editor;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.commands.LSPCommand;
import com.redhat.devtools.lsp4ij.commands.LSPCommandAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Emulates Visual Studio Code's "editor.action.triggerSuggest" command, to trigger code completion after selecting a completion item.
 */
public class TriggerSuggestAction extends LSPCommandAction {

    private static final String CODE_COMPLETION_ACTION = "CodeCompletion";

    @Override
    protected void commandPerformed(@NotNull LSPCommand command, @NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        @Nullable TextEditor activeEditor = findActiveEditor(project);
        if (activeEditor == null) {
            return;
        }
        AnAction codeCompletionAction = ActionManager.getInstance().getAction(CODE_COMPLETION_ACTION);
        if (codeCompletionAction != null ) {
            DataContext dataContext = createDataContext(activeEditor, project);
            ActionUtil.invokeAction(codeCompletionAction, dataContext, ActionPlaces.UNKNOWN, null, null);
        }
    }

    private static DataContext createDataContext(TextEditor activeEditor, Project project) {
        SimpleDataContext.Builder contextBuilder = SimpleDataContext.builder();
        contextBuilder.add(CommonDataKeys.PROJECT, project)
                      .add(CommonDataKeys.EDITOR, activeEditor.getEditor());
        return contextBuilder.build();
    }

    public static @Nullable TextEditor findActiveEditor(Project project) {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        FileEditor editor = fileEditorManager.getSelectedEditor();
        return editor instanceof TextEditor? (TextEditor) editor: null;
    }
}

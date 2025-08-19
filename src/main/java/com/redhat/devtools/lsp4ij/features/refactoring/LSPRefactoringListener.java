/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.refactoring;

import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP refactoring listener which tracks refactoring to send 'workspace/didRenameFiles' when
 * a rename file occurs if they were 'workspace.willRenameFiles' has been consumed.
 */
public class LSPRefactoringListener implements RefactoringEventListener {

    @Override
    public void refactoringStarted(@NotNull String refactoringId, @Nullable RefactoringEventData beforeData) {

    }

    @Override
    public void refactoringDone(@NotNull String refactoringId, @Nullable RefactoringEventData afterData) {
        LSPRenameFilesContext context = LSPRenameFilesContextHolder.get();

        if (context == null) {
            return;
        }

        context.renameFilesParams()
            .forEach((ls, params) -> ls.getWorkspaceService().didRenameFiles(params));

        LSPRenameFilesContextHolder.set(null);
    }

    @Override
    public void conflictsDetected(@NotNull String refactoringId, @NotNull RefactoringEventData conflictsData) {

    }

    @Override
    public void undoRefactoring(@NotNull String refactoringId) {

    }

}

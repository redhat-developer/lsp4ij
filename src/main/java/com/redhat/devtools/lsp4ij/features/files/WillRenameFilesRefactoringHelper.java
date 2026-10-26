/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.files;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringHelper;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Sends the {@code workspace/willRenameFiles} requests that a rename or move refactoring planned, and
 * waits for their responses under the progress that IntelliJ shows before the write action of the
 * refactoring (see {@link WillRenameFilesPreparation}).
 */
public class WillRenameFilesRefactoringHelper implements RefactoringHelper<Void> {

    @Override
    public Void prepareOperation(UsageInfo @NotNull [] usages, @NotNull List<@NotNull PsiElement> elements) {
        if (!elements.isEmpty()) {
            WillRenameFilesPreparation.getInstance(elements.get(0).getProject()).sendAndAwait(elements);
        }
        return null;
    }

    @Override
    public void performOperation(@NotNull Project project, Void operationData) {
    }
}

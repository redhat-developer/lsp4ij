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

    // A raw List on purpose, so that LSP4IJ compiles against every platform it supports. The platform declares
    // List<PsiElement> up to 2025.2 and List<? extends PsiElement> since 2025.3: overriding with either generic type
    // doesn't compile against the other platform ("name clash: ... have the same erasure, yet neither overrides the
    // other"). Both erase to List, though, and a method declared with the erased type overrides both. The elements are
    // only read, so reading them as a List<PsiElement> is safe with either. Use the generic type again once the oldest
    // supported platform is 2025.3.
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Void prepareOperation(UsageInfo @NotNull [] usages, @NotNull List elements) {
        List<PsiElement> psiElements = elements;
        if (!psiElements.isEmpty()) {
            WillRenameFilesPreparation.getInstance(psiElements.get(0).getProject()).sendAndAwait(psiElements);
        }
        return null;
    }

    @Override
    public void performOperation(@NotNull Project project, Void operationData) {
    }
}

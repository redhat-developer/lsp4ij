/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.files;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import com.redhat.devtools.lsp4ij.features.files.WillRenameFilesPreparation.FileRenamePlan;

import java.util.List;
import java.util.Map;

/**
 * Plans the {@code workspace/willRenameFiles} requests for the files and directories that a move
 * refactoring moves, which IntelliJ passes to every handler with the target directory when it
 * detects conflicts, before the write action (see {@link WillRenameFilesPreparation}).
 * <p>
 * This handler never moves anything itself: IntelliJ moves a file with the first handler that can
 * process it, and this one can process none.
 */
public class WillRenameFilesMoveHandler extends MoveFileHandler {

    @Override
    public boolean canProcessElement(PsiFile element) {
        return false;
    }

    @Override
    public void detectConflicts(MultiMap<PsiElement, String> conflicts,
                                PsiElement[] elementsToMove,
                                UsageInfo[] usages,
                                PsiDirectory targetDirectory) {
        if (targetDirectory == null || elementsToMove.length == 0 ||
                !WillRenameFilesPreparation.isWillRenameFilesSupported(targetDirectory.getProject())) {
            return;
        }
        var preparation = WillRenameFilesPreparation.getInstance(targetDirectory.getProject());
        var newParent = targetDirectory.getVirtualFile();
        for (PsiElement element : elementsToMove) {
            var file = element instanceof PsiFileSystemItem item ? item.getVirtualFile() : null;
            if (file != null) {
                preparation.plan(new FileRenamePlan(element, file, newParent, file.getName()));
            }
        }
    }

    @Override
    public void prepareMovedFile(PsiFile file, PsiDirectory moveDestination, Map<PsiElement, PsiElement> oldToNewMap) {
    }

    @Override
    public List<UsageInfo> findUsages(PsiFile psiFile, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles) {
        return null;
    }

    @Override
    public void retargetUsages(List<UsageInfo> usageInfos, Map<PsiElement, PsiElement> oldToNewMap) {
    }

    @Override
    public void updateMovedFile(PsiFile file) {
    }
}

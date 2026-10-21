/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.files;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Plans the {@code workspace/willRenameFiles} requests for the files and directories that a rename
 * refactoring renames, which IntelliJ passes to every processor of the renamed element before the
 * write action (see {@link WillRenameFilesPreparation}).
 * <p>
 * This processor never renames anything itself: it accepts an element only when a processor before
 * it does, so IntelliJ always renames the element with that one.
 */
public class WillRenameFilesRenameProcessor extends RenamePsiElementProcessor {

    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
        if (!element.isValid() || !WillRenameFilesPreparation.isWillRenameFilesSupported(element.getProject())) {
            return false;
        }
        for (RenamePsiElementProcessorBase processor : EP_NAME.getExtensionList()) {
            if (processor == this) {
                return false;
            }
            if (processor.canProcessElement(element)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void prepareRenaming(@NotNull PsiElement element,
                                @NotNull String newName,
                                @NotNull Map<PsiElement, String> allRenames,
                                @NotNull SearchScope scope) {
        // The processors before this one added the files that renaming the element renames, such as
        // the file of a renamed class
        var preparation = WillRenameFilesPreparation.getInstance(element.getProject());
        allRenames.forEach((renamed, name) -> {
            if (renamed instanceof PsiFileSystemItem item) {
                var file = item.getVirtualFile();
                var parent = file != null ? file.getParent() : null;
                if (parent != null) {
                    preparation.plan(new WillRenameFilesPreparation.FileRenamePlan(element, file, parent, name));
                }
            }
        });
    }
}

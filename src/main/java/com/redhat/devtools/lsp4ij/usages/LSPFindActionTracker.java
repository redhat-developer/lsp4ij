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
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.find.actions.FindUsagesAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTargetUtil;
import org.jetbrains.annotations.NotNull;

/**
 * "Find Usages" action execution tracker used to know if {@link LSPUsageTargetProvider}
 * must return LSP {@link com.intellij.usages.UsageTarget} or not:
 *
 * <ul>
 *     <li>when no UsageTarget is already associated to a language, the LSP UsageTarget must be returned to enable the `Find usages` command.</li>
 *     <li>when a UsageTarget is already associated to a language (e.g. Java in IDEA), the LSP UsageTarget must not be returned,
 *     to avoid overriding the platform native support for `Find usages` for that language.</li>
 * </ul>
 */
public class LSPFindActionTracker implements AnActionListener {

    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
        if (action instanceof FindUsagesAction) {
            // Before executing "Find Usages"....

            // The Find usages can be processed if
            // - Psi file and editor exists
            // - or Psi element exists
            PsiFile psiFile = event.getData(CommonDataKeys.PSI_FILE);
            Editor editor = event.getData(CommonDataKeys.EDITOR);
            PsiElement psiElement = event.getData(CommonDataKeys.PSI_ELEMENT);
            if ((editor == null || psiFile == null) && psiElement == null) {
                return;
            }

            // Find usages will be processed...

            // Disable collection of LSP UsageTarget
            if (psiFile != null) {
                LSPUsageTargetProvider.setDisabled(psiFile, true);
            }
            if (psiElement != null) {
                LSPUsageTargetProvider.setDisabled(psiElement, true);
            }

            // Collect other usage targets while the LSP Usage Target is disabled
            var result = UsageTargetUtil.findUsageTargets(id -> event.getDataContext().getData(id));
            if (result == null || result.length == 0) {
                // No other existing usage targets, re-enable the LSP usage target
                // to contribute to the `Find Usages` command
                if (psiFile != null) {
                    LSPUsageTargetProvider.setDisabled(psiFile, null);
                }
                if (psiElement != null) {
                    LSPUsageTargetProvider.setDisabled(psiElement, null);
                }
            }
        }
    }

}

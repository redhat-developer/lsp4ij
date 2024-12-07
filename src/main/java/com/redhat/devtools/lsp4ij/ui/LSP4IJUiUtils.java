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

package com.redhat.devtools.lsp4ij.ui;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.function.Function;

/**
 * Common UI utilities.
 */
public final class LSP4IJUiUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSP4IJUiUtils.class);

    private LSP4IJUiUtils() {
        // Pure utility class
    }

    public enum HintType {

    }

    /**
     * Shows the provided hint in the specified file.
     *
     * @param file         the file in which the hint should be shown
     * @param hint         the hint text
     * @param labelCreator function that creates a component for the provided hint text, typically one of the methods
     *                     from {@link HintUtil}
     */
    public static void showHint(@NotNull PsiFile file,
                                @NotNull String hint,
                                @NotNull Function<String, JComponent> labelCreator) {
        Editor editor = LSPIJUtils.editorForElement(file);
        if (editor != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
                JComponent label = labelCreator.apply(hint);
                label.setBorder(HintUtil.createHintBorder());
                int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
                HintManagerImpl.getInstanceImpl().showEditorHint(new LightweightHint(label), editor, HintManager.ABOVE, flags, 0, false);
            });
        } else {
            // If it couldn't be shown it as an editor hint, show it in the status bar
            LOGGER.warn("Could not find an active editor for file '{}' to show hint: {}", file.getName(), hint);
            StatusBarUtil.setStatusBarInfo(file.getProject(), hint);
        }
    }

    /**
     * Shows the provided hint in the specified file as an error.
     *
     * @param file the file in which the hint should be shown
     * @param hint the hint text
     */
    public static void showErrorHint(@NotNull PsiFile file, @NotNull String hint) {
        showHint(file, hint, HintUtil::createErrorLabel);
    }

    /**
     * Shows the provided hint in the specified file as an warning.
     *
     * @param file the file in which the hint should be shown
     * @param hint the hint text
     */
    public static void showWarningHint(@NotNull PsiFile file, @NotNull String hint) {
        showHint(file, hint, HintUtil::createWarningLabel);
    }

    /**
     * Shows the provided hint in the specified file as information.
     *
     * @param file the file in which the hint should be shown
     * @param hint the hint text
     */
    public static void showInformationHint(@NotNull PsiFile file, @NotNull String hint) {
        showHint(file, hint, HintUtil::createInformationLabel);
    }

    /**
     * Shows the provided hint in the specified file as success.
     *
     * @param file the file in which the hint should be shown
     * @param hint the hint text
     */
    public static void showSuccessHint(@NotNull PsiFile file, @NotNull String hint) {
        showHint(file, hint, HintUtil::createSuccessLabel);
    }

    /**
     * Shows the provided hint in the specified file as a question.
     *
     * @param file the file in which the hint should be shown
     * @param hint the hint text
     */
    public static void showQuestionHint(@NotNull PsiFile file, @NotNull String hint) {
        showHint(file, hint, HintUtil::createQuestionLabel);
    }
}

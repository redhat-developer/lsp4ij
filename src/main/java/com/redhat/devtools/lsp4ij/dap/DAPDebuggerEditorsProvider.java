/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase;
import com.redhat.devtools.lsp4ij.dap.evaluation.DAPExpressionCodeFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debug Adapter Protocol (DAP) editor provider.
 */
public class DAPDebuggerEditorsProvider extends XDebuggerEditorsProviderBase {

    private final @Nullable FileType fileType;
    private final @NotNull DAPDebugProcess debugProcess;

    public DAPDebuggerEditorsProvider(@Nullable FileType fileType,
                                      @NotNull DAPDebugProcess debugProcess) {
        this.fileType = fileType != null ? fileType : PlainTextFileType.INSTANCE;
        this.debugProcess = debugProcess;
    }

    @Override
    public @NotNull FileType getFileType() {
        return fileType != null ? fileType : PlainTextFileType.INSTANCE;
    }

    @Override
    public @NotNull Document createDocument(@NotNull Project project, @NotNull XExpression expression, @Nullable PsiElement context, @NotNull EvaluationMode mode) {
        if (context == null || context.getContainingFile() == null) {
            // File is null, returns a dummy document
            return new DocumentImpl(expression.getExpression());
        }
        return super.createDocument(project, expression, context, mode);
    }

    @Override
    protected PsiFile createExpressionCodeFragment(@NotNull Project project,
                                                   @NotNull String text,
                                                   @Nullable PsiElement context,
                                                   boolean isPhysical) {
        FileType fileType = getFileType();
        Language language = Language.ANY;
        // File should be never null here
        PsiFile file = context != null ? context.getContainingFile() : null;
        if (file != null) {
            // Get file type / language of the file which is debugging when debugger is suspended.
            fileType = file.getFileType();
            language = file.getLanguage();
        }
        return new DAPExpressionCodeFragment(text, fileType, language, debugProcess, project);
    }
}

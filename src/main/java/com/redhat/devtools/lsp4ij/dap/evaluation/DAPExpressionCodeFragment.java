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
package com.redhat.devtools.lsp4ij.dap.evaluation;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.testFramework.LightVirtualFile;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debug Adapter Protocol (DAP) expression code fragment
 * used in the "Evaluate expression" editor.
 */
public class DAPExpressionCodeFragment extends PsiFileBase {

    private final @NotNull FileType fileType;
    private final @NotNull DAPDebugProcess debugProcess;

    public DAPExpressionCodeFragment(@NotNull CharSequence text,
                                     @NotNull FileType fileType,
                                     @NotNull Language language,
                                     @NotNull DAPDebugProcess debugProcess,
                                     @NotNull Project project) {
        super(new SingleRootFileViewProvider(PsiManager.getInstance(project),
                new LightVirtualFile("DAPExpressionCodeFragment." + fileType.getDefaultExtension(), fileType, text)),
                language);
        this.fileType = fileType;
        this.debugProcess = debugProcess;
        ((SingleRootFileViewProvider) getViewProvider()).forceCachedPsi(this);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return fileType;
    }

    public @Nullable DAPStackFrame getCurrentDapStackFrame() {
        return debugProcess.getCurrentDapStackFrame();
    }
}
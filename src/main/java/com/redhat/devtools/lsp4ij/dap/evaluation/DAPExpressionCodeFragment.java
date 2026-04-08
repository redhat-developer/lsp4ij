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
    private final @Nullable DAPDebugProcess debugProcess;

    public DAPExpressionCodeFragment(@NotNull CharSequence text,
                                     @NotNull FileType fileType,
                                     @NotNull Language language,
                                     @Nullable DAPDebugProcess debugProcess,
                                     @NotNull Project project) {
        super(new SingleRootFileViewProvider(PsiManager.getInstance(project),
                createLightVirtualFile(text, fileType, language)),
                language);
        this.fileType = fileType;
        this.debugProcess = debugProcess;
        ((SingleRootFileViewProvider) getViewProvider()).forceCachedPsi(this);
    }

    @NotNull
    private static LightVirtualFile createLightVirtualFile(@NotNull CharSequence text,
                                                           @NotNull FileType fileType,
                                                           @NotNull Language language) {
        String codeFragmentFilename = "DAPExpressionCodeFragment." + fileType.getDefaultExtension();

        // If this is a dialect, use the base language so that the code fragment supports all dialects needed during
        // debugging, even if in a least common denominator manner
        Language baseLanguage = language.getBaseLanguage();
        if ((baseLanguage != null) && !baseLanguage.is(language)) {
            return new LightVirtualFile(codeFragmentFilename, baseLanguage, text);
        } else {
            return new LightVirtualFile(codeFragmentFilename, fileType, text);
        }
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return fileType;
    }

    @Override
    public boolean isPhysical() {
        return false;
    }

    public @Nullable DAPStackFrame getCurrentDapStackFrame() {
        return debugProcess != null ? debugProcess.getCurrentDapStackFrame() : null;
    }
}
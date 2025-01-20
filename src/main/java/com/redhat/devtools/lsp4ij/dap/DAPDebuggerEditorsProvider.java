package com.redhat.devtools.lsp4ij.dap;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
    protected PsiFile createExpressionCodeFragment(@NotNull Project project,
                                                   @NotNull String text,
                                                   @Nullable PsiElement context,
                                                   boolean isPhysical) {
        FileType fileType = getFileType();
        Language language = PlainTextLanguage.INSTANCE;
        PsiFile file = context != null ? context.getContainingFile() : null;
        if (file != null) {
            // Get file type / language of the file which is debugging when debugger is suspended.
            fileType = file.getFileType();
            language = file.getLanguage();
        }
        return new DAPExpressionCodeFragment(text, fileType, language, debugProcess, project);
    }
}

package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.codeInspection.ExternalAnnotatorInspectionVisitor;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LSPDiagnosticAnnotatorInspection extends LocalInspectionTool {

    private final LSPDiagnosticAnnotator annotator = new LSPDiagnosticAnnotator(true);

    @Override
    @NotNull
    public String getShortName() {
        return LSPDiagnosticAnnotatorInspection.class.getSimpleName();
    }

    @Override
    @Nullable
    @Nls
    public String getStaticDescription() {
        return LanguageServerBundle.message("lsp.diagnostic.inspection.description");
    }

    @Override
    public boolean runForWholeFile() {
        return true;
    }

    @Override
    public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file,
                                                    @NotNull InspectionManager manager,
                                                    boolean isOnTheFly) {
        if (!isOnTheFly) {
            return ExternalAnnotatorInspectionVisitor.checkFileWithExternalAnnotator(file, manager, false, annotator);
        } else {
            return ProblemDescriptor.EMPTY_ARRAY;
        }
    }
}

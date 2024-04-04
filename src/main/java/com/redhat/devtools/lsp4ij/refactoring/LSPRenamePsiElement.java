package com.redhat.devtools.lsp4ij.refactoring;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class LSPRenamePsiElement extends LSPPsiElement {
    private final TextEdit textEdit;

    public LSPRenamePsiElement(@NotNull PsiFile file, @NotNull TextRange textRange, TextEdit textEdit) {
        super(file, textRange);
        this.textEdit = textEdit;
    }

    public void rename() {
        Document document = LSPIJUtils.getDocument(getContainingFile().getVirtualFile());
        Editor[] editors = LSPIJUtils.editorsForFile(getContainingFile().getVirtualFile(), getContainingFile().getProject());
        LSPIJUtils.applyEdits(editors[0], document, Arrays.asList(textEdit));
    }
}

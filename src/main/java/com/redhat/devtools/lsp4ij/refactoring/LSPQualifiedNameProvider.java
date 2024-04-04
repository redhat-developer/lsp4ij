package com.redhat.devtools.lsp4ij.refactoring;

import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LSPQualifiedNameProvider implements QualifiedNameProvider {
    @Override
    public @Nullable PsiElement adjustElementToCopy(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public @Nullable String getQualifiedName(@NotNull PsiElement element) {
        if (element instanceof LSPPsiElement) {
            return "LSP";
        }
        return null;
    }

    @Override
    public @Nullable PsiElement qualifiedNameToElement(@NotNull String fqn, @NotNull Project project) {
        return null;
    }
}

package com.redhat.devtools.lsp4ij.features.highlight;

import com.intellij.codeInsight.TargetElementEvaluatorEx2;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.client.ProjectIndexingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class LSPTargetElementEvaluator extends TargetElementEvaluatorEx2 {
    @Override
    public @Nullable PsiElement adjustReferenceOrReferencedElement(@NotNull PsiFile file, @NotNull Editor editor, int offset, int flags, @Nullable PsiElement refElement) {
        if (!LanguageServersRegistry.getInstance().isFileSupported(file)) {
            return null;
        }

        if (ProjectIndexingManager.isIndexingAll()) {
            return null;
        }

        // Try to find the highlighted identifier at the caret and return that fake PSI element
        HighlightUsagesHandlerBase<LSPHighlightPsiElement> highlightUsagesHandler = new LSPHighlightUsagesHandlerFactory().createHighlightUsagesHandler(editor, file);
        List<LSPHighlightPsiElement> targets = highlightUsagesHandler != null ? highlightUsagesHandler.getTargets() : Collections.emptyList();
        return ContainerUtil.find(targets, target -> (target.getTextRange() != null) && target.getTextRange().contains(offset));
    }
}

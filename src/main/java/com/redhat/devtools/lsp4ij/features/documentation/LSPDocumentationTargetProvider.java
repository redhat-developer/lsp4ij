package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.eclipse.lsp4j.Hover;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

public class LSPDocumentationTargetProvider implements DocumentationTargetProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPDocumentationTargetProvider.class);

    @Override
    public @NotNull List<? extends @NotNull DocumentationTarget> documentationTargets(@NotNull PsiFile psiFile, int offset) {
        VirtualFile file = LSPIJUtils.getFile(psiFile);
        if (file == null) {
            return Collections.emptyList();
        }
        final Document document = LSPIJUtils.getDocument(file);
        if (document == null) {
            return Collections.emptyList();
        }
        LSPHoverSupport hoverSupport = LSPFileSupport.getSupport(psiFile).getHoverSupport();
        CompletableFuture<List<Hover>> hoverFuture = hoverSupport.getHover(offset, document);

        try {
            waitUntilDone(hoverFuture, psiFile);
        } catch (ProcessCanceledException | CancellationException e) {
            // cancel the LSP requests textDocument/hover
            hoverSupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/hover' request", e);
        }

        if (isDoneNormally(hoverFuture)) {
            // textDocument/hover has been collected correctly
            List<Hover> hovers = hoverFuture.getNow(null);
            if (hovers != null) {
                return hovers
                        .stream()
                        .map(LSPDocumentationTargetProvider::toDocumentTarget)
                        .filter(Objects::nonNull)
                        .toList();
            }
        }

        return Collections.emptyList();
    }

    @Nullable
    private static DocumentationTarget toDocumentTarget(@Nullable Hover hover) {
        if (hover == null) {
            return null;
        }
        return new LSPDocumentationTarget(hover);
    }
}

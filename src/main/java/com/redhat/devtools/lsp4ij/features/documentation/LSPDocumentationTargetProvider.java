/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.lang.LanguageDocumentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.features.documentation.LSPDocumentationHelper.getValidMarkupContents;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.awaitWithCheckCanceled;

/**
 * LSP {@link DocumentationTargetProvider} implementation used to consume
 * LSP 'textDocument/hover' request and returns alist of {@link LSPDocumentationTarget}
 * per language server, or a {@link LSPPendingHoverDocumentationTarget} while the reply isn't there.
 */
public class LSPDocumentationTargetProvider implements DocumentationTargetProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPDocumentationTargetProvider.class);

    /**
     * How long {@link #documentationTargets(PsiFile, int)} waits at most for the 'textDocument/hover' reply in a file
     * that has documentation of its own.
     */
    private static final long MAX_HOVER_WAIT_MS = 500;

    /**
     * Returns the documentation targets of the 'textDocument/hover' reply at the given offset, without keeping the UI
     * waiting for the language server: IntelliJ also asks for them when it updates Quick Documentation, while the UI
     * thread waits for the update.
     * <p>
     * In a file without documentation of its own, such as a plain text, TextMate or abstract file type file, or a file
     * whose languages register no documentation provider, a reply that is not there yet is returned as a target which
     * waits for it in the background. In another file, IntelliJ shows the file's own documentation only if no provider
     * returns a target, so the reply is waited for {@link #MAX_HOVER_WAIT_MS} at most; a later reply is used the next
     * time at the same offset.
     */
    @Override
    public @NotNull List<? extends @NotNull DocumentationTarget> documentationTargets(@NotNull PsiFile psiFile, int offset) {
        HoverRequest request = requestHover(psiFile, offset);
        if (request == null) {
            return Collections.emptyList();
        }
        if (!request.future().isDone()) {
            if (hasNoDocumentationOfItsOwn(psiFile)) {
                return List.of(new LSPPendingHoverDocumentationTarget(request.future(), request.document(), psiFile));
            }
            // Returns null when the time is up
            ProgressIndicatorUtils.withTimeout(MAX_HOVER_WAIT_MS, () -> await(request.future()));
        }
        return toDocumentationTargets(request.future(), request.document(), psiFile);
    }

    /**
     * Returns the documentation targets of the 'textDocument/hover' reply at the given offset, waiting for the reply.
     *
     * @param psiFile the file.
     * @param offset  the offset.
     * @return the documentation targets, one per language server which has documentation at the offset.
     */
    @ApiStatus.Internal
    public @NotNull List<LSPDocumentationTarget> getHoverTargets(@NotNull PsiFile psiFile, int offset) {
        HoverRequest request = requestHover(psiFile, offset);
        if (request == null) {
            return Collections.emptyList();
        }
        await(request.future());
        return toDocumentationTargets(request.future(), request.document(), psiFile);
    }

    private record HoverRequest(@NotNull CompletableFuture<List<HoverData>> future, @NotNull Document document) {
    }

    private static @Nullable HoverRequest requestHover(@NotNull PsiFile psiFile, int offset) {
        Project project = psiFile.getProject();
        if (project.isDisposed() || DumbService.isDumb(project)) {
            return null;
        }
        if (ProjectIndexingManager.canExecuteLSPFeature(psiFile) != ExecuteLSPFeatureStatus.NOW) {
            return null;
        }
        VirtualFile file = LSPIJUtils.getFile(psiFile);
        if (file == null) {
            return null;
        }
        final Document document = LSPIJUtils.getDocument(file);
        if (document == null) {
            return null;
        }
        var params = new LSPHoverParams(new TextDocumentIdentifier(), LSPIJUtils.toPosition(offset, document), offset);
        LSPHoverSupport hoverSupport = LSPFileSupport.getSupport(psiFile).getHoverSupport();
        return new HoverRequest(hoverSupport.getHover(params), document);
    }

    /**
     * Returns true if IntelliJ has no documentation of its own for the given file, which only a language server
     * documents.
     */
    private static boolean hasNoDocumentationOfItsOwn(@NotNull PsiFile psiFile) {
        return LSPIJEditorUtils.isPlainTextFile(psiFile)
                || LSPIJEditorUtils.isTextMateFile(psiFile)
                || LSPIJEditorUtils.isAbstractFileTypeFile(psiFile)
                || registersNoDocumentationProvider(psiFile);
    }

    /**
     * Returns true if none of the languages of the given file registers a documentation provider, as a language whose
     * plugin leaves its documentation to a language server does. The documentation providers of LSP4IJ, which are
     * registered for any language, are not those of a language.
     */
    private static boolean registersNoDocumentationProvider(@NotNull PsiFile psiFile) {
        return psiFile.getViewProvider().getLanguages().stream()
                .allMatch(language -> LanguageDocumentation.INSTANCE.allForLanguage(language).isEmpty());
    }

    /**
     * Waits for the given 'textDocument/hover' reply.
     *
     * @return null, for {@link ProgressIndicatorUtils#withTimeout(long, com.intellij.openapi.util.Computable)}.
     */
    static @Nullable Void await(@NotNull CompletableFuture<List<HoverData>> hoverFuture) {
        try {
            awaitWithCheckCanceled(hoverFuture);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (CancellationException e) {
            // ignore
        }
        return null;
    }

    /**
     * Returns the documentation targets of the given 'textDocument/hover' reply, or none if it isn't there.
     */
    static @NotNull List<LSPDocumentationTarget> toDocumentationTargets(@NotNull CompletableFuture<List<HoverData>> hoverFuture,
                                                                       @NotNull Document document,
                                                                       @NotNull PsiFile psiFile) {
        if (isDoneNormally(hoverFuture)) {
            // textDocument/hover has been collected correctly
            List<HoverData> hovers = hoverFuture.getNow(null);
            if (hovers != null) {
                return hovers
                        .stream()
                        .map(hover -> toDocumentTarget(hover, document, psiFile))
                        .filter(Objects::nonNull)
                        .toList();
            }
        }
        return Collections.emptyList();
    }

    @Nullable
    private static LSPDocumentationTarget toDocumentTarget(@Nullable HoverData hoverData,
                                                        @NotNull Document document,
                                                        @NotNull PsiFile file) {
        Hover hover = hoverData != null ? hoverData.hover() : null;
        if (hover == null) {
            return null;
        }
        List<MarkupContent> contents = getValidMarkupContents(hover);
        if (contents.isEmpty()) {
            return null;
        }
        String presentationText = getPresentationText(hover, document);
        return new LSPDocumentationTarget(contents, presentationText, file, hoverData.languageServer());
    }

    private static String getPresentationText(@NotNull Hover hover, @NotNull Document document) {
        Range range = hover.getRange();
        if (range != null) {
            TextRange textRange = LSPIJUtils.toTextRange(range, document);
            return textRange != null ? document.getText(textRange) : null;
        }
        return null;
    }


}

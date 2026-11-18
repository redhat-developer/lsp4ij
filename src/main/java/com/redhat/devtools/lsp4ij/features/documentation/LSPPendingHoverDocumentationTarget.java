/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.model.Pointer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * The documentation of a 'textDocument/hover' reply that is not there yet. {@link LSPDocumentationTargetProvider}
 * returns it in a file without documentation of its own instead of waiting for the language server, and IntelliJ
 * computes its documentation in the background, without the read lock.
 */
@ApiStatus.Internal
public class LSPPendingHoverDocumentationTarget implements DocumentationTarget {

    private final CompletableFuture<List<HoverData>> hoverFuture;
    private final Document document;
    // The modification stamp of the document that the hover was asked for
    private final long modificationStamp;
    private final PsiFile file;

    LSPPendingHoverDocumentationTarget(@NotNull CompletableFuture<List<HoverData>> hoverFuture,
                                       @NotNull Document document,
                                       @NotNull PsiFile file) {
        this.hoverFuture = hoverFuture;
        this.document = document;
        this.modificationStamp = document.getModificationStamp();
        this.file = file;
    }

    @NotNull
    @Override
    public TargetPresentation computePresentation() {
        // As for a hover without a range: the reply that could have one isn't there yet
        return TargetPresentation.builder("").presentation();
    }

    @Nullable
    @Override
    public DocumentationResult computeDocumentation() {
        // IntelliJ calls the supplier on a background thread without the read lock, where it can wait for the reply
        return DocumentationResult.asyncDocumentation(() -> {
            String html = getHtml();
            return html != null ? DocumentationResult.documentation(html) : null;
        });
    }

    /**
     * Waits for the reply, and returns its documentation targets, one per language server which has documentation
     * here. A reply is left out once the document has changed since the hover was asked for: its range, whose text
     * the target presents, is that of the earlier text.
     *
     * @return the documentation targets of the reply.
     */
    public @NotNull List<LSPDocumentationTarget> getTargets() {
        LSPDocumentationTargetProvider.await(hoverFuture);
        return ReadAction.compute(() -> file.isValid() && document.getModificationStamp() == modificationStamp
                ? LSPDocumentationTargetProvider.toDocumentationTargets(hoverFuture, document, file)
                : List.of());
    }

    /**
     * Waits for the reply, and returns its documentation as HTML, or null if it has none.
     *
     * @return the documentation as HTML, or null.
     */
    public @Nullable String getHtml() {
        List<LSPDocumentationTarget> targets = getTargets();
        if (targets.isEmpty()) {
            return null;
        }
        // The separator of the contents of one hover, see LSPDocumentationHelper.convertToHtml
        return ReadAction.compute(() -> targets.stream()
                .map(LSPDocumentationTarget::getHtml)
                .collect(Collectors.joining("<hr />")));
    }

    @NotNull
    @Override
    public Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }
}

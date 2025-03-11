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

import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.eclipse.lsp4j.MarkupContent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.redhat.devtools.lsp4ij.features.documentation.LSPDocumentationHelper.convertToHtml;

/**
 * LSP {@link DocumentationTarget} implementation.
 */
public class LSPDocumentationTarget implements DocumentationTarget {

    private final List<MarkupContent> contents;
    private final LanguageServerItem languageServer;
    private final String presentationText;
    private final PsiFile file;

    public LSPDocumentationTarget(@NotNull List<MarkupContent> contents,
                                  String presentationText,
                                  @NotNull PsiFile file,
                                  @NotNull LanguageServerItem languageServer) {
        this.contents = contents;
        this.presentationText = presentationText;
        this.file = file;
        this.languageServer = languageServer;
    }

    @NotNull
    @Override
    public TargetPresentation computePresentation() {
        return TargetPresentation
                .builder(presentationText != null ? presentationText : "")
                .presentation();
    }

    @Nullable
    @Override
    public DocumentationResult computeDocumentation() {
        return DocumentationResult.documentation(getHtml());
    }

    @NotNull
    @ApiStatus.Internal
    public String getHtml() {
        return convertToHtml(contents, languageServer, file);
    }

    @NotNull
    @Override
    public Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }

    public PsiFile getFile() {
        return file;
    }

    public LanguageServerItem getLanguageServer() {
        return languageServer;
    }
}

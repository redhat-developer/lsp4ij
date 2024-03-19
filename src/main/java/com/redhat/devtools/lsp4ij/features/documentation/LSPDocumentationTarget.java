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
import com.intellij.openapi.editor.Editor;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.redhat.devtools.lsp4ij.features.documentation.LSPDocumentationHelper.convertToHTML;

/**
 * LSP {@link DocumentationTarget} implementation.
 */
public class LSPDocumentationTarget implements DocumentationTarget {

    private final Hover hover;
    private final String presentationText;
    private final Editor editor;

    public LSPDocumentationTarget(@NotNull Hover hover,
                                  String presentationText,
                                  @Nullable Editor editor) {
        this.hover = hover;
        this.presentationText = presentationText;
        this.editor = editor;
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
        return DocumentationResult.documentation(toHTMLHover(hover, editor));
    }

    @NotNull
    @Override
    public Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }

    private String toHTMLHover(Hover hover, Editor editor) {
        MarkupContent content = getHoverString(hover);
        return convertToHTML(content, editor);
    }

    private static @Nullable MarkupContent getHoverString(Hover hover) {
        if (hover == null) {
            // textDocument/hover may return null
            return null;
        }
        Either<List<Either<String, MarkedString>>, MarkupContent> hoverContent = hover.getContents();
        if (hoverContent == null) {
            return null;
        }
        if (hoverContent.isLeft()) {
            List<Either<String, MarkedString>> contents = hoverContent.getLeft();
            if (contents == null || contents.isEmpty()) {
                return null;
            }
            String s = contents.stream()
                    .map(content -> {
                        if (content.isLeft()) {
                            return content.getLeft();
                        } else if (content.isRight()) {
                            MarkedString markedString = content.getRight();
                            // TODO this won't work fully until markup parser will support syntax
                            // highlighting but will help display
                            // strings with language tags, e.g. without it things after <?php tag aren't
                            // displayed
                            if (markedString.getLanguage() != null && !markedString.getLanguage().isEmpty()) {
                                return String.format("```%s%n%s%n```", markedString.getLanguage(), markedString.getValue()); //$NON-NLS-1$
                            } else {
                                return markedString.getValue();
                            }
                        } else {
                            return ""; //$NON-NLS-1$
                        }
                    })
                    .filter(((Predicate<String>) String::isEmpty).negate())
                    .collect(Collectors.joining("\n\n"));
            return new MarkupContent(MarkupKind.PLAINTEXT, s);
        } else {
            return hoverContent.getRight();
        }
    }
}

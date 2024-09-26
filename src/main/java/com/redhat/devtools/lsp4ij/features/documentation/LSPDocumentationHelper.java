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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.client.features.LSPHoverFeature;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * LSP documentation utilities.
 */
public class LSPDocumentationHelper {

    private LSPDocumentationHelper() {

    }

    public static @NotNull List<MarkupContent> getValidMarkupContents(@Nullable CompletionItem completionItem) {
        if (completionItem == null) {
            // textDocument/completion may return null
            return Collections.emptyList();
        }
        var documentation = completionItem.getDocumentation();
        if (documentation == null) {
            return Collections.emptyList();
        }
        if (documentation.isLeft()) {
            String value = documentation.getLeft();
            if (!isValidContent(value)) {
                return Collections.emptyList();
            }
            return List.of(new MarkupContent(MarkupKind.PLAINTEXT, value));
        } else if (documentation.isRight()) {
            MarkupContent content = documentation.getRight();
            if (!isValidContent(content)) {
                return Collections.emptyList();
            }
            return List.of(content);
        }
        return Collections.emptyList();
    }

    public static @NotNull List<MarkupContent> getValidMarkupContents(@Nullable Hover hover) {
        if (hover == null) {
            // textDocument/hover may return null
            return Collections.emptyList();
        }
        var hoverContents = hover.getContents();
        if (hoverContents == null) {
            return Collections.emptyList();
        }
        if (hoverContents.isLeft()) {
            var contents = hoverContents.getLeft();
            if (contents == null || contents.isEmpty()) {
                return Collections.emptyList();
            }
            return contents.stream()
                    .map(content -> {
                        if (content.isLeft()) {
                            String value = content.getLeft();
                            if (!isValidContent(value)) {
                                return null;
                            }
                            return new MarkupContent(MarkupKind.MARKDOWN, value);
                        } else if (content.isRight()) {
                            MarkedString markedString = content.getRight();
                            if (!isValidContent(markedString)) {
                                return null;
                            }
                            String value = markedString.getValue();
                            String language = markedString.getLanguage() != null && !markedString.getLanguage().isEmpty() ?
                                    markedString.getLanguage() :
                                    null;
                            if (language == null) {
                                return new MarkupContent(MarkupKind.MARKDOWN, value);
                            }
                            value = String.format("```%s%n%s%n```", language, value);
                            return new MarkupContent(MarkupKind.MARKDOWN, value);
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .toList();
        } else if (hoverContents.isRight()) {
            MarkupContent content = hoverContents.getRight();
            if (isValidContent(content)) {
                return List.of(content);
            }
        }
        return Collections.emptyList();
    }

    private static boolean isValidContent(@Nullable MarkedString content) {
        return content != null && isValidContent(content.getValue());
    }

    private static boolean isValidContent(@Nullable MarkupContent content) {
        return content != null && isValidContent(content.getValue());
    }

    private static boolean isValidContent(@Nullable String value) {
        return value != null && StringUtils.isNotBlank(value);
    }

    /**
     * Convert the given LSP markup content to an HTML string and an empty string otherwise.
     *
     * @param contents the markup contents.
     * @param file the file which has triggered the hover / completion documentation.
     * @return the converted HTML of the LSP markup content and an empty string otherwise.
     */
    public static String convertToHtml(@NotNull List<MarkupContent> contents,
                                       @Nullable LanguageServerItem languageServer,
                                       @NotNull PsiFile file) {
        Project project = file.getProject();
        StringBuilder htmlBody = new StringBuilder();
        for (int i = 0; i < contents.size(); i++) {
            if (i > 0) {
                htmlBody.append("<hr />");
            }
            MarkupContent markupContent = contents.get(i);
            var hoverFeatures = languageServer != null ? languageServer.getClientFeatures().getHoverFeature()
                    : new LSPHoverFeature();
            String content = hoverFeatures.getContent(markupContent, file);
            if(content != null) {
                htmlBody.append(content);
            }
        }
        return htmlBody.toString();
    }

}

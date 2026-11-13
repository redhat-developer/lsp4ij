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
package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.eclipse.lsp4j.FormattingOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * LSP formatting parameters.
 *
 * @param textRanges            the text ranges to format, or an empty list to format the whole document.
 * @param document              the document.
 * @param formattingServer
 * @param formattingOptions
 */
public record LSPFormattingParams(@NotNull List<TextRange> textRanges,
                                  @NotNull Document document,
                                  @NotNull LanguageServerItem formattingServer,
                                  @NotNull FormattingOptions formattingOptions) {

    /**
     * LSP formatting parameters of one text range.
     *
     * @param textRange the text range to format, or null to format the whole document.
     * @deprecated use the constructor which takes a list of text ranges, as IntelliJ can format several ranges of a
     * file at once.
     */
    @Deprecated(since = "0.30.0", forRemoval = true)
    public LSPFormattingParams(@Nullable TextRange textRange,
                               @NotNull Document document,
                               @NotNull LanguageServerItem formattingServer,
                               @NotNull FormattingOptions formattingOptions) {
        this(textRange != null ? List.of(textRange) : List.of(), document, formattingServer, formattingOptions);
    }

    /**
     * Returns the first text range to format, or null to format the whole document.
     *
     * @deprecated use {@link #textRanges()}, as IntelliJ can format several ranges of a file at once.
     */
    @Deprecated(since = "0.30.0", forRemoval = true)
    public @Nullable TextRange textRange() {
        return textRanges.isEmpty() ? null : textRanges.get(0);
    }
}

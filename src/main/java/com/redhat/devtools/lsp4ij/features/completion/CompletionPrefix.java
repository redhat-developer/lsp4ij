/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.internal.CompletionUtils;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Completion prefix provides the capability to compute the prefix where the completion has been triggered and by using the LSP {@link org.eclipse.lsp4j.TextEdit} information.
 * <p>
 * It caches the prefix compute for a given TextEdit to avoid computing the prefix for all completion items which have the same {@link org.eclipse.lsp4j.TextEdit}
 */
public class CompletionPrefix {

    private final int completionOffset;
    private final Position completionPos;
    private final Document document;

    private final Map<Range, String /* prefix */> prefixCache;

    private @Nullable String wordPrefix;

    public CompletionPrefix(int completionOffset, Document document) {
        this.document = document;
        this.completionOffset = completionOffset;
        this.completionPos = LSPIJUtils.toPosition(completionOffset, document);
        this.prefixCache = new HashMap<>();
    }

    public Document getDocument() {
        return document;
    }

    /**
     * Returns the proper prefix from the given text range and label/filterText defined in the given completion item and null otherwise.
     *
     * @param textEditRange the completion item edit range.
     * @param item          the completion item.
     * @return the proper prefix from the given text range and label/filterText defined in the given completion item and null otherwise.
     */
    public @Nullable String getPrefixFor(@NotNull Range textEditRange,
                                         @NotNull CompletionItem item) {
        // Try to get the computed prefix from the cache
        String prefix = prefixCache.get(textEditRange);
        if (prefix == null && !prefixCache.containsKey(textEditRange)) {
            // Compute the prefix which can be null
            // ex : {#ea|ch will return {#ea
            prefix = CompletionProposalTools.getCompletionPrefix(completionPos, textEditRange, document);
            prefixCache.put(textEditRange, prefix);
        }
        if (prefix == null) {
            // - null prefix
            return prefix;
        }
        String filterText = getAccurateFilterText(item);
        if (filterText == null) {
            // - no filter text defined
            return prefix;
        }
        // Filter text is defined here (ex : {#each
        // In case of Qute, completion item has the following data:
        // - label = each
        // - filterText = {#each
        // - text edit = [{#each]
        // if completion is triggered in {#ea|ch
        // prefix will be {#ea
        // Here we need to remove the '{#' defined by the filter text to return the proper prefix 'ea'
        int index = 0;
        for (int i = 0; i < Math.min(prefix.length(), filterText.length()); i++) {
            if (prefix.charAt(i) == filterText.charAt(i)) {
                index++;
            } else {
                break;
            }
        }
        if (index > 0) {
            // Remove '{#' from '{#ea' to get the proper prefix 'ea'
            prefix = prefix.substring(index);
        }
        return prefix;
    }

    /**
     * Returns the prefix of a completion item without a text edit, when the given prefix, which IntelliJ computes from
     * the PSI element at the completion offset, isn't one, and null otherwise.
     * <p>
     * The prefix that IntelliJ computes spans whitespace when a custom PSI keeps a whole comment as one token, as
     * {@code disable-next-line : u}, which matches no completion item. The prefix is then the word that ends at the
     * completion offset, {@code u}, as IntelliJ completes in a comment, and as VS Code completes an item without a
     * text edit (#447).
     *
     * @param psiPrefix the prefix that IntelliJ computes from the PSI element at the completion offset.
     * @return the word that ends at the completion offset if the given prefix spans whitespace, and null otherwise.
     */
    public @Nullable String getWordPrefix(@NotNull String psiPrefix) {
        if (!CompletionUtils.containsWhitespace(psiPrefix)) {
            return null;
        }
        if (wordPrefix == null) {
            wordPrefix = document.getText(new TextRange(CompletionUtils.getWordStartOffset(document, completionOffset), completionOffset));
        }
        return wordPrefix;
    }

    private static String getAccurateFilterText(@NotNull CompletionItem item) {
        String filterText = item.getFilterText();
        if (StringUtils.isBlank(filterText)) {
            return null;
        }
        String label = item.getLabel();
        if (label.startsWith(filterText)) {
            // The label starts with filterText, ignore the filter to avoid computing a bad prefix
            return null;
        }
        return filterText;
    }

}

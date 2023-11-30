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
package com.redhat.devtools.intellij.lsp4ij.operations.completion;

import com.intellij.openapi.editor.Document;
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import com.redhat.devtools.intellij.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.redhat.devtools.intellij.lsp4ij.operations.completion.CompletionProposalTools.getCompletionPrefix;

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

    public CompletionPrefix(int completionOffset, Document document) {
        this.completionOffset = completionOffset;
        this.document = document;
        this.completionPos = LSPIJUtils.toPosition(completionOffset, document);
        this.prefixCache = new HashMap<>();
    }

    public int getCompletionOffset() {
        return completionOffset;
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
    public @Nullable String getPrefixFor(@NotNull Range textEditRange, CompletionItem item) {
        // Try to get the computed prefix from the cache
        String prefix = prefixCache.get(textEditRange);
        if (prefix == null && !prefixCache.containsKey(textEditRange)) {
            // Compute the prefix which can be null
            // ex : {#ea|ch will return {#ea
            prefix = getCompletionPrefix(completionPos, textEditRange, document);
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

    private static String getAccurateFilterText(CompletionItem item) {
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

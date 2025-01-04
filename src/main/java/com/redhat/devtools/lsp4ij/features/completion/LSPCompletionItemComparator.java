/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.completion.PrefixMatcher;
import org.eclipse.lsp4j.CompletionItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Compares LSP {@link CompletionItem}s by their sortText property (falls back to comparing labels)
 */
public class LSPCompletionItemComparator extends AbstractCompletionItemComparator<CompletionItem> {

    public LSPCompletionItemComparator(@Nullable PrefixMatcher prefixMatcher,
                                       @Nullable String currentWord,
                                       boolean caseSensitive) {
        super(prefixMatcher, currentWord, caseSensitive);
    }

    @Override
    protected String getLabel(@NotNull CompletionItem item) {
        return item.getLabel();
    }

    @Override
    protected String getSortText(@NotNull CompletionItem item) {
        return item.getSortText();
    }
}
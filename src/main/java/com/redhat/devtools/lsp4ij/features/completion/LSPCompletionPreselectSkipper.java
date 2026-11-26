/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionPreselectSkipper;
import com.intellij.codeInsight.lookup.LookupElement;
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionProposal;
import org.jetbrains.annotations.NotNull;

/**
 * Makes the lookup select the completion item that the language server preselects
 * ({@code CompletionItem.preselect}).
 *
 * <p>
 * The lookup selects the first item that isn't skipped, so the LSP items of a completion result which aren't
 * preselected are skipped when another item of the same result is. The order of the items is kept, as the LSP
 * specification asks: the client selects the item, it doesn't reorder the list.
 * </p>
 */
public class LSPCompletionPreselectSkipper extends CompletionPreselectSkipper {

    @Override
    public boolean skipElement(@NotNull LookupElement element, @NotNull CompletionLocation location) {
        return element.getObject() instanceof LSPCompletionProposal proposal && proposal.isPreselectSkipped();
    }
}

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionPreselectSkipper;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionProposal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Makes the lookup select the completion item that the language server preselects
 * ({@code CompletionItem.preselect}).
 *
 * <p>
 * The lookup selects the first item that isn't skipped, so the LSP items of a completion result which aren't
 * preselected are skipped when another item of the same result is. The order of the items is kept, as the LSP
 * specification asks: the client selects the item, it doesn't reorder the list.
 * </p>
 *
 * <p>
 * The items of the other completion contributors are skipped too, once a language server preselects an item of the
 * completion. The lookup asks the skippers once per item, when it is added, and selects the first item when it skips
 * them all. So when the typed text filters out the preselected item, the first item is selected, as when no item is
 * preselected, and not the first item of another contributor, which comes after the LSP items.
 * </p>
 */
public class LSPCompletionPreselectSkipper extends CompletionPreselectSkipper {

    // Set on the completion process once a language server preselects one of its items
    private static final Key<Boolean> PRESELECT = Key.create("lsp4ij.completion.preselect");

    /**
     * Skips the items of the other completion contributors in the completion of the given parameters, whose language
     * server preselects an item.
     *
     * @param parameters the completion parameters.
     */
    static void preselectIn(@NotNull CompletionParameters parameters) {
        if (parameters.getProcess() instanceof UserDataHolder process) {
            process.putUserData(PRESELECT, Boolean.TRUE);
        }
    }

    @Override
    public boolean skipElement(@NotNull LookupElement element, @Nullable CompletionLocation location) {
        if (element.getObject() instanceof LSPCompletionProposal proposal) {
            return proposal.isPreselectSkipped();
        }
        return location != null
                && location.getCompletionParameters().getProcess() instanceof UserDataHolder process
                && Boolean.TRUE.equals(process.getUserData(PRESELECT));
    }
}

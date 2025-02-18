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

package com.redhat.devtools.lsp4ij.usages;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewShortNameLocation;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider.LSPSemanticTokenDocumentationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ElementDescriptionProvider} implementation used to show "LSP Symbol" as target in the "Find Usages" view.
 */
public class LSPUsageElementDescriptionProvider implements ElementDescriptionProvider {
    @Override
    @Nullable
    @NlsSafe
    public String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location) {
        if (element instanceof LSPUsageTriggeredPsiElement) {
            return LanguageServerBundle.message("usage.description");
        }
        // If this is for Ctrl/Cmd+Mouse hover, try to get the element description from the documentation provider
        else if (location instanceof UsageViewShortNameLocation) {
            return LSPSemanticTokenDocumentationProvider.getElementDescription(element, null);
        }

        return null;
    }
}

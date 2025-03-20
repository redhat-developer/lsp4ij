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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.usageView.UsageViewLongNameLocation;
import com.intellij.usageView.UsageViewShortNameLocation;
import com.intellij.usageView.UsageViewTypeLocation;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider.LSPSemanticTokenDocumentationProvider;
import com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider.LSPSemanticTokenPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ElementDescriptionProvider} implementation used to show information about the target in the "Find Usages" view.
 */
public class LSPUsageElementDescriptionProvider implements ElementDescriptionProvider {
    @Override
    @Nullable
    @NlsSafe
    public String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location) {
        // Only for LSP4IJ-supported files
        PsiFile file = element.getContainingFile();
        if ((file == null) || !file.isValid() || !LSPFileSupport.hasSupport(file)) {
            return null;
        }

        // Try to use the real element if possible
        if (element instanceof LSPUsageTriggeredPsiElement usageTriggeredPsiElement) {
            PsiElement realElement = usageTriggeredPsiElement.getRealElement();
            if (realElement != null) {
                element = realElement;
            }
        }

        // If it's not one of ours, don't provide a description
        if (!(element instanceof LSPPsiElement)) {
            return null;
        }

        // Usage declaration type
        if (location instanceof UsageViewTypeLocation) {
            // See if we can get a better type
            if (element instanceof LSPSemanticTokenPsiElement semanticTokenElement) {
                String tokenType = semanticTokenElement.getType();
                if (StringUtil.isNotEmpty(tokenType)) {
                    return StringUtil.join(NameUtil.splitNameIntoWords(tokenType), String::toLowerCase, " ");
                }
            }

            // If we couldn't derive a type, just use a placeholder
            return LanguageServerBundle.message("usage.description");
        }

        // Usage declaration name
        else if (location instanceof UsageViewLongNameLocation) {
            // See if we can get a better description
            if (element instanceof LSPSemanticTokenPsiElement semanticTokenElement) {
                return semanticTokenElement.getName();
            }

            // Otherwise try to get the current word
            Document document = LSPIJUtils.getDocument(element);
            TextRange wordRange = document != null ? LSPIJUtils.getWordRangeAt(document, file, element.getTextOffset()) : null;
            if (wordRange != null) {
                return document.getText(wordRange);
            }

            // Otherwise just use a placeholder
            return LanguageServerBundle.message("usage.description");
        }

        // If this is for Ctrl/Cmd+Mouse hover, try to get the element documentation from the documentation provider
        else if (location instanceof UsageViewShortNameLocation) {
            return LSPSemanticTokenDocumentationProvider.getDocumentation(element);
        }

        return null;
    }
}

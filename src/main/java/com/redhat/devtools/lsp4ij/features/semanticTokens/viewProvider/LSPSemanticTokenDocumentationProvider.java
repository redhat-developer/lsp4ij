/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Documentation provider for {@link LSPSemanticTokensFileViewProvider} files that provides quick navigation info
 * based on information from the semantic token.
 */
public class LSPSemanticTokenDocumentationProvider extends AbstractDocumentationProvider {
    @Override
    @Nullable
    @Nls
    public String getQuickNavigateInfo(PsiElement targetElement,
                                       PsiElement sourceElement) {
        String elementDescription = getElementDescription(targetElement, sourceElement);
        return elementDescription != null ? elementDescription : super.getQuickNavigateInfo(targetElement, sourceElement);
    }

    @Nullable
    @ApiStatus.Internal
    public static String getElementDescription(@Nullable PsiElement targetElement,
                                               @Nullable PsiElement sourceElement) {
        if (targetElement instanceof LSPPsiElement lspElement) {
            PsiFile targetFile = targetElement.getContainingFile();

            // Try to get an element description for the semantic token if present
            if (targetFile.getViewProvider() instanceof LSPSemanticTokensFileViewProvider semanticTokensFileViewProvider) {
                String elementDescription = semanticTokensFileViewProvider.getElementDescription(targetElement, sourceElement);
                if (StringUtil.isNotEmpty(elementDescription)) {
                    return elementDescription;
                }
            }

            // If that's not possible, create one from the LSP element's name
            String elementName = lspElement.getName();
            if (StringUtil.isNotEmpty(elementName)) {
                return "<html><code>" + elementName + "</code>" +
                        // If the source and target are in different files, include the target file name
                        ((sourceElement != null) && !Objects.equals(targetFile, sourceElement.getContainingFile()) ? " in <code>" + targetFile.getName() + "</code>" : "") +
                        "</html>";
            }
        }

        return null;
    }
}

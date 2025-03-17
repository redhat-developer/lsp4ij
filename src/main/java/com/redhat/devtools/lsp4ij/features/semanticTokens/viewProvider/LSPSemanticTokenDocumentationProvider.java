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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.features.documentation.LSPDocumentationTarget;
import com.redhat.devtools.lsp4ij.features.documentation.LSPDocumentationTargetProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Documentation provider for {@link LSPSemanticTokensFileViewProvider} files that provides quick navigation info
 * based on the LSP documentation for a semantic token.
 */
public class LSPSemanticTokenDocumentationProvider extends AbstractDocumentationProvider {

    private static final LSPDocumentationTargetProvider LSP_DOCUMENTATION_TARGET_PROVIDER = new LSPDocumentationTargetProvider();

    @Override
    @Nullable
    @Nls
    public String getQuickNavigateInfo(PsiElement targetElement,
                                       PsiElement sourceElement) {
        String documentation = getDocumentation(sourceElement, targetElement);
        return documentation != null ? documentation : super.getQuickNavigateInfo(targetElement, sourceElement);
    }

    /**
     * Provides external access to documentation for other parts of LSP4IJ, e.g.,
     * {@link com.redhat.devtools.lsp4ij.usages.LSPUsageElementDescriptionProvider}.
     *
     * @param element the element for which documentation is requested
     * @return documentation for the element, or null if none could be found
     */
    @Nullable
    @ApiStatus.Internal
    public static String getDocumentation(@Nullable PsiElement element) {
        return getDocumentation(element, element);
    }

    @Nullable
    private static String getDocumentation(@Nullable PsiElement sourceElement,
                                           @Nullable PsiElement targetElement) {
        LSPSemanticTokensFileViewProvider semanticTokensFileViewProvider = LSPSemanticTokensFileViewProvider.getInstance(sourceElement);
        if (semanticTokensFileViewProvider != null) {
            int offset = sourceElement.getTextOffset();
            ThreeState isIdentifier = semanticTokensFileViewProvider.isIdentifier(offset);
            if ((isIdentifier == ThreeState.YES) ||
                // If the token is definitely not an identifier and it's not whitespace, give the benefit of the doubt
                ((isIdentifier == ThreeState.NO) && !semanticTokensFileViewProvider.isWhitespace(offset))) {
                // Try to get the actual documentation for the element via LSP
                try {
                    PsiFile file = sourceElement.getContainingFile();
                    List<LSPDocumentationTarget> lspDocumentationTargets = LSP_DOCUMENTATION_TARGET_PROVIDER
                            .documentationTargets(file, offset)
                            .stream()
                            .filter((Predicate<DocumentationTarget>) dt -> dt instanceof LSPDocumentationTarget)
                            .map((Function<DocumentationTarget, LSPDocumentationTarget>) dt -> (LSPDocumentationTarget) dt)
                            .toList();
                    if (!ContainerUtil.isEmpty(lspDocumentationTargets)) {
                        String documentation = StringUtil.join(lspDocumentationTargets, LSPDocumentationTarget::getHtml, "\n").trim();
                        if (StringUtil.isNotEmpty(documentation)) {
                            return documentation;
                        }
                    }
                } catch (ProcessCanceledException pce) {
                    // Larger documentation can time out, so just degrade gracefully to fallback documentation
                }
            }
        }

        return getFallbackDocumentation(sourceElement, targetElement);
    }

    @Nullable
    private static String getFallbackDocumentation(@Nullable PsiElement sourceElement,
                                                   @Nullable PsiElement targetElement) {
        // Minimally try to include the element's name
        if (targetElement instanceof PsiNamedElement namedElement) {
            String elementName = namedElement.getName();
            if (StringUtil.isNotEmpty(elementName)) {
                // If the source and target are in different files, include the target file name
                PsiFile targetFile = namedElement.getContainingFile();
                return "<html><code>" + elementName + "</code>" +
                       // If the source and target are in different files, include the target file name
                       ((targetFile != null) && (sourceElement != null) && !Objects.equals(targetFile, sourceElement.getContainingFile()) ?
                               " in <code>" + targetFile.getName() + "</code>" :
                               "") +
                       "</html>";
            }
        }

        return null;
    }
}

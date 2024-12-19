/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.Processor;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Utility class that helps to process/find external references to LSP4IJ-based (pseudo-)elements.
 */
public final class LSPExternalReferencesFinder {

    private LSPExternalReferencesFinder() {
        // Pure utility class
    }

    /**
     * Processes all external references for the LSP4IJ element at the offset in the specified file.
     *
     * @param file        the file for which the element at the specified offset should be processed for external references
     * @param offset      the offset of the element in the file
     * @param processor   the external reference processor
     */
    public static void processExternalReferences(@NotNull PsiFile file,
                                                 int offset,
                                                 @NotNull Processor<PsiReference> processor) {
        processExternalReferences(file, offset, file.getUseScope(), processor);
    }

    /**
     * Processes all external references for the LSP4IJ element at the offset in the specified file.
     *
     * @param file        the file for which the element at the specified offset should be processed for external references
     * @param offset      the offset of the element in the file
     * @param searchScope the search scope
     * @param processor   the external reference processor
     */
    public static void processExternalReferences(@NotNull PsiFile file,
                                                 int offset,
                                                 @NotNull SearchScope searchScope,
                                                 @NotNull Processor<PsiReference> processor) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
            Document document = LSPIJUtils.getDocument(virtualFile);
            TextRange wordTextRange = document != null ? LSPIJUtils.getWordRangeAt(document, file, offset) : null;
            if (wordTextRange != null) {
                LSPPsiElement wordElement = new LSPPsiElement(file, wordTextRange);
                String wordText = wordElement.getText();
                if (StringUtil.isNotEmpty(wordText)) {
                    processExternalReferences(
                            file,
                            wordText,
                            wordTextRange,
                            searchScope,
                            ProgressManager.getInstance().getProgressIndicator(),
                            processor
                    );
                }
            }
        }
    }

    private static void processExternalReferences(@NotNull PsiFile file,
                                                  @NotNull String wordText,
                                                  @NotNull TextRange wordTextRange,
                                                  @NotNull SearchScope searchScope,
                                                  @Nullable ProgressIndicator progressIndicator,
                                                  @NotNull Processor<PsiReference> processor) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
            return;
        }

        // Determine whether or not to search/match in a case-sensitive manner based on client configuration
        Project project = file.getProject();
        boolean caseSensitive = LanguageServiceAccessor.getInstance(project)
                .hasAny(file.getVirtualFile(), ls -> ls.getClientFeatures().isCaseSensitive(file));

        if (progressIndicator != null) {
            progressIndicator.setText("Finding usages of '" + wordText + "'");
        }

        Set<String> externalReferenceKeys = new HashSet<>();
        PsiSearchHelper.getInstance(project).processElementsWithWord(
                (element, offsetInElement) -> {
                    PsiReference originalReference = element.findReferenceAt(offsetInElement);
                    List<PsiReference> references = originalReference != null ?
                            PsiReferenceUtil.unwrapMultiReference(originalReference) :
                            Collections.emptyList();
                    for (PsiReference reference : references) {
                        // Deduplicate using a unique key with reference type, file, and text range
                        String referenceKey = getReferenceKey(reference);
                        if (referenceKey != null) {
                            // Only add references we haven't added previously
                            if (!externalReferenceKeys.contains(referenceKey)) {
                                PsiElement targetElement = reference.resolve();
                                PsiFile targetFile = targetElement != null ? targetElement.getContainingFile() : null;
                                // Files match
                                if ((targetFile != null) && Objects.equals(file, targetFile)) {
                                    // Text ranges match
                                    TextRange targetTextRange = targetElement.getTextRange();
                                    if ((targetTextRange != null) && Objects.equals(wordTextRange, targetTextRange)) {
                                        // Text matches according to case-sensitivity
                                        String targetText = reference.getCanonicalText();
                                        if (caseSensitive ? wordText.equals(targetText) : wordText.equalsIgnoreCase(targetText)) {
                                            if (!processor.process(reference)) {
                                                return false;
                                            }
                                            externalReferenceKeys.add(referenceKey);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (progressIndicator != null) {
                        progressIndicator.checkCanceled();
                    }
                    return true;
                },
                searchScope,
                wordText,
                UsageSearchContext.ANY,
                caseSensitive
        );
    }

    @Nullable
    private static String getReferenceKey(@NotNull PsiReference reference) {
        PsiElement sourceElement = reference.getElement();
        PsiFile sourceFile = sourceElement.getContainingFile();
        VirtualFile sourceVirtualFile = sourceFile != null ? sourceFile.getVirtualFile() : null;
        if (sourceVirtualFile != null) {
            return reference.getClass().getName() + "::" + sourceVirtualFile.getPath() + "::" + reference.getAbsoluteRange();
        }
        return null;
    }
}

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

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.redhat.devtools.lsp4ij.usages.LSPFindUsagesHandlerFactory.isUsageSupportedByLanguageServer;

/**
 * LSP usage target provider.
 * <p>
 * This provider creates usage targets for LSP-managed files, enabling IntelliJ's Find Usages infrastructure
 * to discover symbols and their references via the language server. A usage target represents the element
 * at the caret position or the element to find usages for.
 * </p>
 * <p>
 * The provider acts as a bridge between IntelliJ's usage system and LSP protocol, converting the caret
 * position or PSI element into an {@link LSPUsageTriggeredPsiElement} that can be queried via LSP requests
 * (textDocument/definition, textDocument/references, etc.).
 * </p>
 * <p>
 * This provider only activates when:
 * <ul>
 *   <li>The language doesn't have a custom FindUsagesProvider (to avoid overriding native language support)</li>
 *   <li>Project indexing allows LSP features to execute</li>
 *   <li>A language server supports usage features for the element</li>
 * </ul>
 * </p>
 */
public class LSPUsageTargetProvider implements UsageTargetProvider {

    /**
     * Creates usage targets from the current editor position.
     * <p>
     * This method is called by IntelliJ's Find Usages action when invoked from the editor (e.g., via
     * keyboard shortcut or menu action). It determines the element at the caret position and creates
     * a usage target if the language server supports usage features for that element.
     * </p>
     * <p>
     * Returns {@code null} (delegating to other providers) when:
     * <ul>
     *   <li>The file's language has a custom FindUsagesProvider (e.g., Java, Kotlin) to avoid conflicts</li>
     *   <li>Project is currently indexing or scanning</li>
     *   <li>No language server supports usage features for the element at the caret</li>
     * </ul>
     * </p>
     *
     * @param editor the editor where Find Usages was invoked.
     * @param file the PSI file being edited.
     * @return an array containing a single LSP usage target, or {@code null} if LSP should not handle this request.
     */
    @Override
    public UsageTarget @Nullable [] getTargets(@NotNull Editor editor, @NotNull PsiFile file) {
        if (LanguageServersRegistry.getInstance().hasCustomLanguageFindUsages(file.getLanguage())) {
            // Defer to custom FindUsagesProvider (e.g., JavaFindUsagesHandlerFactory) to avoid conflicts
            return null;
        }
        if (ProjectIndexingManager.canExecuteLSPFeature(file) != ExecuteLSPFeatureStatus.NOW) {
            // Wait for indexing/scanning to complete before executing LSP features
            return null;
        }
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        if (!isUsageSupportedByLanguageServer(element)) {
            // No language server provides usage support for this element
            return null;
        }
        return getLSPTargets(editor, file);
    }

    /**
     * Creates usage targets from a specific PSI element.
     * <p>
     * This method is called when Find Usages is invoked on a specific PSI element (e.g., from a tree view
     * or programmatic invocation). It creates a usage target if the language server supports usage features
     * for the element.
     * </p>
     * <p>
     * Returns {@code null} (delegating to other providers) when:
     * <ul>
     *   <li>The element's language has a custom FindUsagesProvider (e.g., Java, Kotlin) to avoid conflicts</li>
     *   <li>No language server supports usage features for the element</li>
     *   <li>No editor can be found for the element (required to determine the word range)</li>
     * </ul>
     * </p>
     *
     * @param psiElement the PSI element to find usages for.
     * @return an array containing a single LSP usage target, or {@code null} if LSP should not handle this request.
     */
    @Override
    public UsageTarget @Nullable [] getTargets(@NotNull PsiElement psiElement) {
        if (LanguageServersRegistry.getInstance().hasCustomLanguageFindUsages(psiElement.getLanguage())) {
            // Defer to custom FindUsagesProvider (e.g., JavaFindUsagesHandlerFactory) to avoid conflicts
            return null;
        }
        if (!isUsageSupportedByLanguageServer(psiElement)) {
            // No language server provides usage support for this element
            return null;
        }
        PsiFile file = psiElement.getContainingFile();
        Editor editor = LSPIJUtils.editorForElement(psiElement);
        return editor != null ? getLSPTargets(editor, file) : null;
    }

    /**
     * Creates an LSP usage target from the word at the caret position.
     * <p>
     * This method determines the word boundary at the caret position and wraps it in an
     * {@link LSPUsageTriggeredPsiElement}. This pseudo-element will later be queried by
     * {@link LSPUsageSearcher} to collect usages via LSP protocol requests.
     * </p>
     * <p>
     * The word range is computed using {@link LSPIJUtils#getWordRangeAt}, which respects
     * language-specific word boundaries (identifiers, keywords, etc.).
     * </p>
     *
     * @param editor the editor containing the target.
     * @param file the PSI file being searched.
     * @return an array containing a single usage target, or an empty array if no word is found at the caret.
     */
    @NotNull
    private static UsageTarget[] getLSPTargets(@NotNull Editor editor, @NotNull PsiFile file) {
        TextRange targetTextRange = LSPIJUtils.getWordRangeAt(editor.getDocument(), file, editor.getCaretModel().getOffset());
        if (targetTextRange == null) {
            return UsageTarget.EMPTY_ARRAY;
        }
        LSPUsageTriggeredPsiElement triggeredElement = new LSPUsageTriggeredPsiElement(file, targetTextRange);
        // Pre-compute the element name from the text range for usage display
        triggeredElement.getName();
        UsageTarget target = new PsiElement2UsageTargetAdapter(triggeredElement, true);
        return new UsageTarget[]{target};
    }
}

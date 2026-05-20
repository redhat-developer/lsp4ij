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

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP find usages handler factory.
 * <p>
 * This factory provides "Find Usages" functionality for LSP-managed files by delegating to the language server.
 * It integrates with IntelliJ's Find Usages infrastructure to discover references, declarations, definitions,
 * implementations, and type definitions using LSP protocol requests.
 * </p>
 * <p>
 * The factory is registered as "last" in the extension point to avoid overriding language-specific
 * FindUsagesHandlerFactory implementations (e.g., JavaFindUsagesHandlerFactory). It only activates when:
 * <ul>
 *   <li>The file is associated with a language server</li>
 *   <li>The language server supports usage-related LSP features</li>
 *   <li>No other FindUsagesHandlerFactory already handles the element</li>
 *   <li>Project indexing allows LSP features to execute</li>
 * </ul>
 * </p>
 */
public class LSPFindUsagesHandlerFactory extends FindUsagesHandlerFactory {

    private static final Key<Boolean> CHECKING_PLUGIN_FACTORY_KEY = Key.create("lsp.find.usages.checking.plugin.factory");

    /**
     * Determines if this factory can provide Find Usages support for the given element.
     * <p>
     * This method returns {@code true} only when all the following conditions are met:
     * <ul>
     *   <li>Project indexing state allows LSP features to execute now (not during indexing/scanning)</li>
     *   <li>The element's file is associated with a language server that supports usage features</li>
     *   <li>No plugin-defined FindUsagesHandlerFactory already handles this element (to avoid conflicts)</li>
     * </ul>
     * </p>
     * <p>
     * The check for plugin-defined factories ensures LSP acts as a fallback and doesn't override
     * dedicated language plugins. For example, if a Java file is also managed by an LSP server,
     * JavaFindUsagesHandlerFactory (from the Java plugin) should take precedence since it provides
     * more comprehensive Java-specific functionality.
     * </p>
     *
     * @param element the PSI element to check.
     * @return {@code true} if this factory should handle Find Usages for the element, {@code false} otherwise.
     */
    @Override
    public boolean canFindUsages(@NotNull PsiElement element) {
        if (element.getUserData(CHECKING_PLUGIN_FACTORY_KEY) != null) {
            return false;
        }
        if (ProjectIndexingManager.canExecuteLSPFeature(element.getContainingFile()) == ExecuteLSPFeatureStatus.NOW
                && isUsageSupportedByLanguageServer(element)) {
            // Defer to plugin-defined factories (e.g., JavaFindUsagesHandlerFactory) to avoid conflicts.
            // Both this factory and language-specific factories may be registered as "last" priority.
            try {
                element.putUserData(CHECKING_PLUGIN_FACTORY_KEY, true);
                return !hasPluginDefinedFactory(element);
            } finally {
                element.putUserData(CHECKING_PLUGIN_FACTORY_KEY, null);
            }
        }
        return false;
    }

    /**
     * Checks if usage searching is supported by at least one language server for the given element.
     * <p>
     * Usage searching is supported when a language server provides at least one of the following LSP features:
     * <ul>
     *   <li>textDocument/declaration</li>
     *   <li>textDocument/typeDefinition</li>
     *   <li>textDocument/definition</li>
     *   <li>textDocument/references</li>
     *   <li>textDocument/implementation</li>
     * </ul>
     * and the specific element type is eligible for usage searching (e.g., not a comment, keyword, modifier, or operator
     * in the case of semantic token elements).
     * </p>
     *
     * @param element the PSI element to check, or {@code null}.
     * @return {@code true} if at least one language server supports usage features for this element, {@code false} otherwise.
     */
    public static boolean isUsageSupportedByLanguageServer(@Nullable PsiElement element) {
        if (element == null) {
            return false;
        }
        PsiFile file = element instanceof PsiFile ? (PsiFile) element : element.getContainingFile();
        if (file == null) {
            return false;
        }
        return LanguageServiceAccessor.getInstance(file.getProject())
                .hasAny(file, ls -> {
                    var usageFeature = ls.getClientFeatures().getUsageFeature();
                    return usageFeature.isSupported(file) && usageFeature.isUsageSupported(element);
                });
    }

    /**
     * Creates an LSP-based Find Usages handler for the given element.
     * <p>
     * The handler uses {@link LSPUsageSearcher} to query the language server for references, declarations,
     * definitions, implementations, and type definitions via LSP protocol.
     * </p>
     *
     * @param element            the PSI element to find usages for.
     * @param forHighlightUsages {@code true} if the handler is being created for highlighting usages in the editor,
     *                           {@code false} if for the Find Usages dialog.
     * @return a new {@link LSPFindUsagesHandler} instance.
     */
    @Override
    public @Nullable FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
        return new LSPFindUsagesHandler(element);
    }

    /**
     * Checks if a plugin has already defined a FindUsagesHandlerFactory that should be preferred for the given element.
     * <p>
     * This method iterates through all registered FindUsagesHandlerFactory extensions and checks if any
     * plugin-defined factory (excluding this LSP factory) can handle find usages for the element.
     * </p>
     * <p>
     * This ensures LSP acts as a fallback mechanism rather than overriding specialized plugin support.
     * Language-specific plugins typically provide richer functionality than generic LSP support.
     * For example:
     * <ul>
     *   <li>Java plugin's JavaFindUsagesHandlerFactory understands Java-specific concepts like overridden methods</li>
     *   <li>Kotlin plugin provides Kotlin-specific usage searching with better semantic understanding</li>
     *   <li>Python plugin handles dynamic features that LSP may not fully capture</li>
     * </ul>
     * By deferring to these plugin-defined factories, we ensure users get the best possible Find Usages experience.
     * </p>
     *
     * @param element the PSI element to check.
     * @return {@code true} if a plugin-defined factory should handle Find Usages for this element, {@code false} otherwise.
     */
    private boolean hasPluginDefinedFactory(@NotNull PsiElement element) {
        for (FindUsagesHandlerFactory factory : FindUsagesHandlerFactory.EP_NAME.getExtensions(element.getProject())) {
            try {
                if (!factory.equals(this) && factory.canFindUsages(element)) {
                    return true;
                }
            } catch (IndexNotReadyException | ProcessCanceledException e) {
                throw e;
            } catch (Exception e) {

            }
        }
        return false;
    }

}

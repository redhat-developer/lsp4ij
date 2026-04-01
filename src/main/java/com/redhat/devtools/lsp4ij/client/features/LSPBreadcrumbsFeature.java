/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.lang.Language;
import com.intellij.psi.PsiFile;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPBreadcrumbsProvider;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Breadcrumbs/sticky lines feature. This does not correspond directly to an LSP feature but is built upon
 * {@link LSPDocumentSymbolFeature}.
 */
@ApiStatus.Experimental
public class LSPBreadcrumbsFeature extends AbstractLSPDocumentFeature {

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
    }

    @Override
    public boolean isEnabled(@NotNull PsiFile file) {
        return isExistingBreadcrumbsProviderOverrideable(file) || !hasThirdPartyBreadcrumbsProvider(file);
    }

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        // Requires the document symbol feature
        return getClientFeatures().getDocumentSymbolFeature().isSupported(file);
    }

    /**
     * This specifies whether the language server should override a {@link BreadcrumbsProvider} registered for languages in a file.

     * @return true to use the language server for code bread crumbs  and false to use plugin-provided/built-in bread crumbs.
     * @apiNote This method will only be called with files that contain a language supported by this language server.
     */
    public boolean isExistingBreadcrumbsProviderOverrideable(@NotNull PsiFile file) {
        return false;
    }

    /**
     * Returns {@code true} if a {@link BreadcrumbsProvider} other than LSP4IJ's own
     * is registered and supports the language of the given file.
     */
    @SuppressWarnings("UnstableApiUsage")
    private static boolean hasThirdPartyBreadcrumbsProvider(@NotNull PsiFile file) {
        // Check all languages exposed by the file (handles multi-language files
        // such as HTML+JS or JSX).
        Set<Language> fileLanguages = file.getViewProvider().getLanguages();

        for (BreadcrumbsProvider provider : BreadcrumbsProvider.EP_NAME.getExtensionList()) {
            // Skip LSP4IJ's own provider to avoid self-detection.
            if (provider instanceof LSPBreadcrumbsProvider) {
                continue;
            }
            for (Language providerLanguage : provider.getLanguages()) {
                for (Language fileLanguage : fileLanguages) {
                    // Use isKindOf() to respect the language dialect hierarchy
                    // (e.g. TypeScript extends JavaScript).
                    if (fileLanguage.isKindOf(providerLanguage)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

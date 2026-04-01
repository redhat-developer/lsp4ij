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
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPBreadcrumbsProvider;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * Breadcrumbs/sticky lines feature. This does not correspond directly to an LSP feature but is built upon
 * {@link LSPDocumentSymbolFeature}.
 */
@ApiStatus.Experimental
public class LSPBreadcrumbsFeature extends AbstractLSPDocumentFeature {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPBreadcrumbsFeature.class);

    /**
     * The {@link BreadcrumbsProvider} extension point name, resolved once via reflection to avoid
     * using the {@code @Internal} {@link BreadcrumbsProvider#EP_NAME} field directly.
     * Falls back to {@link ExtensionPointName#create(String)} if reflection fails.
     */
    private static final ExtensionPointName<BreadcrumbsProvider> BREADCRUMBS_EP_NAME = resolveBreadcrumbsEPName();

    @SuppressWarnings("unchecked")
    private static ExtensionPointName<BreadcrumbsProvider> resolveBreadcrumbsEPName() {
        try {
            Field epNameField = BreadcrumbsProvider.class.getDeclaredField("EP_NAME");
            epNameField.setAccessible(true);
            return (ExtensionPointName<BreadcrumbsProvider>) epNameField.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Fallback: recreate the extension point by its string name
            LOGGER.warn("Unable to resolve BreadcrumbsProvider.EP_NAME via reflection, falling back to ExtensionPointName.create()", e);
            return ExtensionPointName.create("com.intellij.breadcrumbsInfoProvider");
        }
    }

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
     * Specifies whether the language server should override a {@link BreadcrumbsProvider} registered
     * for languages in a file.
     * <p>
     * Override this method and return {@code true} to always use the language server for breadcrumbs,
     * even if another plugin already provides breadcrumbs for the same language.
     *
     * @return {@code true} to use the language server for breadcrumbs and {@code false} to use
     * plugin-provided/built-in breadcrumbs.
     * @apiNote This method will only be called with files that contain a language supported by this language server.
     */
    public boolean isExistingBreadcrumbsProviderOverrideable(@NotNull PsiFile file) {
        return false;
    }

    /**
     * Returns {@code true} if a {@link BreadcrumbsProvider} other than LSP4IJ's own
     * is registered and supports at least one of the languages of the given file.
     * <p>
     * Multi-language files (e.g. HTML+JS, JSX) are handled by checking all languages
     * exposed by the file's {@link com.intellij.psi.FileViewProvider}.
     */
    private static boolean hasThirdPartyBreadcrumbsProvider(@NotNull PsiFile file) {
        if (BREADCRUMBS_EP_NAME == null) {
            return false;
        }
        // Check all languages exposed by the file (handles multi-language files such as HTML+JS or JSX)
        Set<Language> fileLanguages = file.getViewProvider().getLanguages();
        for (BreadcrumbsProvider provider : BREADCRUMBS_EP_NAME.getExtensionList()) {
            // Skip LSP4IJ's own provider to avoid self-detection
            if (provider instanceof LSPBreadcrumbsProvider) {
                continue;
            }
            for (Language providerLanguage : provider.getLanguages()) {
                for (Language fileLanguage : fileLanguages) {
                    // Use isKindOf() to respect the language dialect hierarchy
                    // (e.g. TypeScript extends JavaScript)
                    if (fileLanguage.isKindOf(providerLanguage)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
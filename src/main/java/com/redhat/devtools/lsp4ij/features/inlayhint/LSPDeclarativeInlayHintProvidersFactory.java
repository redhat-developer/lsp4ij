/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * FalsePattern - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.inlayhint;


import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory;
import com.intellij.codeInsight.hints.declarative.InlayProviderInfo;
import com.intellij.lang.Language;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link InlayHintsProviderFactory inlay hint factory} implementation
 * to register all languages mapped with a language server with {@link LSPInlayHintsProvider}
 * to avoid for the external plugin to declare in plugin.xml the 'codeInsight.inlayProvider'.
 */
public class LSPDeclarativeInlayHintProvidersFactory implements InlayHintsProviderFactory {
    @Override
    public @Nullable InlayProviderInfo getProviderInfo(@NotNull Language language, @NotNull String providerId) {
        return getProvidersForLanguage(language)
                .stream()
                .filter(info -> info.getProviderId().equals(providerId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public @NotNull List<InlayProviderInfo> getProvidersForLanguage(@NotNull Language language) {
        return LanguageServersRegistry.getInstance().getDeclarativeInlayHintProviderInfos().getOrDefault(language.getID(), Collections.emptyList());
    }

    @Override
    public @NotNull Set<Language> getSupportedLanguages() {
        return LanguageServersRegistry.getInstance().getDeclarativeInlayHintProviderInfos().keySet().stream().map(Language::findLanguageByID).collect(Collectors.toSet());
    }
}

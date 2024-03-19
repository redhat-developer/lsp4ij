/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features;

import com.intellij.codeInsight.hints.InlayHintsProviderFactory;
import com.intellij.codeInsight.hints.ProviderInfo;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.features.inlayhint.LSPInlayHintsProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@link InlayHintsProviderFactory inlay hint factory} implementation
 * to register all languages mapped with a language server with {@link LSPInlayHintsProvider} and {@link DeprecatedLSPCodeLensProvider}
 * to avoid for the external plugin to declare in plugin.xml the 'codeInsight.inlayProvider'.
 */
public class LSPInlayHintProvidersFactory implements InlayHintsProviderFactory {
    @NotNull
    @Override
    public List<ProviderInfo<? extends Object>> getProvidersInfo() {
        return LanguageServersRegistry.getInstance().getInlayHintProviderInfos();
    }

    @NotNull
    @Override
    public List<ProviderInfo<? extends Object>> getProvidersInfo(@NotNull Project project) {
        return getProvidersInfo();
    }
}

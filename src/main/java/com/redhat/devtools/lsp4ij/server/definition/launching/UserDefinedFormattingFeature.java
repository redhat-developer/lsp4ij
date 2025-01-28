/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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

package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings.ClientConfigurationOnTypeFormattingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adds client-side formatting configuration features.
 */
public class UserDefinedFormattingFeature extends LSPFormattingFeature {

    @Nullable
    private ClientConfigurationOnTypeFormattingSettings getOnTypeFormattingSettings() {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getClientFeatures().getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration != null ? clientConfiguration.onTypeFormatting : null;
    }

    @Override
    public boolean isOnTypeFormattingEnabled(@NotNull PsiFile file) {
        ClientConfigurationOnTypeFormattingSettings onTypeFormattingSettings = getOnTypeFormattingSettings();
        return onTypeFormattingSettings != null ? onTypeFormattingSettings.enabled : super.isOnTypeFormattingEnabled(file);
    }
}
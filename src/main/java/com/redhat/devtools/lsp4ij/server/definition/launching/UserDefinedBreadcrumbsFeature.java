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

package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.LSPBreadcrumbsFeature;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings.ClientConfigurationBreadcrumbsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User-defined breadcrumbs feature.
 */
public class UserDefinedBreadcrumbsFeature extends LSPBreadcrumbsFeature {

    @Nullable
    private ClientConfigurationBreadcrumbsSettings getSettings() {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getClientFeatures().getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration != null ? clientConfiguration.breadcrumbs : null;
    }

    @Override
    public boolean isEnabled(@NotNull PsiFile file) {
        ClientConfigurationBreadcrumbsSettings breadcrumbsSettings = getSettings();
        // Note that this defaults to enabled for user-defined language server definitions
        return (breadcrumbsSettings == null) || breadcrumbsSettings.enabled;
    }
}

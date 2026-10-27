/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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

import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.ConfigurableWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Workspace folder feature for user-defined language servers.
 * Uses configurable strategy by default, loading configuration from settings.
 */
public class UserDefinedWorkspaceFolderFeature extends LSPWorkspaceFolderFeature {

    @NotNull
    @Override
    protected WorkspaceFolderStrategy createStrategy() {
        ConfigurableWorkspaceFolderStrategy strategy = new ConfigurableWorkspaceFolderStrategy();

        // Load workspace folder configuration from settings
        var serverDefinition = getClientFeatures().getServerDefinition();
        var settings = UserDefinedLanguageServerSettings.getInstance()
                .getUserDefinedLanguageServerSettings(serverDefinition.getId());
        if (settings != null) {
            String config = settings.getWorkspaceFolderStrategyConfiguration();
            if (config != null && !config.trim().isEmpty()) {
                strategy.configure(config);
            }
        }

        return strategy;
    }

    @Override
    public void reset() {
        strategy = null;
        super.reset();
    }
}

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

import com.intellij.openapi.progress.ProgressIndicator;
import com.redhat.devtools.lsp4ij.installation.DeclarativeLanguageServerInstaller;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.launching.ui.UICommandLineUpdater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User defined language server installer.
 */
public class UserDefinedLanguageServerInstaller extends DeclarativeLanguageServerInstaller {

    @Override
    protected @Nullable ServerInstallerDescriptor getServerInstallerDescriptor() {
        var serverDefinition = getUserDefinedLanguageServerDefinition();
        return serverDefinition != null ? serverDefinition.getServerInstallerDescriptor() : null;
    }

    @Override
    protected @NotNull InstallerContext createInstallerContext(InstallerContext.@NotNull InstallerAction action,
                                                               @NotNull ProgressIndicator indicator) {
        var context = super.createInstallerContext(action, indicator);
        var serverDefinition = getUserDefinedLanguageServerDefinition();
        if (serverDefinition != null) {
            context.setCommandLineUpdater(new UICommandLineUpdater(serverDefinition, getProject()));
        }
        return context;
    }

    private @Nullable UserDefinedLanguageServerDefinition getUserDefinedLanguageServerDefinition() {
        var serverDefinition = getClientFeatures().getServerDefinition();
        if (serverDefinition instanceof UserDefinedLanguageServerDefinition ls) {
            return ls;
        }
        return null;
    }

    @Override
    protected boolean canExecute(@NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        if(super.canExecute(serverInstallerDescriptor)) {
            return true;
        }
        var serverDefinition = getUserDefinedLanguageServerDefinition();
        if (serverDefinition == null) {
            return true;
        }
        String languageServerId = serverDefinition.getId();
        UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings = UserDefinedLanguageServerSettings.getInstance().getLaunchConfigSettings(languageServerId);
        if (settings != null) {
            boolean canExecute = !settings.isInstallAlreadyDone();
            if (canExecute) {
                settings.setInstallAlreadyDone(true);
            }
            return canExecute;
        }
        return false;
    }
}

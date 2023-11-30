/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
package com.redhat.devtools.intellij.lsp4ij.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.redhat.devtools.intellij.lsp4ij.LanguageServersRegistry;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * UI settings to configure a given language server:
 *
 * <ul>
 *     <li>Debug port</li>
 *     <li>Suspend and wait for a debugger</li>
 * </ul>
 */
public class LanguageServerConfigurable extends NamedConfigurable<LanguageServersRegistry.LanguageServerDefinition> {

    private final LanguageServersRegistry.LanguageServerDefinition languageServerDefinition;
    private final Project project;

    private LanguageServerView myView;

    public LanguageServerConfigurable(LanguageServersRegistry.LanguageServerDefinition languageServerDefinition, Runnable updater, Project project) {
        super(false, updater);
        this.languageServerDefinition = languageServerDefinition;
        this.project = project;
    }

    @Override
    public void setDisplayName(String name) {
        // Do nothing: the language server name is nt editable.
    }

    @Override
    public LanguageServersRegistry.LanguageServerDefinition getEditableObject() {
        return languageServerDefinition;
    }

    @Override
    public @NlsContexts.DetailedDescription String getBannerSlogan() {
        return languageServerDefinition.getDisplayName();
    }

    @Override
    public JComponent createOptionsPanel() {
        if (myView == null) {
            myView = new LanguageServerView(languageServerDefinition);
        }
        return myView.getComponent();
    }

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return languageServerDefinition.getDisplayName();
    }

    @Override
    public @Nullable Icon getIcon(boolean expanded) {
        String serverId = languageServerDefinition.id;
        return LanguageServersRegistry.getInstance().getServerIcon(serverId);
    }

    @Override
    public boolean isModified() {
        UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = UserDefinedLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(languageServerDefinition.id);
        if (settings == null) {
            return true;
        }
        return !(myView.getDebugPort().equals(settings.getDebugPort())
                && myView.isDebugSuspend() == settings.isDebugSuspend()
                && myView.getServerTrace() == settings.getServerTrace());
    }

    @Override
    public void apply() throws ConfigurationException {
        UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = new UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings();
        settings.setDebugPort(myView.getDebugPort());
        settings.setDebugSuspend(myView.isDebugSuspend());
        settings.setServerTrace(myView.getServerTrace());
        UserDefinedLanguageServerSettings.getInstance(project).setLanguageServerSettings(languageServerDefinition.id, settings);
    }

    @Override
    public void reset() {
        ServerTrace serverTrace = ServerTrace.off;
        UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = UserDefinedLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(languageServerDefinition.id);
        if (settings != null) {
            myView.setDebugPort(settings.getDebugPort());
            myView.setDebugSuspend(settings.isDebugSuspend());
            if (settings.getServerTrace() != null) {
                serverTrace = settings.getServerTrace();
            }
        }
        myView.setServerTrace(serverTrace);
    }

    @Override
    public void disposeUIResources() {
        if (myView != null) Disposer.dispose(myView);
    }
}

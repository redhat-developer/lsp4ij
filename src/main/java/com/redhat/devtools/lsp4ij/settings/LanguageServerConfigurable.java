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
package com.redhat.devtools.lsp4ij.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
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
public class LanguageServerConfigurable extends NamedConfigurable<LanguageServerDefinition> implements LanguageServerView.LanguageServerNameProvider {

    private final LanguageServerDefinition languageServerDefinition;
    private final Project project;

    private LanguageServerView myView;

    public LanguageServerConfigurable(LanguageServerDefinition languageServerDefinition, Runnable updater, Project project) {
        super(languageServerDefinition instanceof UserDefinedLanguageServerDefinition, updater);
        this.languageServerDefinition = languageServerDefinition;
        this.project = project;
    }

    @Override
    public void setDisplayName(String name) {
        // Do nothing: the language server name is not editable.
        if (languageServerDefinition instanceof UserDefinedLanguageServerDefinition launchConfiguration) {
            launchConfiguration.setName(name);
        }
    }

    @Override
    public LanguageServerDefinition getEditableObject() {
        return languageServerDefinition;
    }

    @Override
    public @NlsContexts.DetailedDescription String getBannerSlogan() {
        return languageServerDefinition.getDisplayName();
    }

    @Override
    public JComponent createOptionsPanel() {
        if (myView == null) {
            myView = new LanguageServerView(languageServerDefinition, this, project);
        }
        return myView.getComponent();
    }

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return languageServerDefinition.getDisplayName();
    }

    @Override
    public @Nullable Icon getIcon(boolean expanded) {
        return languageServerDefinition.getIcon();
    }

    @Override
    public boolean isModified() {
        return myView.isModified();
    }

    @Override
    public void apply() throws ConfigurationException {
        myView.apply();
    }

    @Override
    public void reset() {
        myView.reset();
    }

    @Override
    public void disposeUIResources() {
        if (myView != null) Disposer.dispose(myView);
    }
}

/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.ui.refactoring;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Language Server "Refactoring" preference page to configure:
 *
 * <ul>
 *     <li>the "On file create|delete|rename" kind</li>
 * </ul>
 */
public class LanguageServerRefactoringConfigurable extends NamedConfigurable<UserDefinedLanguageServerSettings>  {

    @NotNull
    private final Project project;
    private LanguageServerRefactoringView myView;

    public LanguageServerRefactoringConfigurable(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void setDisplayName(@NlsSafe String name) {

    }

    @Override
    public UserDefinedLanguageServerSettings getEditableObject() {
        return UserDefinedLanguageServerSettings.getInstance(project);
    }

    @Override
    public @NlsContexts.DetailedDescription String getBannerSlogan() {
        return null;
    }

    @Override
    public JComponent createOptionsPanel() {
        if (myView == null) {
            myView = new LanguageServerRefactoringView();
        }
        return myView.getComponent();
    }

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return LanguageServerBundle.message("language.servers.refactoring");
    }

    @Override
    public boolean isModified() {
        if (myView == null) return false;
        var settings = getEditableObject();
        return myView.getRefactoringOnFileOperationsKind() != settings.getRefactoringOnFileOperationsKind();
    }

    @Override
    public void apply() throws ConfigurationException {
        if (myView == null) return;
        var settings = getEditableObject();
        settings.setRefactoringOnFileOperationsKind(myView.getRefactoringOnFileOperationsKind());
    }

    @Override
    public void reset() {
        if (myView == null) return;
        var settings = getEditableObject();
        myView.setRefactoringOnFileOperationsKind(settings.getRefactoringOnFileOperationsKind());
    }
}

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

import com.intellij.openapi.Disposable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.ui.LanguageServerPanel;
import com.redhat.devtools.lsp4ij.settings.ui.ServerMappingsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.util.List;

/**
 * UI settings view to configure a given language server:
 *
 * <ul>
 *     <li>Debug port</li>
 *     <li>Suspend and wait for a debugger?</li>
 * </ul>
 */
public class LanguageServerView implements Disposable {

    private final JPanel myMainPanel;

    private LanguageServerPanel languageServerPanel;

    private ServerMappingsPanel mappingPanel;

    public LanguageServerView(LanguageServerDefinition languageServerDefinition) {
        boolean isLaunchConfiguration = languageServerDefinition instanceof UserDefinedLanguageServerDefinition;
        JComponent descriptionPanel = createDescription(languageServerDefinition.description.trim());
        JPanel settingsPanel = createSettings(descriptionPanel, isLaunchConfiguration);
        if (!isLaunchConfiguration) {
            TitledBorder title = IdeBorderFactory.createTitledBorder(languageServerDefinition.getDisplayName());
            settingsPanel.setBorder(title);
        }
        JPanel wrapper = JBUI.Panels.simplePanel(settingsPanel);
        wrapper.setBorder(JBUI.Borders.emptyLeft(10));
        this.myMainPanel = wrapper;
    }

    private JPanel createSettings(JComponent description, boolean launchingServerDefinition) {
        FormBuilder builder = FormBuilder.createFormBuilder()
                .setFormLeftIndent(10);
        this.languageServerPanel = new LanguageServerPanel(builder,
                description,
                launchingServerDefinition ? LanguageServerPanel.EditionMode.EDIT_USER_DEFINED :
                        LanguageServerPanel.EditionMode.EDIT_EXTENSION);
        this.mappingPanel = languageServerPanel.getMappingsPanel();
        return builder
                .addComponentFillVertically(new JPanel(), 50)
                .getPanel();
    }

    private JComponent createDescription(String description) {
        /**
         * Normally comments are below the controls.
         * Here we want the comments to precede the controls, we therefore create an empty, 0-sized panel.
         */
        JPanel titledComponent = UI.PanelFactory.grid().createPanel();
        titledComponent.setMinimumSize(JBUI.emptySize());
        titledComponent.setPreferredSize(JBUI.emptySize());
        if (description != null && !description.isBlank()) {
            titledComponent = UI.PanelFactory.panel(titledComponent)
                    .withComment(description)
                    .resizeX(true)
                    .resizeY(true)
                    .createPanel();
        }
        return titledComponent;
    }

    public JComponent getComponent() {
        return myMainPanel;
    }

    public String getDebugPort() {
        var debugPortField = languageServerPanel.getDebugPortField();
        return debugPortField.getNumber() <= 0 ? "" : Integer.toString(debugPortField.getNumber());
    }

    public void setDebugPort(String debugPort) {
        var debugPortField = languageServerPanel.getDebugPortField();
        int port = 0;
        try {
            port = Integer.parseInt(debugPort);
            if (port < debugPortField.getMin() || port > debugPortField.getMax()) {
                port = 0;
            }
        } catch (Exception ignore) {
        }
        debugPortField.setNumber(port);
    }

    public boolean isDebugSuspend() {
        return languageServerPanel.getDebugSuspendCheckBox().isSelected();
    }

    public void setDebugSuspend(boolean debugSuspend) {
        languageServerPanel.getDebugSuspendCheckBox().setSelected(debugSuspend);
    }

    public ServerTrace getServerTrace() {
        return (ServerTrace) languageServerPanel.getServerTraceComboBox().getSelectedItem();
    }

    public void setServerTrace(ServerTrace serverTrace) {
        languageServerPanel.getServerTraceComboBox().setSelectedItem(serverTrace);
    }

    public String getCommandLine() {
        return languageServerPanel.getCommandLine().getText();
    }

    public void setCommandLine(String commandLine) {
        languageServerPanel.getCommandLine().setText(commandLine);

    }

    public void setLanguageMappings(@NotNull List<ServerMappingSettings> mappings) {
        mappingPanel.setLanguageMappings(mappings);
    }

    public void setFileTypeMappings(@NotNull List<ServerMappingSettings> mappings) {
        mappingPanel.setFileTypeMappings(mappings);
    }

    public void setFileNamePatternMappings(List<ServerMappingSettings> mappings) {
        mappingPanel.setFileNamePatternMappings(mappings);
    }

    public String getConfigurationContent() {
        return languageServerPanel.getConfiguration().getText();
    }

    public void setConfigurationContent(String configurationContent) {
        var configuration = languageServerPanel.getConfiguration();
        configuration.setText(configurationContent);
        configuration.setCaretPosition(0);
    }

    public String getInitializationOptionsContent() {
        return languageServerPanel.getInitializationOptionsWidget().getText();
    }

    public void setInitializationOptionsContent(String initializationOptionsContent) {
        var initializationOptions = languageServerPanel.getInitializationOptionsWidget();
        initializationOptions.setText(initializationOptionsContent);
        initializationOptions.setCaretPosition(0);
    }

    @Override
    public void dispose() {

    }

    public List<ServerMappingSettings> getMappings() {
        return mappingPanel.getAllMappings();
    }

}

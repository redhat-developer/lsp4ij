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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.PortField;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.launching.ui.CommandLineWidget;
import com.redhat.devtools.lsp4ij.launching.ui.FileTypeServerMappingTablePanel;
import com.redhat.devtools.lsp4ij.launching.ui.LanguageServerMappingTablePanel;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.util.ArrayList;
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

    private final JBTextArea commandField = new CommandLineWidget();
    private final PortField debugPortField = new PortField();
    private final JBCheckBox debugSuspendCheckBox = new JBCheckBox(LanguageServerBundle.message("language.server.debug.suspend"));
    private final ComboBox<ServerTrace> serverTraceComboBox = new ComboBox<>(new DefaultComboBoxModel<>(ServerTrace.values()));
    private LanguageServerMappingTablePanel languageMappingsPanel;
    private FileTypeServerMappingTablePanel fileTypeMappingsPanel;
    public LanguageServerView(LanguageServerDefinition languageServerDefinition) {
        boolean isLaunchConfiguration = languageServerDefinition instanceof UserDefinedLanguageServerDefinition;
        JComponent descriptionPanel = createDescription(languageServerDefinition.description.trim());
        JPanel settingsPanel = createSettings(descriptionPanel,isLaunchConfiguration);
        if (!isLaunchConfiguration) {
            TitledBorder title = IdeBorderFactory.createTitledBorder(languageServerDefinition.getDisplayName());
            settingsPanel.setBorder(title);
        }
        JPanel wrapper = JBUI.Panels.simplePanel(settingsPanel);
        wrapper.setBorder(JBUI.Borders.emptyLeft(10));
        this.myMainPanel = wrapper;
    }

    private JPanel createSettings(JComponent description,boolean launchingServerDefinition) {
        FormBuilder builder = FormBuilder.createFormBuilder()
                .setFormLeftIndent(10)
                .addComponent(description, 0);
        if (launchingServerDefinition) {
         builder.addLabeledComponent(LanguageServerBundle.message("new.language.server.dialog.command"), commandField, true);
        } else {
            builder
                    .addLabeledComponent(LanguageServerBundle.message("language.server.debug.port"), debugPortField, 5)
                    .addComponent(debugSuspendCheckBox, 5)
                    .addLabeledComponent(LanguageServerBundle.message("language.server.trace"), serverTraceComboBox, 5);

        }
        boolean editable = launchingServerDefinition;
        languageMappingsPanel = new LanguageServerMappingTablePanel(editable);
        builder.addLabeledComponent(LanguageServerBundle.message("new.language.server.dialog.mappings.language"),
                languageMappingsPanel, true);

        fileTypeMappingsPanel = new FileTypeServerMappingTablePanel(editable);
        builder.addLabeledComponent(LanguageServerBundle.message("new.language.server.dialog.mappings.fileType"),
                fileTypeMappingsPanel, true);

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
        return debugPortField.getNumber() <= 0? "": Integer.toString(debugPortField.getNumber());
    }

    public void setDebugPort(String debugPort) {
        int port = 0;
        try {
            port = Integer.parseInt(debugPort);
            if (port < debugPortField.getMin() || port > debugPortField.getMax()) {
                port = 0;
            }
        } catch (Exception ignore) {}
        debugPortField.setNumber(port);
    }

    public boolean isDebugSuspend() {
        return debugSuspendCheckBox.isSelected();
    }

    public void setDebugSuspend(boolean debugSuspend) {
        debugSuspendCheckBox.setSelected(debugSuspend);
    }

    public ServerTrace getServerTrace() {
        return (ServerTrace) serverTraceComboBox.getSelectedItem();
    }

    public void setServerTrace(ServerTrace serverTrace) {
        serverTraceComboBox.setSelectedItem(serverTrace);
    }

    public String getCommandLine() {
        return commandField.getText();
    }

    public void setCommandLine(String commandLine) {
        commandField.setText(commandLine);
    }

    public void setLanguageMappings(@NotNull List<ServerMappingSettings> mappings) {
        languageMappingsPanel.refresh(mappings);
    }

    public void setFileTypeMappings(@NotNull List<ServerMappingSettings> mappings) {
        fileTypeMappingsPanel.refresh(mappings);
    }

    @Override
    public void dispose() {

    }

    public List<ServerMappingSettings> getMappings() {
        List<ServerMappingSettings> mappings =new ArrayList<>(languageMappingsPanel.getServerMappings());
        mappings.addAll(fileTypeMappingsPanel.getServerMappings());
        return mappings;
    }
}

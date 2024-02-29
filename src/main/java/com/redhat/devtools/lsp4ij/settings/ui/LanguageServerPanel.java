/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.PortField;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Language server panel which show information about language server in several tabs:
 *
 * <ul>
 *     <li>Server tab</li>
 *     <li>Mappings tab</li>
 *     <li>Configuration tab</li>
 *     <li>Debug tab</li>
 * </ul>
 */
public class LanguageServerPanel {

    public static enum EditionMode {
        NEW_USER_DEFINED,
        EDIT_USER_DEFINED,
        EDIT_EXTENSION;
    }

    private JBTextField serverName;
    private CommandLineWidget commandLine;
    private ServerMappingsPanel mappingsPanel;

    private final PortField debugPortField = new PortField();
    private final JBCheckBox debugSuspendCheckBox = new JBCheckBox(LanguageServerBundle.message("language.server.debug.suspend"));
    private final ComboBox<ServerTrace> serverTraceComboBox = new ComboBox<>(new DefaultComboBoxModel<>(ServerTrace.values()));

    private LanguageServerConfigurationWidget configurationWidget;

    private LanguageServerInitializationOptionsWidget initializationOptionsWidget;

    public LanguageServerPanel(FormBuilder builder, JComponent description, EditionMode mode) {
        createUI(builder, description, mode);
    }

    private void createUI(FormBuilder builder, JComponent description, EditionMode mode) {
        JBTabbedPane tabbedPane = new JBTabbedPane();
        builder.addComponent(tabbedPane);

        // Server tab
        addServerTab(tabbedPane, description, mode);
        // Mappings tab
        addMappingsTab(tabbedPane, mode);
        if (mode != EditionMode.EDIT_EXTENSION) {
            // Configuration tab to fill LSP Configuration + LSP Initialize Options
            addConfigurationTab(tabbedPane);
        }
        // Debug tab
        addDebugTab(tabbedPane, mode);
    }

    private void addServerTab(JBTabbedPane tabbedPane, JComponent description, EditionMode mode) {
        FormBuilder serverTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.server"));
        if (description != null) {
            serverTab.addComponent(description, 0);
        }
        if (mode == EditionMode.NEW_USER_DEFINED) {
            // Server name
            createServerNameField(serverTab);
        }
        if (mode != EditionMode.EDIT_EXTENSION) {
            // Command line
            createCommandLineField(serverTab);
        }
    }

    private void addMappingsTab(JBTabbedPane tabbedPane, EditionMode mode) {
        FormBuilder mappingsTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.mappings"));
        this.mappingsPanel = new ServerMappingsPanel(mappingsTab, mode != EditionMode.EDIT_EXTENSION);
    }

    private void addDebugTab(JBTabbedPane tabbedPane, EditionMode mode) {
        if (mode == EditionMode.NEW_USER_DEFINED) {
            return;
        }
        FormBuilder debugTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.debug"));
        if (mode == EditionMode.EDIT_EXTENSION) {
            debugTab
                    .addLabeledComponent(LanguageServerBundle.message("language.server.debug.port"), debugPortField)
                    .addComponent(debugSuspendCheckBox);
        }
        debugTab.addLabeledComponent(LanguageServerBundle.message("language.server.trace"), serverTraceComboBox);
    }

    private void addConfigurationTab(JBTabbedPane tabbedPane) {
        FormBuilder configurationTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.configuration"), false);
        createConfigurationField(configurationTab);
        createInitializationOptionsTabField(configurationTab);
    }

    private static FormBuilder addTab(JBTabbedPane tabbedPane, String tabTitle) {
        return addTab(tabbedPane, tabTitle, true);
    }

    @NotNull
    private static FormBuilder addTab(JBTabbedPane tabbedPane, String tabTitle, boolean addToTop) {
        FormBuilder builder = new FormBuilder();
        var tabPanel = new BorderLayoutPanel();
        if (addToTop) {
            tabPanel.addToTop(builder.getPanel());
        } else {
            tabPanel.add(builder.getPanel());
        }
        tabbedPane.add(tabTitle, tabPanel);
        return builder;
    }

    private void createServerNameField(FormBuilder builder) {
        serverName = new JBTextField();
        builder.addLabeledComponent(LanguageServerBundle.message("language.server.serverName"), serverName);
    }

    private void createCommandLineField(FormBuilder builder) {
        commandLine = new CommandLineWidget();
        JBScrollPane scrollPane = new JBScrollPane(commandLine);
        scrollPane.setMinimumSize(new Dimension(JBUIScale.scale(600), JBUIScale.scale(100)));
        builder.addLabeledComponent(LanguageServerBundle.message("language.server.command"), scrollPane, true);
    }

    private void createConfigurationField(FormBuilder builder) {
        configurationWidget = new LanguageServerConfigurationWidget();
        JBScrollPane scrollPane = new JBScrollPane(configurationWidget);
        scrollPane.setMinimumSize(new Dimension(JBUIScale.scale(600), JBUIScale.scale(100)));
        builder.addLabeledComponent(LanguageServerBundle.message("language.server.configuration"), scrollPane, true);
    }

    private void createInitializationOptionsTabField(FormBuilder builder) {
        initializationOptionsWidget = new LanguageServerInitializationOptionsWidget();
        JBScrollPane scrollPane = new JBScrollPane(initializationOptionsWidget);
        scrollPane.setMinimumSize(new Dimension(JBUIScale.scale(600), JBUIScale.scale(100)));
        builder.addLabeledComponent(LanguageServerBundle.message("language.server.initializationOptions"), scrollPane, true);
    }

    public JBTextField getServerName() {
        return serverName;
    }

    public CommandLineWidget getCommandLine() {
        return commandLine;
    }

    public ServerMappingsPanel getMappingsPanel() {
        return mappingsPanel;
    }

    public LanguageServerConfigurationWidget getConfiguration() {
        return configurationWidget;
    }

    public LanguageServerInitializationOptionsWidget getInitializationOptionsWidget() {
        return initializationOptionsWidget;
    }

    public JBCheckBox getDebugSuspendCheckBox() {
        return debugSuspendCheckBox;
    }

    public PortField getDebugPortField() {
        return debugPortField;
    }

    public ComboBox<ServerTrace> getServerTraceComboBox() {
        return serverTraceComboBox;
    }
}

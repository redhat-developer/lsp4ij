/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * Mitja Leino <mitja.leino@hotmail.com> - Add DialogWrapper for validations
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.settings.ErrorReportingKind;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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

    public enum EditionMode {
        NEW_USER_DEFINED,
        EDIT_USER_DEFINED,
        EDIT_EXTENSION;
    }

    private ServerNameWidget serverName;
    private EnvironmentVariablesComponent environmentVariables;
    private CommandLineWidget commandLine;
    private ServerMappingsPanel mappingsPanel;

    private final ComboBox<ErrorReportingKind> errorReportingKindCombo = new ComboBox<>(new DefaultComboBoxModel<>(ErrorReportingKind.values()));
    private final ComboBox<ServerTrace> serverTraceComboBox = new ComboBox<>(new DefaultComboBoxModel<>(ServerTrace.values()));
    private final PortField debugPortField = new PortField();
    private final JBCheckBox debugSuspendCheckBox = new JBCheckBox(LanguageServerBundle.message("language.server.debug.suspend"));
    private LanguageServerConfigurationWidget configurationWidget;

    private LanguageServerInitializationOptionsWidget initializationOptionsWidget;
    private final ValidatableDialog dialogWrapper;

    public LanguageServerPanel(FormBuilder builder, JComponent description, EditionMode mode, ValidatableDialog dialogWrapper) {
        this.dialogWrapper = dialogWrapper;
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

        // Add validation
        var serverNameWidget = getServerName();
        if (serverNameWidget != null) {
            addValidator(serverNameWidget);
        }
        var commandLineWidget = getCommandLine();
        if (commandLineWidget != null) {
            addValidator(getCommandLine());
        }
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
            environmentVariables = new EnvironmentVariablesComponent();
            serverTab.addComponent(environmentVariables);
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

        // Error reporting kind
        createErrorReportingCombo(debugTab);

        // Server trace
        debugTab.addLabeledComponent(LanguageServerBundle.message("language.server.trace"), serverTraceComboBox);

        if (mode == EditionMode.EDIT_EXTENSION) {
            // Debug port + suspend
            debugTab
                    .addLabeledComponent(LanguageServerBundle.message("language.server.debug.port"), debugPortField)
                    .addComponent(debugSuspendCheckBox);
        }
    }

    private void createErrorReportingCombo(FormBuilder builder) {

        errorReportingKindCombo.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
            String text = LanguageServerBundle.message("language.server.error.reporting.none");
            if (value != null) {
                switch (value) {
                    case as_notification ->
                            text = LanguageServerBundle.message("language.server.error.reporting.as_notification");
                    case in_log -> text = LanguageServerBundle.message("language.server.error.reporting.in_log");
                }
            }
            label.setText(text);
        }));

        var helpLabel = ContextHelpLabel.createWithLink("",
                LanguageServerBundle.message("language.server.error.reporting.context.help.tooltip"),
                LanguageServerBundle.message("language.server.error.reporting.context.help.link"),
                () -> {
                    BrowserUtil.browse("https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#responseMessage");
                });

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(errorReportingKindCombo);
        panel.add(helpLabel);

        builder.addLabeledComponent(LanguageServerBundle.message("language.server.error.reporting"), panel, 0);
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
        FormBuilder builder = FormBuilder.createFormBuilder();
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
        serverName = new ServerNameWidget();
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

    public ServerNameWidget getServerName() {
        return serverName;
    }

    public EnvironmentVariablesComponent getEnvironmentVariables() {
        return environmentVariables;
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

    public ComboBox<ErrorReportingKind> getErrorReportingKindCombo() {
        return errorReportingKindCombo;
    }

    public @NotNull List<ValidationInfo> doValidateAll() {
        List<ValidationInfo> validations = new ArrayList<>();
        var serverNameWidget = getServerName();
        if (serverNameWidget != null) {
            serverNameWidget.validate(validations);
        }
        var commandLineWidget = getCommandLine();
        if (commandLineWidget != null) {
            commandLineWidget.validate(validations);
        }
        return validations;
    }

    private void addValidator(JTextComponent textComponent) {
        textComponent.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                dialogWrapper.refreshValidation();
            }
        });
    }
}

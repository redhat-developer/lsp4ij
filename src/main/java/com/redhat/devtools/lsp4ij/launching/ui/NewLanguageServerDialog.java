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
package com.redhat.devtools.lsp4ij.launching.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.ui.FormBuilder;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.UIConfiguration;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.ui.LanguageServerPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.redhat.devtools.lsp4ij.LSPNotificationConstants.LSP4IJ_GENERAL_NOTIFICATIONS_ID;

/**
 * New language server dialog.
 */
public class NewLanguageServerDialog extends DialogWrapper {

    private final Project project;

    private LanguageServerPanel languageServerPanel;
    private LanguageServerTemplate currentTemplate = null;

    public NewLanguageServerDialog(@NotNull Project project) {
        super(project);
        this.project = project;
        super.setTitle(LanguageServerBundle.message("new.language.server.dialog.title"));
        init();
        initValidation();
    }

    private static String getCommandLine(LanguageServerTemplate entry) {
        StringBuilder command = new StringBuilder();
        if (entry.getProgramArgs() != null) {
            if (!command.isEmpty()) {
                command.append(' ');
            }
            command.append(entry.getProgramArgs());
        }
        return command.toString();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        FormBuilder builder = FormBuilder
                .createFormBuilder();

        // Template combo
        createTemplatePanel(builder);
        // Create server name,  command line, mappings, configuration UI
        this.languageServerPanel = new LanguageServerPanel(builder, null, createUIConfiguration(), false, project);
        languageServerPanel.setCommandLineUpdater(new CommandLineUpdater() {
            @Override
            public String getCommandLine() {
                return languageServerPanel.getCommandLine();
            }

            @Override
            public void setCommandLine(String commandLine) {
                ApplicationManager.getApplication().invokeLater(() -> languageServerPanel.setCommandLine(commandLine));
            }
        });

        // Add validation
        addValidator(this.languageServerPanel.getServerName());
        var commandLine = this.languageServerPanel.getCommandLineWidget();
        if (commandLine != null) {
            addValidator(commandLine);
        }

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(builder.getPanel(), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(300, 500));
        return panel;
    }

    private UIConfiguration createUIConfiguration() {
        UIConfiguration configuration = new UIConfiguration();

        // Server tab configuration
        configuration.setShowServerName(true);
        configuration.setShowCommandLine(true);

        // Mappings tab configuration
        configuration.setServerMappingsEditable(true);

        // Configuration tab configuration
        configuration.setShowServerConfiguration(true);
        configuration.setShowServerInitializationOptions(true);
        configuration.setShowServerExperimental(true);
        configuration.setShowClientConfiguration(true);

        // Debug tab configuration
        configuration.setShowDebug(false);

        // Installer tab configuration
        configuration.setShowInstaller(true);

        return configuration;
    }

    private void createTemplatePanel(@NotNull FormBuilder builder) {
        JPanel templatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        // Create "Choose template..." hyperlink
        final var selectTemplateHyperLink =  new HyperlinkLabel(LanguageServerBundle.message("new.language.server.dialog.choose.template"));
        selectTemplateHyperLink.addHyperlinkListener(e -> {
            openChooseTemplatePopup(selectTemplateHyperLink);
        });
        templatePanel.add(selectTemplateHyperLink);
        // or
        templatePanel.add(new JLabel(DAPBundle.message("dap.settings.editor.server.factory.or")));
        // Create "Import template..." hyperlink
        final var importTemplateHyperLink =  new HyperlinkLabel(LanguageServerBundle.message("new.language.server.dialog.import.template"));
        importTemplateHyperLink.addHyperlinkListener(e -> {
            importTemplate();
        });
        templatePanel.add(importTemplateHyperLink);
        builder.addLabeledComponent(LanguageServerBundle.message("new.language.server.dialog.template"), templatePanel);
    }

    private void openChooseTemplatePopup(@NotNull Component button) {
        var searchTemplatePopupUI = new ChooseLanguageServerTemplatePopupUI(this::loadFromTemplate);
        searchTemplatePopupUI.show(button);
    }

    private void importTemplate() {
        FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true,
                false, false, false, false);
        fileChooserDescriptor.setTitle(LanguageServerBundle.message("new.language.server.dialog.export.template.title"));
        fileChooserDescriptor.setDescription(LanguageServerBundle.message("new.language.server.dialog.export.template.description"));

        VirtualFile virtualFile = FileChooser.chooseFile(fileChooserDescriptor, project, null);
        if (virtualFile != null) {
            try {
                var template = LanguageServerTemplateManager.getInstance().importServerTemplate(virtualFile);
                if (template != null) {
                    loadFromTemplate(template);
                } else {
                    showImportErrorNotification(LanguageServerBundle.message("new.language.server.dialog.import.template.error.description"));
                }
            } catch (IOException ex) {
                showImportErrorNotification(ex.getLocalizedMessage());
            }
        }
    }

    private void showImportErrorNotification(String message) {
        Notification notification = new Notification(LSP4IJ_GENERAL_NOTIFICATIONS_ID,
                LanguageServerBundle.message("new.language.server.dialog.import.template.error.title"),
                message, NotificationType.ERROR);
        Notifications.Bus.notify(notification);
    }

    @Override
    protected @NotNull Action getHelpAction() {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentTemplate != null && StringUtils.isNotBlank(currentTemplate.getDescription())) {
                    ShowInstructionDialog dialog = new ShowInstructionDialog(currentTemplate, project);
                    dialog.show();
                }
            }
        };
    }

    public void loadFromTemplate(@NotNull LanguageServerTemplate template) {
        this.currentTemplate = template;
        // Update name
        var serverName = this.languageServerPanel.getServerName();
        serverName.setText(template.getName() != null ? template.getName() : "");

        // Update server Url
        languageServerPanel.setServerUrl(template.getUrl());

        // Update command
        String command = getCommandLine(template);
        languageServerPanel.setCommandLine(command);

        // Update mappings
        var mappingsPanel = this.languageServerPanel.getMappingsPanel();
        mappingsPanel.refreshMappings(template);

        // Update server configuration + expand
        languageServerPanel.setConfigurationContent(template.getConfiguration());
        this.languageServerPanel.setExpandConfiguration(template.isExpandConfiguration());

        // Update server configuration JSON Schema
        this.languageServerPanel.setConfigurationSchemaContent(template.getConfigurationSchema());

        // Update initialization options
        this.languageServerPanel.setInitializationOptionsContent(template.getInitializationOptions());

        // Update experimental
        this.languageServerPanel.setExperimentalContent(template.getExperimental());

        // Update client configuration
        this.languageServerPanel.setClientConfigurationContent(template.getClientConfiguration());

        // Update installer configuration
        this.languageServerPanel.setInstallerConfigurationContent(template.getInstallerConfiguration());
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return this.languageServerPanel.getServerName();
    }

    @Override
    protected @NotNull List<ValidationInfo> doValidateAll() {
        List<ValidationInfo> validations = new ArrayList<>();
        addValidationInfo(validateServerName(), validations);
        addValidationInfo(validateCommand(), validations);
        return validations;
    }

    private void addValidationInfo(ValidationInfo validationInfo, List<ValidationInfo> validations) {
        if (validationInfo == null) {
            return;
        }
        validations.add((validationInfo));
    }

    private ValidationInfo validateServerName() {
        var serverName = this.languageServerPanel.getServerName();
        if (serverName.getText().isBlank()) {
            String errorMessage = LanguageServerBundle.message("new.language.server.dialog.validation.serverName.must.be.set");
            return new ValidationInfo(errorMessage, serverName);
        }
        return null;
    }

    private ValidationInfo validateCommand() {
        var commandLine = this.languageServerPanel.getCommandLineWidget();
        if (commandLine != null && commandLine.getText().isBlank()) {
            String errorMessage = LanguageServerBundle.message("new.language.server.dialog.validation.commandLine.must.be.set");
            return new ValidationInfo(errorMessage, commandLine);
        }
        return null;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction(), getCancelAction()};
    }


    @Override
    protected void doOKAction() {
        String serverId = UUID.randomUUID().toString();

        List<ServerMappingSettings> mappingSettings = this.languageServerPanel.getMappingsPanel().getAllMappings();

        // Register language server and mappings definition
        String templateId = currentTemplate != null && currentTemplate != LanguageServerTemplate.NEW_TEMPLATE ? currentTemplate.getId() : null;
        String serverName = this.languageServerPanel.getServerName().getText();
        String serverUrl = this.languageServerPanel.getServerUrl();
        String commandLine = this.languageServerPanel.getCommandLine();
        Map<String, String> userEnvironmentVariables = this.languageServerPanel.getUserEnvironmentVariables();
        boolean includeSystemEnvironmentVariables = this.languageServerPanel.isIncludeSystemEnvironmentVariables();
        String configuration = this.languageServerPanel.getConfigurationContent();
        boolean expandConfiguration = this.languageServerPanel.isExpandConfiguration();
        String configurationSchema = this.languageServerPanel.getConfigurationSchemaContent();
        String initializationOptions = this.languageServerPanel.getInitializationOptionsContent();
        String experimental = this.languageServerPanel.getExperimentalContent();
        String clientConfiguration = this.languageServerPanel.getClientConfigurationContent();
        String installerConfiguration = this.languageServerPanel.getInstallerConfigurationContent();
        UserDefinedLanguageServerDefinition definition = new UserDefinedLanguageServerDefinition(serverId,
                templateId,
                serverName,
                serverUrl,
                "",
                commandLine != null ? commandLine : "",
                userEnvironmentVariables != null ? userEnvironmentVariables : Collections.emptyMap(),
                includeSystemEnvironmentVariables,
                configuration,
                expandConfiguration,
                configurationSchema,
                initializationOptions,
                experimental,
                clientConfiguration,
                installerConfiguration);
        definition.setUrl(this.languageServerPanel.getServerUrl());
        // Save the server-specific settings (trace, debug, use integer IDs, etc.) BEFORE registering the server
        // This ensures the settings are available immediately when the server is first accessed
        saveServerSettings(serverId);

        LanguageServersRegistry.getInstance().addServerDefinition(project, definition, mappingSettings);

        // Close the dialog after everything is saved
        super.doOKAction();
    }

    /**
     * Saves the server-specific settings (trace, debug, use integer IDs, etc.) from the UI to the project settings.
     */
    private void saveServerSettings(String serverId) {
        // Save project-scope settings (trace, debug, use integer IDs)
        var projectSettings = new ProjectLanguageServerSettings.LanguageServerDefinitionSettings();
        projectSettings.setServerTrace(languageServerPanel.getServerTraceComboBox().getItem());
        projectSettings.setErrorReportingKind(languageServerPanel.getErrorReportingKindCombo().getItem());
        projectSettings.setDebugPort(String.valueOf(languageServerPanel.getDebugPortField().getNumber()));
        projectSettings.setDebugSuspend(languageServerPanel.getDebugSuspendCheckBox().isSelected());
        boolean useIntegerIds = languageServerPanel.getUseIntegerIdsCheckBox().isSelected();
        projectSettings.setUseIntegerIds(useIntegerIds);


        // Save the settings to the project
        ProjectLanguageServerSettings
            .getInstance(project)
            .updateSettings(serverId, projectSettings, false);

    }

    private void addValidator(JTextComponent textComponent) {
        textComponent.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                NewLanguageServerDialog.super.initValidation();
            }
        });
    }

    @Override
    protected void dispose() {
        this.languageServerPanel.dispose();
        super.dispose();
    }

}

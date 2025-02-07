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
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBInsets;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.devtools.lsp4ij.LSPNotificationConstants.LSP4IJ_GENERAL_NOTIFICATIONS_ID;

/**
 * New language server dialog.
 */
public class NewLanguageServerDialog extends DialogWrapper {

    private final ComboBox<LanguageServerTemplate> templateCombo = new ComboBox<>(new DefaultComboBoxModel<>(getLanguageServerTemplates()));
    private final Project project;

    private LanguageServerPanel languageServerPanel;
    private LanguageServerTemplate currentTemplate = null;
    private JButton showInstructionButton = null;

    private static LanguageServerTemplate[] getLanguageServerTemplates() {
        List<LanguageServerTemplate> templates = new ArrayList<>();
        templates.add(LanguageServerTemplate.NONE);
        templates.add(LanguageServerTemplate.NEW_TEMPLATE);
        templates.addAll(LanguageServerTemplateManager.getInstance().getTemplates());
        return templates.toArray(new LanguageServerTemplate[0]);
    }

    public NewLanguageServerDialog(@NotNull Project project) {
        super(project);
        this.project = project;
        super.setTitle(LanguageServerBundle.message("new.language.server.dialog.title"));
        init();
        initValidation();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        FormBuilder builder = FormBuilder
                .createFormBuilder();

        // Template combo
        createTemplateCombo(builder);
        // Create server name,  command line, mappings, configuration UI
        this.languageServerPanel = new LanguageServerPanel(builder, null, LanguageServerPanel.EditionMode.NEW_USER_DEFINED, project);

        // Add validation
        addValidator(this.languageServerPanel.getServerName());
        addValidator(this.languageServerPanel.getCommandLine());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(builder.getPanel(), BorderLayout.CENTER);
        return panel;
    }

    private void createTemplateCombo(FormBuilder builder) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        templateCombo.setRenderer(new SimpleListCellRenderer<>() {
            @Override
            public void customize(@NotNull JList list,
                                  @Nullable LanguageServerTemplate value,
                                  int index,
                                  boolean selected,
                                  boolean hasFocus) {
                if (value == null) {
                    setText("");
                } else {
                    setText(value.getName());
                }
            }
        });

        showInstructionButton = super.createHelpButton(new JBInsets(0, 0, 0, 0));
        showInstructionButton.setText("");
        templateCombo.addItemListener(getTemplateComboListener());

        panel.add(templateCombo, BorderLayout.WEST);
        panel.add(showInstructionButton, BorderLayout.CENTER);
        builder.addLabeledComponent(LanguageServerBundle.message("new.language.server.dialog.template"), panel);
    }

    /**
     * Create the template combo listener that handles item selection
     *
     * @return created ItemListener
     */
    private ItemListener getTemplateComboListener() {
        FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true,
                false, false, false, false);
        fileChooserDescriptor.setTitle(LanguageServerBundle.message("new.language.server.dialog.export.template.title"));
        fileChooserDescriptor.setDescription(LanguageServerBundle.message("new.language.server.dialog.export.template.description"));

        return e -> {
            // Only trigger listener on selected items to avoid double triggering
            if (e.getStateChange() == ItemEvent.SELECTED) {
                LanguageServerTemplate template = templateCombo.getItem();
                if (template == LanguageServerTemplate.NEW_TEMPLATE) {
                    VirtualFile virtualFile = FileChooser.chooseFile(fileChooserDescriptor, project, null);
                    if (virtualFile != null) {
                        try {
                            template = LanguageServerTemplateManager.getInstance().importLsTemplate(virtualFile);
                            if (template == null) {
                                showImportErrorNotification(LanguageServerBundle.message("new.language.server.dialog.import.template.error.description"));
                            }
                        } catch (IOException ex) {
                            showImportErrorNotification(ex.getLocalizedMessage());
                        }
                    }
                    // Reset template to None after trying to import a custom file
                    templateCombo.setItem(LanguageServerTemplate.NONE);
                }

                currentTemplate = template;
                showInstructionButton.setEnabled(hasValidDescription(template));
                if (template != null) {
                    loadFromTemplate(template);
                }
            }
        };
    }

    private void showImportErrorNotification(String message) {
        Notification notification = new Notification(LSP4IJ_GENERAL_NOTIFICATIONS_ID,
                LanguageServerBundle.message("new.language.server.dialog.import.template.error.title"),
                message, NotificationType.ERROR);
        Notifications.Bus.notify(notification);
    }

    /**
     * Check that the template is not a placeholder and that it has a valid description
     *
     * @param template to check
     * @return true if template is not null, not a placeholder and has a description, else false
     */
    private boolean hasValidDescription(@Nullable LanguageServerTemplate template) {
        return template != null && template != LanguageServerTemplate.NONE
                && template != LanguageServerTemplate.NEW_TEMPLATE
                && StringUtils.isNotBlank(template.getDescription());
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

    private void loadFromTemplate(@NotNull LanguageServerTemplate template) {
        // Update name
        var serverName = this.languageServerPanel.getServerName();
        serverName.setText(template.getName() != null ? template.getName() : "");

        // Update command
        var commandLine = this.languageServerPanel.getCommandLine();
        String command = getCommandLine(template);
        commandLine.setText(command);

        // Update mappings
        var mappingsPanel = this.languageServerPanel.getMappingsPanel();
        mappingsPanel.refreshMappings(template);

        // Update server configuration
        var configuration = this.languageServerPanel.getConfiguration();
        configuration.setText(template.getConfiguration() != null ? template.getConfiguration() : "");
        configuration.setCaretPosition(0);

        // Update server configuration JSON Schema
        this.languageServerPanel.setConfigurationSchemaContent(template.getConfigurationSchema() != null ? template.getConfigurationSchema() : "");

        // Update initialization options
        var initializationOptions = this.languageServerPanel.getInitializationOptionsWidget();
        initializationOptions.setText(template.getInitializationOptions() != null ? template.getInitializationOptions() : "");
        initializationOptions.setCaretPosition(0);

        // Update client configuration
        var clientConfiguration = this.languageServerPanel.getClientConfigurationWidget();
        clientConfiguration.setText(template.getClientConfiguration() != null ? template.getClientConfiguration() : "");
        clientConfiguration.setCaretPosition(0);
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
        var commandLine = this.languageServerPanel.getCommandLine();
        if (commandLine.getText().isBlank()) {
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
        super.doOKAction();

        String serverId = UUID.randomUUID().toString();
        List<ServerMappingSettings> mappingSettings = this.languageServerPanel.getMappingsPanel().getAllMappings();

        // Register language server and mappings definition
        String templateId = currentTemplate != null && currentTemplate != LanguageServerTemplate.NEW_TEMPLATE ? currentTemplate.getId() : null;
        String serverName = this.languageServerPanel.getServerName().getText();
        String commandLine = this.languageServerPanel.getCommandLine().getText();
        Map<String, String> userEnvironmentVariables = this.languageServerPanel.getEnvironmentVariables().getEnvs();
        boolean includeSystemEnvironmentVariables = this.languageServerPanel.getEnvironmentVariables().isPassParentEnvs();
        String configuration = this.languageServerPanel.getConfiguration().getText();
        String configurationSchema = this.languageServerPanel.getConfigurationSchemaContent();
        String initializationOptions = this.languageServerPanel.getInitializationOptionsWidget().getText();
        String clientConfiguration = this.languageServerPanel.getClientConfigurationWidget().getText();
        UserDefinedLanguageServerDefinition definition = new UserDefinedLanguageServerDefinition(serverId,
                templateId,
                serverName,
                "",
                commandLine,
                userEnvironmentVariables,
                includeSystemEnvironmentVariables,
                configuration,
                configurationSchema,
                initializationOptions,
                clientConfiguration);
        LanguageServersRegistry.getInstance().addServerDefinition(project, definition, mappingSettings);
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

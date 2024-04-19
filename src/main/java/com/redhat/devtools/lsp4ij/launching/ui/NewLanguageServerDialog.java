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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBInsets;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * New language server dialog.
 */
public class NewLanguageServerDialog extends DialogWrapper {

    private final ComboBox<LanguageServerTemplate> templateCombo = new ComboBox<>(new DefaultComboBoxModel<>(getLanguageServerTemplates()));
    private final Project project;

    private LanguageServerPanel languageServerPanel;

    private static LanguageServerTemplate[] getLanguageServerTemplates() {
        List<LanguageServerTemplate> templates = new ArrayList<>();
        templates.add(LanguageServerTemplate.NONE);
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
        this.languageServerPanel = new LanguageServerPanel(builder, null, LanguageServerPanel.EditionMode.NEW_USER_DEFINED);

        // Add validation
        addValidator(this.languageServerPanel.getServerName());
        addValidator(this.languageServerPanel.getCommandLine());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(builder.getPanel(), BorderLayout.CENTER);
        return panel;
    }

    private void createTemplateCombo(FormBuilder builder) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        templateCombo.setRenderer(new SimpleListCellRenderer<LanguageServerTemplate>() {
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

        final JButton showInstructionButton = super.createHelpButton(new JBInsets(0,0,0,0));
        showInstructionButton.setText("");
        templateCombo.addItemListener(event -> {
            LanguageServerTemplate template = (LanguageServerTemplate) event.getItem();
            loadFromTemplate(template);
            showInstructionButton.setEnabled(template.hasDocumentation() && template != LanguageServerTemplate.NONE);
        });
        panel.add(templateCombo, BorderLayout.WEST);

        showInstructionButton.addActionListener(e -> {
            LanguageServerTemplate template = (LanguageServerTemplate) templateCombo.getSelectedItem();
            if (template != null) {
                ShowInstructionDialog dialog = new ShowInstructionDialog(template, project);
                dialog.show();
            }
        });
        showInstructionButton.setEnabled(false);
        panel.add(showInstructionButton, BorderLayout.CENTER);
        builder.addLabeledComponent(LanguageServerBundle.message("new.language.server.dialog.template"), panel);
    }

    private void loadFromTemplate(LanguageServerTemplate template) {
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

        // Update configuration
        var configuration = this.languageServerPanel.getConfiguration();
        configuration.setText(template.getConfiguration() != null ? template.getConfiguration() : "");
        configuration.setCaretPosition(0);

        // Update initialize options
        var initializationOptions = this.languageServerPanel.getInitializationOptionsWidget();
        initializationOptions.setText(template.getInitializationOptions() != null ? template.getInitializationOptions() : "");
        initializationOptions.setCaretPosition(0);
    }

    private static String getCommandLine(LanguageServerTemplate entry) {
        StringBuilder command = new StringBuilder();
        if (entry.getRuntime() != null) {
            command.append(entry.getRuntime());
        }
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
        addValidationInfo(this.languageServerPanel.getCommandLine().getValidationInfo(), validations);
        addValidationInfo(this.languageServerPanel.getServerName().getValidationInfo(), validations);
        return validations;
    }

    private void addValidationInfo(ValidationInfo validationInfo, List<ValidationInfo> validations) {
        if (validationInfo == null) {
            return;
        }
        validations.add((validationInfo));
    }


    @Override
    protected void doOKAction() {
        super.doOKAction();

        String serverId = UUID.randomUUID().toString();
        List<ServerMappingSettings> mappingSettings = this.languageServerPanel.getMappingsPanel().getAllMappings();

        // Register language server and mappings definition
        String serverName = this.languageServerPanel.getServerName().getText();
        String commandLine = this.languageServerPanel.getCommandLine().getText();
        Map<String, String> userEnvironmentVariables = this.languageServerPanel.getEnvironmentVariables().getEnvs();
        boolean includeSystemEnvironmentVariables = this.languageServerPanel.getEnvironmentVariables().isPassParentEnvs();
        String configuration = this.languageServerPanel.getConfiguration().getText();
        String initializationOptions = this.languageServerPanel.getInitializationOptionsWidget().getText();
        UserDefinedLanguageServerDefinition definition = new UserDefinedLanguageServerDefinition(serverId,
                serverName,
                "",
                commandLine,
                userEnvironmentVariables ,
                includeSystemEnvironmentVariables,
                configuration,
                initializationOptions);
        LanguageServersRegistry.getInstance().addServerDefinition(definition, mappingSettings);

    }

    private void addValidator(JTextComponent textComponent) {
        textComponent.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                NewLanguageServerDialog.super.initValidation();
            }
        });
    }

}

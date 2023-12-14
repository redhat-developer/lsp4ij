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
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBInsets;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * New language server dialog.
 */
public class NewLanguageServerDialog extends DialogWrapper {

    private final ComboBox<LanguageServerTemplate> templateCombo = new ComboBox<>(new DefaultComboBoxModel<>(getLanguageServerTemplates()));
    private final Project project;

    private JBTextField serverName;
    private CommandLineWidget commandLine;
    private LanguageServerMappingTablePanel languageMappingsPanel;
    private FileTypeServerMappingTablePanel fileTypeMappingsPanel;

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
        FormBuilder builder = new FormBuilder();

        // Template combo
        createTemplateCombo(builder);
        // Server name
        createServerNameField(builder);
        // Command line
        createCommandLineField(builder);
        // Language mappings
        createLanguageMappingsContent(builder);
        // File type mappings
        createFileTypeMappingsContent(builder);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(builder.getPanel(), BorderLayout.CENTER);
        return panel;
    }

    private void createTemplateCombo(FormBuilder builder) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        templateCombo.setRenderer(new ListCellRendererWrapper<LanguageServerTemplate>() {
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

        final JButton showInstructionButton = super.createHelpButton(JBInsets.emptyInsets());
        showInstructionButton.setText("");
        templateCombo.addItemListener(event -> {
            LanguageServerTemplate template = (LanguageServerTemplate) event.getItem();
            loadFromTemplate(template);
            showInstructionButton.setEnabled(template.getDescription() != null && template != LanguageServerTemplate.NONE);
        });
        panel.add(templateCombo, BorderLayout.WEST);

        showInstructionButton.addActionListener(e -> {
            LanguageServerTemplate template = (LanguageServerTemplate) templateCombo.getSelectedItem();
            ShowInstructionDialog dialog = new ShowInstructionDialog(template, project);
            dialog.show();
        });
        showInstructionButton.setEnabled(false);
        panel.add(showInstructionButton, BorderLayout.CENTER);
        builder.addLabeledComponent(LanguageServerBundle.message("new.language.server.dialog.template"), panel);
    }

    private void loadFromTemplate(LanguageServerTemplate template) {
        // Update name and command
        serverName.setText(template.getName() != null ? template.getName() : "");
        String command = getCommandLine(template);
        commandLine.setText(command);
        // Update mappings
        languageMappingsPanel.refresh(template.getLanguageMappings());
        fileTypeMappingsPanel.refresh(template.getFileTypeMappings());
    }

    private void createServerNameField(FormBuilder builder) {
        serverName = new JBTextField();
        builder.addLabeledComponent(LanguageServerBundle.message("new.language.server.dialog.serverName"), serverName);
        addValidator(serverName);
    }

    private void createCommandLineField(FormBuilder builder) {
        commandLine = new CommandLineWidget();

        JBScrollPane scrollPane = new JBScrollPane(commandLine);
        scrollPane.setMinimumSize(new Dimension(JBUIScale.scale(600), JBUIScale.scale(100)));
        builder.addLabeledComponent(LanguageServerBundle.message("new.language.server.dialog.command"), scrollPane, true);

        addValidator(commandLine);
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

    private void createLanguageMappingsContent(FormBuilder builder) {
        languageMappingsPanel = new LanguageServerMappingTablePanel();
        builder.addLabeledComponent(LanguageServerBundle.message("new.language.server.dialog.mappings.language"),
                languageMappingsPanel, true);
    }

    private void createFileTypeMappingsContent(FormBuilder builder) {
        fileTypeMappingsPanel = new FileTypeServerMappingTablePanel();
        builder.addLabeledComponent(LanguageServerBundle.message("new.language.server.dialog.mappings.fileType"),
                fileTypeMappingsPanel, true);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return serverName;
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
        if (serverName.getText().isBlank()) {
            String errorMessage = LanguageServerBundle.message("new.language.server.dialog.validation.serverName.must.be.set");
            return new ValidationInfo(errorMessage, serverName);
        }
        return null;
    }

    private ValidationInfo validateCommand() {
        if (commandLine.getText().isBlank()) {
            String errorMessage = LanguageServerBundle.message("new.language.server.dialog.validation.commandLine.must.be.set");
            return new ValidationInfo(errorMessage, commandLine);
        }
        return null;
    }


    @Override
    protected void doOKAction() {
        super.doOKAction();

        String serverId = UUID.randomUUID().toString();

        // Collect fileType/language mappings
        var languageMappings = languageMappingsPanel.getServerMappings();
        var fileTypeMappings = fileTypeMappingsPanel.getServerMappings();
        List<ServerMappingSettings> mappingSettings = new ArrayList<>(languageMappings);
        mappingSettings.addAll(fileTypeMappings);


        // Register language server and mappings definition
        String serverName = this.serverName.getText();
        String commandLine = this.commandLine.getText();
        UserDefinedLanguageServerDefinition definition = new UserDefinedLanguageServerDefinition(serverId, serverName, "", commandLine);
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

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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.vfs.VfsUtilCore;
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
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateDeserializer;
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
    private LanguageServerTemplate currentTemplate = null;
    private JButton showInstructionButton = null;

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

        showInstructionButton = super.createHelpButton(new JBInsets(0,0,0,0));
        showInstructionButton.setText("");
        TextFieldWithBrowseButton textFieldWithBrowseButton = new TextFieldWithBrowseButton();
        FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true,
                false, false, false, false);
        fileChooserDescriptor.setTitle(LanguageServerBundle.message("new.language.server.dialog.export.template.title"));
        fileChooserDescriptor.setDescription(LanguageServerBundle.message("new.language.server.dialog.export.template.description"));

        textFieldWithBrowseButton.addBrowseFolderListener(new TextBrowseFolderListener(fileChooserDescriptor, project) {
            @Override
            public void onFileChosen(@NotNull VirtualFile virtualFile) {
                super.onFileChosen(virtualFile);
                try {
                    loadFromTemplate(virtualFile);
                } catch (IOException e) {
                    Messages.showErrorDialog(project, e.getMessage(), LanguageServerBundle.message("new.language.server.dialog.export.template.error"));
                }
            }
        });
        panel.add(textFieldWithBrowseButton, BorderLayout.WEST);
        panel.add(showInstructionButton, BorderLayout.CENTER);
        builder.addLabeledComponent(LanguageServerBundle.message("new.language.server.dialog.template"), panel);
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

    private void loadFromTemplate(VirtualFile templateFolder) throws IOException {
        currentTemplate = null;

        String templateJson = null;
        String settingsJson = null;
        String initializationOptionsJson = null;
        String description = null;

        for (VirtualFile file : templateFolder.getChildren()) {
            if (file.isDirectory()) {
                continue;
            }
            switch (file.getName().toLowerCase()) {
                case "template.json":
                    templateJson = VfsUtilCore.loadText(file);
                    break;
                case "settings.json":
                    settingsJson = VfsUtilCore.loadText(file);
                    break;
                case "initializationoptions.json":
                    initializationOptionsJson = VfsUtilCore.loadText(file);
                    break;
                case "readme.md":
                    description = VfsUtilCore.loadText(file);
                    break;
                default:
                    break;
            }
        }

        if (templateJson == null) {
            // Don't continue, if no template.json is found
            return;
        }
        if (settingsJson == null) {
            settingsJson = "{}";
        }
        if (initializationOptionsJson == null) {
            initializationOptionsJson = "{}";
        }

        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(LanguageServerTemplate.class, new LanguageServerTemplateDeserializer());
        Gson gson = builder.create();

        LanguageServerTemplate template = gson.fromJson(templateJson, LanguageServerTemplate.class);
        template.setConfiguration(settingsJson);
        template.setInitializationOptions(initializationOptionsJson);
        if (StringUtils.isNotBlank(description)) {
            template.setDescription(description);
        }

        currentTemplate = template;
        showInstructionButton.setEnabled(StringUtils.isNotBlank(currentTemplate.getDescription()));
        loadFromTemplate(template);
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

}

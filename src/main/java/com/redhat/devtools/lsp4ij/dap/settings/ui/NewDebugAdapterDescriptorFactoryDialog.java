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
package com.redhat.devtools.lsp4ij.dap.settings.ui;

import com.google.common.collect.Streams;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBInsets;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplate;
import com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplateManager;
import com.redhat.devtools.lsp4ij.dap.descriptors.userdefined.UserDefinedDebugAdapterDescriptorFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * New Debug Adapter descriptor factory dialog.
 */
public class NewDebugAdapterDescriptorFactoryDialog extends DialogWrapper {

    private final ComboBox<DAPTemplate> templateCombo = new ComboBox<>(new DefaultComboBoxModel<>(getDAPTemplates()));
    private final Project project;

    private DebugAdapterDescriptorFactoryPanel dapDescriptorFactoryPanel;
    private DAPTemplate currentTemplate = null;
    private JButton showInstructionButton = null;
    private UserDefinedDebugAdapterDescriptorFactory createdFactory;

    private static DAPTemplate[] getDAPTemplates() {
        List<DAPTemplate> templates = new ArrayList<>();
        templates.add(DAPTemplate.NONE);
        templates.addAll(DAPTemplateManager.getInstance().getTemplates());
        return templates.toArray(new DAPTemplate[0]);
    }

    public NewDebugAdapterDescriptorFactoryDialog(@Nullable Project project) {
        super(project);
        this.project = project;
        super.setTitle(DAPBundle.message("new.debug.adapter.dialog.title"));
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
        this.dapDescriptorFactoryPanel = new DebugAdapterDescriptorFactoryPanel(builder, null, DebugAdapterDescriptorFactoryPanel.EditionMode.NEW_USER_DEFINED, project);
        dapDescriptorFactoryPanel.setServerId("");

        // Add validation
        addValidator(this.dapDescriptorFactoryPanel.getServerNameField());
        addValidator(this.dapDescriptorFactoryPanel.getCommandLineWidget());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(builder.getPanel(), BorderLayout.CENTER);
        return panel;
    }

    private void createTemplateCombo(FormBuilder builder) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        templateCombo.setRenderer(new SimpleListCellRenderer<>() {
            @Override
            public void customize(@NotNull JList list,
                                  @Nullable DAPTemplate value,
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
        builder.addLabeledComponent(DAPBundle.message("new.debug.adapter.dialog.template"), panel);
    }

    /**
     * Create the template combo listener that handles item selection
     *
     * @return created ItemListener
     */
    private ItemListener getTemplateComboListener() {
        return e -> {
            // Only trigger listener on selected items to avoid double triggering
            if (e.getStateChange() == ItemEvent.SELECTED) {
                currentTemplate = templateCombo.getItem();
                showInstructionButton.setEnabled(hasValidDescription(currentTemplate));
                if (currentTemplate != null) {
                    loadFromTemplate(currentTemplate);
                }
            }
        };
    }

    /**
     * Check that the template is not a placeholder and that it has a valid description
     *
     * @param template to check
     * @return true if template is not null, not a placeholder and has a description, else false
     */
    private boolean hasValidDescription(@Nullable DAPTemplate template) {
        return template != null && template != DAPTemplate.NONE
                && template != DAPTemplate.NEW_TEMPLATE;
    }

   /* @Override
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
    }*/

    private void loadFromTemplate(DAPTemplate template) {
        // Update name
        var serverName = this.dapDescriptorFactoryPanel.getServerNameField();
        serverName.setText(template.getName() != null ? template.getName() : "");

        // Update command
        String command = getCommandLine(template);
        this.dapDescriptorFactoryPanel.setCommandLine(command);

        // Update wait for trace
        this.dapDescriptorFactoryPanel.getDebugServerWaitStrategyPanel().update(null,
                template.getConnectTimeout(),
                template.getDebugServerReadyPattern());

        // Update mappings
        var mappingsPanel = this.dapDescriptorFactoryPanel.getMappingsPanel();
        mappingsPanel.refreshMappings(template.getLanguageMappings(), template.getFileTypeMappings());

        // Update launch/attach configuration
        dapDescriptorFactoryPanel.refreshLaunchConfigurations(template.getLaunchConfigurations());;
    }

    private static String getCommandLine(DAPTemplate entry) {
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
        return this.dapDescriptorFactoryPanel.getServerNameField();
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
        var serverName = this.dapDescriptorFactoryPanel.getServerNameField();
        if (serverName.getText().isBlank()) {
            String errorMessage = DAPBundle.message("new.debug.adapter.dialog.validation.serverName.must.be.set");
            return new ValidationInfo(errorMessage, serverName);
        }
        return null;
    }

    private ValidationInfo validateCommand() {
        var commandLine = this.dapDescriptorFactoryPanel.getCommandLine();
        if (commandLine.isBlank()) {
            String errorMessage = DAPBundle.message("new.debug.adapter.dialog.validation.commandLine.must.be.set");
            return new ValidationInfo(errorMessage, this.dapDescriptorFactoryPanel.getCommandLineWidget());
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
        // Register language server and mappings definition
        String serverName = this.dapDescriptorFactoryPanel.getServerNameField().getText();
        Map<String, String> userEnvironmentVariables = this.dapDescriptorFactoryPanel.getEnvironmentVariables().getEnvs();
        boolean includeSystemEnvironmentVariables = this.dapDescriptorFactoryPanel.getEnvironmentVariables().isPassParentEnvs();
        String commandLine = this.dapDescriptorFactoryPanel.getCommandLine();
        int connectTimeout = this.dapDescriptorFactoryPanel.getDebugServerWaitStrategyPanel().getConnectTimeout();
        String trackTrace = this.dapDescriptorFactoryPanel.getDebugServerWaitStrategyPanel().getTrace();
        var launchConfigurations = this.dapDescriptorFactoryPanel.getLaunchConfigurations();
        var mappingsPanel = dapDescriptorFactoryPanel.getMappingsPanel();
        createdFactory = new UserDefinedDebugAdapterDescriptorFactory(serverId,
                serverName,
                commandLine,
                mappingsPanel.getLanguageMappings(),
                Streams.concat(mappingsPanel.getFileTypeMappings().stream(),
                                mappingsPanel.getFileNamePatternMappings().stream())
                        .toList());
        createdFactory.setUserEnvironmentVariables(userEnvironmentVariables);
        createdFactory.setIncludeSystemEnvironmentVariables(includeSystemEnvironmentVariables);
        createdFactory.setConnectTimeout(connectTimeout);
        createdFactory.setDebugServerReadyPattern(trackTrace);
        createdFactory.setLaunchConfigurations(launchConfigurations);
        DebugAdapterManager.getInstance().addDebugAdapterDescriptorFactory(createdFactory);
    }

    private void addValidator(JTextComponent textComponent) {
        textComponent.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                NewDebugAdapterDescriptorFactoryDialog.super.initValidation();
            }
        });
    }

    @Override
    protected void dispose() {
        this.dapDescriptorFactoryPanel.dispose();
        super.dispose();
    }

    @Nullable
    public UserDefinedDebugAdapterDescriptorFactory getCreatedFactory() {
        return createdFactory;
    }
}

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
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplate;
import com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplateManager;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
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
 * New Debug Adapter Server dialog.
 */
public class NewDebugAdapterServerDialog extends DialogWrapper {

    private final ComboBox<DAPTemplate> templateCombo = new ComboBox<>(new DefaultComboBoxModel<>(getDAPTemplates()));
    private final Project project;

    private DebugAdapterServerPanel debugAdapterServerPanel;
    private DAPTemplate currentTemplate = null;
    private JButton showInstructionButton = null;
    private UserDefinedDebugAdapterServerDefinition createdServer;

    private static DAPTemplate[] getDAPTemplates() {
        List<DAPTemplate> templates = new ArrayList<>();
        templates.add(DAPTemplate.NONE);
        templates.addAll(DAPTemplateManager.getInstance().getTemplates());
        return templates.toArray(new DAPTemplate[0]);
    }

    public NewDebugAdapterServerDialog(@Nullable Project project) {
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
        this.debugAdapterServerPanel = new DebugAdapterServerPanel(builder, null, DebugAdapterServerPanel.EditionMode.NEW_USER_DEFINED, project);
        debugAdapterServerPanel.setServerId("");

        // Add validation
        addValidator(this.debugAdapterServerPanel.getServerNameField());
        addValidator(this.debugAdapterServerPanel.getCommandLineWidget());

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
        var serverName = this.debugAdapterServerPanel.getServerNameField();
        serverName.setText(template.getName() != null ? template.getName() : "");

        // Update command
        String command = getCommandLine(template);
        this.debugAdapterServerPanel.setCommandLine(command);

        // Update wait for trace
        this.debugAdapterServerPanel.getDebugServerWaitStrategyPanel().update(null,
                template.getConnectTimeout(),
                template.getDebugServerReadyPattern());

        // Update mappings
        var mappingsPanel = this.debugAdapterServerPanel.getMappingsPanel();
        mappingsPanel.refreshMappings(template.getLanguageMappings(), template.getFileTypeMappings());

        // Update launch/attach configuration
        debugAdapterServerPanel.refreshLaunchConfigurations(template.getLaunchConfigurations());

        // Attach (JSON path (ex:$port))
        debugAdapterServerPanel.setAttachAddress(StringUtils.isNotBlank(template.getAttachAddress()) ? template.getAttachAddress() : "");
        debugAdapterServerPanel.setAttachPort(StringUtils.isNotBlank(template.getAttachPort()) ? template.getAttachPort() : "");
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
        return this.debugAdapterServerPanel.getServerNameField();
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
        var serverName = this.debugAdapterServerPanel.getServerNameField();
        if (serverName.getText().isBlank()) {
            String errorMessage = DAPBundle.message("new.debug.adapter.dialog.validation.serverName.must.be.set");
            return new ValidationInfo(errorMessage, serverName);
        }
        return null;
    }

    private ValidationInfo validateCommand() {
        // Don't validate required command when DAP server supports only "attach"
        /*var commandLine = this.debugAdapterServerPanel.getCommandLine();
        if (commandLine.isBlank()) {
            String errorMessage = DAPBundle.message("new.debug.adapter.dialog.validation.commandLine.must.be.set");
            return new ValidationInfo(errorMessage, this.debugAdapterServerPanel.getCommandLineWidget());
        }*/
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
        String serverName = this.debugAdapterServerPanel.getServerNameField().getText();
        Map<String, String> userEnvironmentVariables = this.debugAdapterServerPanel.getEnvironmentVariables().getEnvs();
        boolean includeSystemEnvironmentVariables = this.debugAdapterServerPanel.getEnvironmentVariables().isPassParentEnvs();
        String commandLine = this.debugAdapterServerPanel.getCommandLine();
        int connectTimeout = this.debugAdapterServerPanel.getDebugServerWaitStrategyPanel().getConnectTimeout();
        String trackTrace = this.debugAdapterServerPanel.getDebugServerWaitStrategyPanel().getTrace();
        var launchConfigurations = this.debugAdapterServerPanel.getLaunchConfigurations();
        var mappingsPanel = debugAdapterServerPanel.getMappingsPanel();
        String attachAddress = debugAdapterServerPanel.getAttachAddress();
        String attachPort = debugAdapterServerPanel.getAttachPort();
        createdServer = new UserDefinedDebugAdapterServerDefinition(serverId,
                serverName,
                commandLine,
                mappingsPanel.getLanguageMappings(),
                Streams.concat(mappingsPanel.getFileTypeMappings().stream(),
                                mappingsPanel.getFileNamePatternMappings().stream())
                        .toList());
        createdServer.setUserEnvironmentVariables(userEnvironmentVariables);
        createdServer.setIncludeSystemEnvironmentVariables(includeSystemEnvironmentVariables);
        createdServer.setConnectTimeout(connectTimeout);
        createdServer.setDebugServerReadyPattern(trackTrace);
        createdServer.setLaunchConfigurations(launchConfigurations);
        createdServer.setAttachAddress(attachAddress);
        createdServer.setAttachPort(attachPort);
        DebugAdapterManager.getInstance().addDebugAdapterServer(createdServer);
    }

    private void addValidator(JTextComponent textComponent) {
        textComponent.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                NewDebugAdapterServerDialog.super.initValidation();
            }
        });
    }

    @Override
    protected void dispose() {
        this.debugAdapterServerPanel.dispose();
        super.dispose();
    }

    @Nullable
    public UserDefinedDebugAdapterServerDefinition getCreatedServer() {
        return createdServer;
    }
}

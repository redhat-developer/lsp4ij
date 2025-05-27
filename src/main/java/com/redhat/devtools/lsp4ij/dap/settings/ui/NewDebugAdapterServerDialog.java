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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.ui.FormBuilder;
import com.redhat.devtools.lsp4ij.ServerMessageHandler;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterServerListener;
import com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplate;
import com.redhat.devtools.lsp4ij.dap.settings.UserDefinedDebugAdapterServerSettings;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
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
 * New Debug Adapter Server dialog.
 */
public class NewDebugAdapterServerDialog extends DialogWrapper {

    private final Project project;

    private DebugAdapterServerPanel debugAdapterServerPanel;
    private DAPTemplate currentTemplate = null;
    private UserDefinedDebugAdapterServerDefinition createdServer;

    public NewDebugAdapterServerDialog(@Nullable Project project) {
        super(project);
        this.project = project;
        super.setTitle(DAPBundle.message("new.debug.adapter.dialog.title"));
        init();
        initValidation();
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
        // Create "Choose template..." hyperlink
        final var chooseTemplateHyperLink =  new HyperlinkLabel(DAPBundle.message("new.debug.adapter.dialog.choose.template"));
        chooseTemplateHyperLink.addHyperlinkListener(e -> {
            openChooseTemplatePopup(chooseTemplateHyperLink);
        });
        panel.add(chooseTemplateHyperLink, BorderLayout.WEST);
        builder.addLabeledComponent(DAPBundle.message("new.debug.adapter.dialog.template"), panel);
    }

    private void openChooseTemplatePopup(@NotNull Component button) {
        var searchTemplatePopupUI = new ChooseDebugServerTemplatePopupUI(this::loadFromTemplate);
        searchTemplatePopupUI.show(button);
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

        // Update installer
        debugAdapterServerPanel.setInstallerConfiguration(template.getInstallerConfiguration());
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
        String installerConfiguration = debugAdapterServerPanel.getInstallerConfiguration();
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
        createdServer.setInstallerConfiguration(installerConfiguration);
        DebugAdapterManager.getInstance().addDebugAdapterServer(createdServer);

        if (installerConfiguration != null && StringUtils.isNotBlank(installerConfiguration) && !installerConfiguration.equals("{}")) {
            if (MessageDialogBuilder.yesNo(DAPBundle.message("new.debug.adapter.dialog.install.title"),
                            DAPBundle.message("new.debug.adapter.dialog.install.content"))
                    .ask(project)) {
                try {
                    var context = new InstallerContext(project, InstallerContext.InstallerAction.CHECK_AND_RUN, new CommandLineUpdater() {

                        @Override
                        public String getCommandLine() {
                            return createdServer.getCommandLine();
                        }

                        @Override
                        public void setCommandLine(String commandLine) {
                            createdServer.setCommandLine(commandLine);

                            // Update command line settings
                            String serverId = createdServer.getId();
                            var settings = UserDefinedDebugAdapterServerSettings.getInstance().getSettings(serverId);
                            if (settings != null) {
                                settings.setCommandLine(commandLine);
                                UserDefinedDebugAdapterServerSettings.getInstance().setSettings(serverId, settings);
                            }

                            // Notifications
                            DebugAdapterServerListener.ChangedEvent event = new DebugAdapterServerListener.ChangedEvent(
                                    createdServer,
                                    false,
                                    true,
                                    false,
                                    false,
                                    false,
                                    false,
                                    false,
                                    false,
                                    false,
                                    false);
                            DebugAdapterManager.getInstance().handleChangeEvent(event);
                        }
                    });
                    ServerInstallerManager
                            .getInstance(project)
                            .install(installerConfiguration, context);
                } catch (Exception s) {
                    Notification notification = new Notification(ServerMessageHandler.LSP_WINDOW_SHOW_MESSAGE_GROUP_ID,
                            "Install error",
                            s.getMessage(),
                            NotificationType.ERROR);
                    Notifications.Bus.notify(notification, project);
                }
            }
        }
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

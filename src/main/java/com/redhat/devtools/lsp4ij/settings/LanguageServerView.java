/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.settings;

import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerFileAssociation;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.ui.LanguageServerPanel;
import com.redhat.devtools.lsp4ij.settings.ui.ServerMappingsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * UI settings view to configure a given language server:
 *
 * <ul>
 *     <li>Report language server error kind (None, In Notification, In Log)</li>
 *     <li>Server trace</li>
 *     <li>Debug port</li>
 *     <li>Suspend and wait for a debugger?</li>
 * </ul>
 */
public class LanguageServerView implements Disposable {

    private final LanguageServerNameProvider languageServerNameProvider;

    public interface LanguageServerNameProvider {
        String getDisplayName();
    }

    private final JPanel myMainPanel;
    private final LanguageServerDefinition languageServerDefinition;
    private final Project project;

    private LanguageServerPanel languageServerPanel;

    private ServerMappingsPanel mappingPanel;

    public LanguageServerView(@NotNull LanguageServerDefinition languageServerDefinition,
                              @Nullable LanguageServerNameProvider languageServerNameProvider,
                              @NotNull Project project
    ) {
        this.languageServerDefinition = languageServerDefinition;
        this.languageServerNameProvider = languageServerNameProvider;
        this.project = project;
        boolean isLaunchConfiguration = languageServerDefinition instanceof UserDefinedLanguageServerDefinition;
        JComponent descriptionPanel = createDescription(languageServerDefinition.getDescription());
        JPanel settingsPanel = createSettings(descriptionPanel, isLaunchConfiguration);
        if (!isLaunchConfiguration) {
            TitledBorder title = IdeBorderFactory.createTitledBorder(languageServerDefinition.getDisplayName());
            settingsPanel.setBorder(title);
        }
        JPanel wrapper = JBUI.Panels.simplePanel(settingsPanel);
        wrapper.setBorder(JBUI.Borders.emptyLeft(10));
        this.myMainPanel = wrapper;
    }

    /**
     * Returns true if there are some modification in the UI fields and false otherwise.
     *
     * @return true if there are some modification in the UI fields and false otherwise.
     */
    public boolean isModified() {
        String languageServerId = languageServerDefinition.getId();
        if (languageServerDefinition instanceof UserDefinedLanguageServerDefinition) {
            com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings = com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings.getInstance().getLaunchConfigSettings(languageServerId);
            if (settings == null) {
                return true;
            }
            if (!(isEquals(getDisplayName(), settings.getServerName())
                    && isEquals(this.getCommandLine(), settings.getCommandLine())
                    && Objects.equals(this.getEnvData().getEnvs(), settings.getUserEnvironmentVariables())
                    && this.getEnvData().isPassParentEnvs() == settings.isIncludeSystemEnvironmentVariables()
                    && Objects.deepEquals(this.getMappings(), settings.getMappings())
                    && isEquals(this.getConfigurationContent(), settings.getConfigurationContent())
                    && isEquals(this.getConfigurationSchemaContent(), settings.getConfigurationSchemaContent())
                    && isEquals(this.getInitializationOptionsContent(), settings.getInitializationOptionsContent())
                    && isEquals(this.getClientConfigurationContent(), settings.getClientConfigurationContent()))) {
                return true;
            }
        }
        com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(languageServerId);
        if (settings == null) {
            // There is no settings, check if the view is filled with default value
            return !(!this.isDebugSuspend()
                    && StringUtils.isEmpty(this.getDebugPort())
                    && isEquals(this.getServerTrace(), ServerTrace.getDefaultValue())
                    && isEquals(this.getReportErrorKind(), ErrorReportingKind.getDefaultValue()));
        }
        if (!(languageServerDefinition instanceof UserDefinedLanguageServerDefinition)) {
            if (!(isEquals(this.getDebugPort(), settings.getDebugPort())
                    && this.isDebugSuspend() == settings.isDebugSuspend())) {
                return true;
            }
        }
        return !(isEquals(this.getServerTrace(), settings.getServerTrace()) &&
                isEquals(this.getReportErrorKind(), settings.getErrorReportingKind()));
    }

    static boolean isEquals(String s1, String s2) {
        // the comparison between null and "" should return true
        s1 = s1 == null ? "" : s1;
        s2 = s2 == null ? "" : s2;
        return Objects.equals(s1, s2);
    }

    static boolean isEquals(ServerTrace st1, ServerTrace st2) {
        // the comparison between null and default value trace should return true
        st1 = st1 == null ? ServerTrace.getDefaultValue() : st1;
        st2 = st2 == null ? ServerTrace.getDefaultValue() : st2;
        return Objects.equals(st1, st2);
    }

    static boolean isEquals(ErrorReportingKind k1, ErrorReportingKind k2) {
        // the comparison between null and default value error reporting kind should return true
        k1 = k1 == null ? ErrorReportingKind.getDefaultValue() : k1;
        k2 = k2 == null ? ErrorReportingKind.getDefaultValue() : k2;
        return Objects.equals(k1, k2);
    }

    /**
     * Update the UI from the registered language server definition + settings.
     */
    public void reset() {
        String languageServerId = languageServerDefinition.getId();

        // Commons settings (user defined language server + extension point)
        UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = UserDefinedLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(languageServerId);
        final ErrorReportingKind errorReportingKind = settings != null && settings.getErrorReportingKind() != null ? settings.getErrorReportingKind() : ErrorReportingKind.as_notification;
        final ServerTrace serverTrace = settings != null && settings.getServerTrace() != null ? settings.getServerTrace() : ServerTrace.off;
        this.setReportErrorKind(errorReportingKind);
        this.setServerTrace(serverTrace);

        if (languageServerDefinition instanceof UserDefinedLanguageServerDefinition) {
            // User defined language server
            com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings userDefinedLanguageServerSettings = com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings.getInstance().getLaunchConfigSettings(languageServerId);
            if (userDefinedLanguageServerSettings != null) {
                this.setCommandLine(userDefinedLanguageServerSettings.getCommandLine());
                this.setEnvData(EnvironmentVariablesData.create(
                        userDefinedLanguageServerSettings.getUserEnvironmentVariables(),
                        userDefinedLanguageServerSettings.isIncludeSystemEnvironmentVariables()));
                this.setConfigurationContent(userDefinedLanguageServerSettings.getConfigurationContent());
                this.setConfigurationSchemaContent(userDefinedLanguageServerSettings.getConfigurationSchemaContent());
                this.setInitializationOptionsContent(userDefinedLanguageServerSettings.getInitializationOptionsContent());
                this.setClientConfigurationContent(userDefinedLanguageServerSettings.getClientConfigurationContent());

                List<ServerMappingSettings> languageMappings = userDefinedLanguageServerSettings.getMappings()
                        .stream()
                        .filter(mapping -> !StringUtils.isEmpty(mapping.getLanguage()))
                        .map(mapping ->
                            ServerMappingSettings.createLanguageMappingSettings(mapping.getLanguage(), mapping.getLanguageId())
                        )
                        .collect(Collectors.toList());
                this.setLanguageMappings(languageMappings);

                List<ServerMappingSettings> fileTypeMappings = userDefinedLanguageServerSettings.getMappings()
                        .stream()
                        .filter(mapping -> !StringUtils.isEmpty(mapping.getFileType()))
                        .map(mapping ->
                            ServerMappingSettings.createFileTypeMappingSettings(mapping.getFileType(), mapping.getLanguageId())
                        )
                        .collect(Collectors.toList());
                this.setFileTypeMappings(fileTypeMappings);

                List<ServerMappingSettings> fileNamePatternMappings = userDefinedLanguageServerSettings.getMappings()
                        .stream()
                        .filter(mapping -> mapping.getFileNamePatterns() != null)
                        .map(mapping ->
                                ServerMappingSettings.createFileNamePatternsMappingSettings(mapping.getFileNamePatterns(), mapping.getLanguageId())
                        )
                        .collect(Collectors.toList());
                this.setFileNamePatternMappings(fileNamePatternMappings);
            }
        } else {
            // Language server from extension point
            if (settings != null) {
                this.setDebugPort(settings.getDebugPort());
                this.setDebugSuspend(settings.isDebugSuspend());
            }
            List<LanguageServerFileAssociation> mappings = LanguageServersRegistry.getInstance().findLanguageServerDefinitionFor(languageServerId);
            List<ServerMappingSettings> languageMappings = mappings
                    .stream()
                    .filter(mapping -> mapping.getLanguage() != null)
                    .map(mapping -> {
                        Language language = mapping.getLanguage();
                        String languageId = mapping.getLanguageId();
                        return ServerMappingSettings.createLanguageMappingSettings(language.getID(), languageId);
                    })
                    .collect(Collectors.toList());
            this.setLanguageMappings(languageMappings);

            List<ServerMappingSettings> fileTypeMappings = mappings
                    .stream()
                    .filter(mapping -> mapping.getFileType() != null)
                    .map(mapping -> {
                        FileType fileType = mapping.getFileType();
                        String languageId = mapping.getLanguageId();
                        return ServerMappingSettings.createFileTypeMappingSettings(fileType.getName(), languageId);
                    })
                    .collect(Collectors.toList());
            this.setFileTypeMappings(fileTypeMappings);

            List<ServerMappingSettings> fileNamePatternMappings = mappings
                    .stream()
                    .filter(mapping -> mapping.getFileNameMatchers() != null)
                    .map(mapping -> {
                        List<FileNameMatcher> matchers = mapping.getFileNameMatchers();
                        String languageId = mapping.getLanguageId();
                        return ServerMappingSettings.createFileNamePatternsMappingSettings(matchers.
                                stream()
                                .map(FileNameMatcher::getPresentableString)
                                .toList(), languageId);
                    })
                    .collect(Collectors.toList());
            this.setFileNamePatternMappings(fileNamePatternMappings);
        }
    }

    /**
     * Update the proper language server settings and language server definition from the UI fields.
     */
    public void apply() {
        String languageServerId = languageServerDefinition.getId();

        // Update commons settings
        com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = new com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings();
        settings.setServerTrace(getServerTrace());
        settings.setErrorReportingKind(getReportErrorKind());

        if (languageServerDefinition instanceof UserDefinedLanguageServerDefinition launch) {
            // Register settings and server definition without firing events
            var settingsChangedEvent = com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings
                    .getInstance(project)
                    .updateSettings(languageServerId, settings, false);
            // Update user-defined language server settings
            var serverChangedEvent = LanguageServersRegistry.getInstance()
                    .updateServerDefinition(
                            new LanguageServersRegistry.UpdateServerDefinitionRequest(project,
                                    launch,
                                    getDisplayName(),
                                    getCommandLine(),
                                    getEnvData().getEnvs(),
                                    getEnvData().isPassParentEnvs(),
                                    getMappings(),
                                    getConfigurationContent(),
                                    getConfigurationSchemaContent(),
                                    getInitializationOptionsContent(),
                                    getClientConfigurationContent()),
                            false);
            if (settingsChangedEvent != null) {
                // Settings has changed, fire the event
                com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings
                        .getInstance(project)
                        .handleChanged(settingsChangedEvent);
            }
            if (serverChangedEvent != null) {
                // Server definition has changed, fire the event
                LanguageServersRegistry.getInstance().handleChangeEvent(serverChangedEvent);
            }
        } else {
            // Update user-defined language server settings
            settings.setDebugPort(getDebugPort());
            settings.setDebugSuspend(isDebugSuspend());
            com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.getInstance(project)
                    .updateSettings(languageServerId, settings);
        }
    }

    private String getDisplayName() {
        return languageServerNameProvider != null ? languageServerNameProvider.getDisplayName() : languageServerDefinition.getDisplayName();
    }

    private JPanel createSettings(JComponent description, boolean launchingServerDefinition) {
        FormBuilder builder = FormBuilder
                .createFormBuilder()
                .setFormLeftIndent(10);
        this.languageServerPanel = new LanguageServerPanel(builder,
                description,
                launchingServerDefinition ? LanguageServerPanel.EditionMode.EDIT_USER_DEFINED :
                        LanguageServerPanel.EditionMode.EDIT_EXTENSION, project);
        this.mappingPanel = languageServerPanel.getMappingsPanel();
        return builder.getPanel();
    }

    private JComponent createDescription(String description) {
        /**
         * Normally comments are below the controls.
         * Here we want the comments to precede the controls, we therefore create an empty, 0-sized panel.
         */
        JPanel titledComponent = UI.PanelFactory.grid().createPanel();
        titledComponent.setMinimumSize(JBUI.emptySize());
        titledComponent.setPreferredSize(JBUI.emptySize());
        if (description == null) {
            description = "";
        }
        description = description.trim();
        if (!description.isBlank()) {
            titledComponent = UI.PanelFactory.panel(titledComponent)
                    .withComment(description)
                    .resizeX(true)
                    .resizeY(true)
                    .createPanel();
        }
        return titledComponent;
    }

    public JComponent getComponent() {
        return myMainPanel;
    }

    public String getDebugPort() {
        var debugPortField = languageServerPanel.getDebugPortField();
        return debugPortField.getNumber() <= 0 ? "" : Integer.toString(debugPortField.getNumber());
    }

    public void setDebugPort(String debugPort) {
        var debugPortField = languageServerPanel.getDebugPortField();
        int port = 0;
        try {
            port = Integer.parseInt(debugPort);
            if (port < debugPortField.getMin() || port > debugPortField.getMax()) {
                port = 0;
            }
        } catch (Exception ignore) {
        }
        debugPortField.setNumber(port);
    }

    public boolean isDebugSuspend() {
        return languageServerPanel.getDebugSuspendCheckBox().isSelected();
    }

    public void setDebugSuspend(boolean debugSuspend) {
        languageServerPanel.getDebugSuspendCheckBox().setSelected(debugSuspend);
    }

    public ServerTrace getServerTrace() {
        return (ServerTrace) languageServerPanel.getServerTraceComboBox().getSelectedItem();
    }

    public void setServerTrace(ServerTrace serverTrace) {
        languageServerPanel.getServerTraceComboBox().setSelectedItem(serverTrace);
    }

    public ErrorReportingKind getReportErrorKind() {
        return (ErrorReportingKind) languageServerPanel.getErrorReportingKindCombo().getSelectedItem();
    }

    public void setReportErrorKind(ErrorReportingKind errorReportingKind) {
        languageServerPanel.getErrorReportingKindCombo().setSelectedItem(errorReportingKind);
    }

    public String getCommandLine() {
        return languageServerPanel.getCommandLine().getText();
    }

    public void setCommandLine(String commandLine) {
        languageServerPanel.getCommandLine().setText(commandLine);
    }

    public void setEnvData(EnvironmentVariablesData envData) {
        if (envData != null) {
            languageServerPanel.getEnvironmentVariables().setEnvData(envData);
        }
    }

    public @NotNull EnvironmentVariablesData getEnvData() {
        return languageServerPanel.getEnvironmentVariables().getEnvData();
    }

    public void setLanguageMappings(@NotNull List<ServerMappingSettings> mappings) {
        mappingPanel.setLanguageMappings(mappings);
    }

    public void setFileTypeMappings(@NotNull List<ServerMappingSettings> mappings) {
        mappingPanel.setFileTypeMappings(mappings);
    }

    public void setFileNamePatternMappings(List<ServerMappingSettings> mappings) {
        mappingPanel.setFileNamePatternMappings(mappings);
    }

    public String getConfigurationContent() {
        return languageServerPanel.getConfiguration().getText();
    }

    public void setConfigurationContent(String configurationContent) {
        var configuration = languageServerPanel.getConfiguration();
        configuration.setText(configurationContent);
        configuration.setCaretPosition(0);
    }

    public String getConfigurationSchemaContent() {
        return languageServerPanel.getConfigurationSchemaContent();
    }

    public void setConfigurationSchemaContent(String configurationSchemaContent) {
        languageServerPanel.setConfigurationSchemaContent(configurationSchemaContent);
    }

    public String getInitializationOptionsContent() {
        return languageServerPanel.getInitializationOptionsWidget().getText();
    }

    public void setInitializationOptionsContent(String initializationOptionsContent) {
        var initializationOptions = languageServerPanel.getInitializationOptionsWidget();
        initializationOptions.setText(initializationOptionsContent);
        initializationOptions.setCaretPosition(0);
    }

    public String getClientConfigurationContent() {
        return languageServerPanel.getClientConfigurationWidget().getText();
    }

    public void setClientConfigurationContent(String configurationContent) {
        var clientConfiguration = languageServerPanel.getClientConfigurationWidget();
        clientConfiguration.setText(configurationContent);
        clientConfiguration.setCaretPosition(0);
    }

    @Override
    public void dispose() {
        languageServerPanel.dispose();
    }

    public List<ServerMappingSettings> getMappings() {
        return mappingPanel.getAllMappings();
    }


    /**
     * Returns true if the command is editing and false otherwise.
     *
     * @return true if the command is editing and false otherwise.
     */
    public boolean isEditingCommand() {
        return languageServerPanel.getCommandLine() != null && languageServerPanel.getCommandLine().hasFocus();
    }
}

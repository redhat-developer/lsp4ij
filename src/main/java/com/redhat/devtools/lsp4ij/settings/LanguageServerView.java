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
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.redhat.devtools.lsp4ij.LanguageServerManager;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import com.redhat.devtools.lsp4ij.launching.ui.UICommandLineUpdater;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerFileAssociation;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.ui.LanguageServerPanel;
import com.redhat.devtools.lsp4ij.settings.ui.ServerMappingsPanel;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.util.List;
import java.util.Map;
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
    private final boolean canExecuteInstaller;
    private final JPanel myMainPanel;
    private final LanguageServerDefinition languageServerDefinition;
    private final Project project;
    private LanguageServerPanel languageServerPanel;
    private ServerMappingsPanel mappingPanel;

    public LanguageServerView(@NotNull LanguageServerDefinition languageServerDefinition,
                              @Nullable LanguageServerNameProvider languageServerNameProvider,
                              boolean canExecuteInstaller,
                              @NotNull Project project) {
        this.canExecuteInstaller = canExecuteInstaller;
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

    private static @Nullable String getUrl(UserDefinedLanguageServerDefinition definition) {
        String url = definition.getUrl();
        if (url != null) {
            return url;
        }
        String templateId = definition.getTemplateId();
        if (templateId != null) {
            var template = LanguageServerTemplateManager.getInstance().findTemplateById(templateId);
            return template != null ? template.getUrl() : null;
        }
        return null;
    }

    /**
     * Returns true if there are some modification in the UI fields and false otherwise.
     *
     * @return true if there are some modification in the UI fields and false otherwise.
     */
    public boolean isModified() {
        String languageServerId = languageServerDefinition.getId();

        // Check if there are some modification for user defined language server
        if (languageServerDefinition instanceof UserDefinedLanguageServerDefinition) {
            // Check is user defined language server settings has changed
            UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings = UserDefinedLanguageServerSettings.getInstance()
                    .getUserDefinedLanguageServerSettings(languageServerId);
            if (settings == null) {
                return true;
            }
            if (!(isEquals(getDisplayName(), settings.getServerName())
                    && isEquals(this.getCommandLine(), settings.getCommandLine())
                    && Objects.equals(this.getUserEnvironmentVariables(), settings.getUserEnvironmentVariables())
                    && this.isIncludeSystemEnvironmentVariables() == settings.isIncludeSystemEnvironmentVariables()
                    && Objects.deepEquals(this.getMappings(), settings.getMappings())
                    && isEquals(this.getClientConfigurationContent(), settings.getClientConfigurationContent())
                    && isEquals(this.getInstallerConfigurationContent(), settings.getInstallerConfigurationContent()))) {
                return true;
            }
        }

        // Check if there are some modification from commons settings (project scope)
        ProjectLanguageServerSettings.LanguageServerDefinitionSettings projectSettings = ProjectLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(languageServerId);
        if (isProjectScopeModified(projectSettings)) {
            return true;
        }

        // Check if there are some modification from commons settings (global scope)
        GlobalLanguageServerSettings.LanguageServerDefinitionSettings globalSettings = GlobalLanguageServerSettings.getInstance()
                .getLanguageServerSettings(languageServerId);
        return isGlobalScopeModified(globalSettings);
    }

    private boolean isProjectScopeModified(LanguageServerSettings.LanguageServerDefinitionSettings settings) {
        if (settings == null) {
            // There is no settings, check if the view is filled with default value
            return !(!this.isDebugSuspend()
                    && StringUtils.isEmpty(this.getDebugPort())
                    && isEquals(this.getServerTrace(), ServerTrace.getDefaultValue())
                    && isEquals(this.getReportErrorKind(), ErrorReportingKind.getDefaultValue())
                    && !this.isUseIntegerIds()
            );
        }

        // The settings exist.
        return (!(isEquals(this.getServerTrace(), settings.getServerTrace()) &&
                isEquals(this.getReportErrorKind(), settings.getErrorReportingKind())
                && isEquals(this.getDebugPort(), settings.getDebugPort())
                && this.isDebugSuspend() == settings.isDebugSuspend()
                && this.isUseIntegerIds() == settings.isUseIntegerIds()));
    }

    private boolean isGlobalScopeModified(LanguageServerSettings.LanguageServerDefinitionSettings settings) {
        if (settings == null) {
            return false;
        }

        // The settings exist.
        return (!(isEquals(this.getConfigurationContent(), settings.getConfigurationContent())
                && isEquals(this.getConfigurationSchemaContent(), settings.getConfigurationSchemaContent())
                && this.isExpandConfiguration() == settings.isExpandConfiguration()
                && isEquals(this.getInitializationOptionsContent(), settings.getInitializationOptionsContent())
                && isEquals(this.getExperimentalContent(), settings.getExperimentalContent())));
    }

    /**
     * Update the UI from the registered language server definition + settings.
     */
    public void reset() {
        String languageServerId = languageServerDefinition.getId();

        // Commons settings (user defined language server + extension point)
        ProjectLanguageServerSettings.LanguageServerDefinitionSettings projectSettings = ProjectLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(languageServerId);
        final ErrorReportingKind errorReportingKind = projectSettings != null && projectSettings.getErrorReportingKind() != null ? projectSettings.getErrorReportingKind() : ErrorReportingKind.as_notification;
        final ServerTrace serverTrace = projectSettings != null && projectSettings.getServerTrace() != null ? projectSettings.getServerTrace() : ServerTrace.off;
        this.setReportErrorKind(errorReportingKind);
        this.setServerTrace(serverTrace);
        
        // Load project-specific settings for all language servers
        if (projectSettings != null) {
            this.setDebugPort(projectSettings.getDebugPort());
            this.setDebugSuspend(projectSettings.isDebugSuspend());
            this.setUseIntegerIds(projectSettings.isUseIntegerIds());
        }

        GlobalLanguageServerSettings.LanguageServerDefinitionSettings globalSettings = GlobalLanguageServerSettings.getInstance()
                .getLanguageServerSettings(languageServerId);
        if (globalSettings != null) {
            this.setConfigurationContent(globalSettings.getConfigurationContent());
            this.setExpandConfiguration(globalSettings.isExpandConfiguration());
            this.setConfigurationSchemaContent(globalSettings.getConfigurationSchemaContent());
            this.setInitializationOptionsContent(globalSettings.getInitializationOptionsContent());
            this.setExperimentalContent(globalSettings.getExperimentalContent());
        }

        if (languageServerDefinition instanceof UserDefinedLanguageServerDefinition userDef) {
            UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings userSettings = UserDefinedLanguageServerSettings.getInstance()
                    .getUserDefinedLanguageServerSettings(languageServerId);
            this.setServerUrl(getUrl(userDef));
            
            // User defined language server
            if (userSettings != null) {
                this.setCommandLine(userSettings.getCommandLine());
                this.setEnvData(EnvironmentVariablesData.create(
                        userSettings.getUserEnvironmentVariables(),
                        userSettings.isIncludeSystemEnvironmentVariables()));
                this.setClientConfigurationContent(userSettings.getClientConfigurationContent());
                this.setInstallerConfigurationContent(userSettings.getInstallerConfigurationContent());

                List<ServerMappingSettings> languageMappings = userSettings.getMappings()
                        .stream()
                        .filter(mapping -> !StringUtils.isEmpty(mapping.getLanguage()))
                        .map(mapping ->
                                ServerMappingSettings.createLanguageMappingSettings(mapping.getLanguage(), mapping.getLanguageId())
                        )
                        .collect(Collectors.toList());
                this.setLanguageMappings(languageMappings);

                List<ServerMappingSettings> fileTypeMappings = userSettings.getMappings()
                        .stream()
                        .filter(mapping -> !StringUtils.isEmpty(mapping.getFileType()))
                        .map(mapping ->
                                ServerMappingSettings.createFileTypeMappingSettings(mapping.getFileType(), mapping.getLanguageId())
                        )
                        .collect(Collectors.toList());
                this.setFileTypeMappings(fileTypeMappings);

                List<ServerMappingSettings> fileNamePatternMappings = userSettings.getMappings()
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
        ProjectLanguageServerSettings.LanguageServerDefinitionSettings projectSettings = new ProjectLanguageServerSettings.LanguageServerDefinitionSettings();
        projectSettings.setServerTrace(getServerTrace());
        projectSettings.setErrorReportingKind(getReportErrorKind());
        projectSettings.setDebugPort(getDebugPort());
        projectSettings.setDebugSuspend(isDebugSuspend());
        projectSettings.setUseIntegerIds(isUseIntegerIds());

        // Update contribute settings
        GlobalLanguageServerSettings.LanguageServerDefinitionSettings globalSettings = new GlobalLanguageServerSettings.LanguageServerDefinitionSettings();
        globalSettings.setConfigurationContent(getConfigurationContent());
        globalSettings.setExpandConfiguration(isExpandConfiguration());
        globalSettings.setConfigurationSchemaContent(getConfigurationSchemaContent());
        globalSettings.setInitializationOptionsContent(getInitializationOptionsContent());
        globalSettings.setExperimentalContent(getExperimentalContent());

        // Register project settings  without firing events
        var projectSettingsChangedEvent = ProjectLanguageServerSettings
                .getInstance(project)
                .updateSettings(languageServerId, projectSettings, false);

        // Register global settings without firing events
        var globalSettingsChangedEvent = GlobalLanguageServerSettings
                .getInstance()
                .updateSettings(languageServerId, globalSettings, false);

        if (languageServerDefinition instanceof UserDefinedLanguageServerDefinition) {
            // Update user-defined language server definition
            var serverChangedEvent = LanguageServersRegistry.getInstance()
                    .updateServerDefinition(
                            new LanguageServersRegistry.UpdateServerDefinitionRequest(project,
                                    languageServerDefinition,
                                    getDisplayName(),
                                    getServerUrl(),
                                    getCommandLine(),
                                    getEnvData() != null ? getEnvData().getEnvs() : null,
                                    isIncludeSystemEnvironmentVariables(),
                                    getMappings(),
                                    getClientConfigurationContent(),
                                    getInstallerConfigurationContent()),
                            false);
            if (projectSettingsChangedEvent != null) {
                // Settings has changed, fire the event
                ProjectLanguageServerSettings
                        .getInstance(project)
                        .handleChanged(projectSettingsChangedEvent);
            }
            if (globalSettingsChangedEvent != null) {
                // Settings has changed, fire the event
                GlobalLanguageServerSettings
                        .getInstance()
                        .handleChanged(globalSettingsChangedEvent);
            }
            if (serverChangedEvent != null) {
                // Server definition has changed, fire the event
                LanguageServersRegistry.getInstance().handleChangeEvent(serverChangedEvent);
            }
        } else {
            if (projectSettingsChangedEvent != null) {
                // Settings has changed, fire the event
                ProjectLanguageServerSettings
                        .getInstance(project)
                        .handleChanged(projectSettingsChangedEvent);
            }
            if (globalSettingsChangedEvent != null) {
                // Settings has changed, fire the event
                GlobalLanguageServerSettings
                        .getInstance()
                        .handleChanged(globalSettingsChangedEvent);
            }
        }
    }

    private String getDisplayName() {
        return languageServerNameProvider != null ? languageServerNameProvider.getDisplayName() : languageServerDefinition.getDisplayName();
    }

    private JPanel createSettings(JComponent description, boolean launchingServerDefinition) {
        FormBuilder builder = FormBuilder
                .createFormBuilder()
                .setFormLeftIndent(10);
        var uiConfiguration = createUIConfiguration();
        this.languageServerPanel = new LanguageServerPanel(builder,
                description,
                uiConfiguration,
                canExecuteInstaller,
                project);
        if (languageServerDefinition instanceof UserDefinedLanguageServerDefinition def) {
            languageServerPanel.setCommandLineUpdater(new UICommandLineUpdater(def, project));
        }
        // Stop and disable the language server when installation is started
        languageServerPanel.addPreInstallAction(() -> {
            // Stop all servers from all opened project
            Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
            for (Project project : openProjects) {
                if (!project.isDisposed()) {
                    LanguageServerManager.getInstance(project)
                            .stop(languageServerDefinition.getId());
                    languageServerDefinition.setEnabled(false, project);
                }
            }
        });
        // Re-enable the language server when installation is terminated
        languageServerPanel.addPostInstallAction(() -> {
            languageServerDefinition.setEnabled(true, project);
        });
        this.mappingPanel = languageServerPanel.getMappingsPanel();
        return builder.getPanel();
    }

    private UIConfiguration createUIConfiguration() {
        boolean isUserDefined = languageServerDefinition instanceof UserDefinedLanguageServerDefinition;
        var settingsContributor = languageServerDefinition.getLanguageServerSettingsContributor();
        UIConfiguration configuration = new UIConfiguration();

        // Server tab configuration
        configuration.setShowServerName(false);
        configuration.setShowCommandLine(isUserDefined);

        // Mappings tab configuration
        configuration.setServerMappingsEditable(isUserDefined);

        // Configuration tab configuration
        configuration.setShowServerConfiguration(settingsContributor != null && settingsContributor.getServerConfigurationContributor() != null);
        configuration.setShowServerInitializationOptions(settingsContributor != null && settingsContributor.getServerInitializationOptionsContributor() != null);
        configuration.setShowServerExperimental(settingsContributor != null && settingsContributor.getServerExperimentalContributor() != null);
        configuration.setShowClientConfiguration(isUserDefined);

        // Debug tab configuration
        configuration.setShowDebug(true);
        configuration.setShowDebugPortAndSuspend(isUserDefined);

        // Installer tab configuration
        configuration.setShowInstaller(isUserDefined);

        return configuration;
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

    public boolean isUseIntegerIds() {
        return languageServerPanel.getUseIntegerIdsCheckBox().isSelected();
    }

    public void setUseIntegerIds(boolean useIntegerIds) {
        languageServerPanel.getUseIntegerIdsCheckBox().setSelected(useIntegerIds);
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

    public @Nullable String getCommandLine() {
        return languageServerPanel.getCommandLine();
    }

    public void setCommandLine(@Nullable String commandLine) {
        languageServerPanel.setCommandLine(commandLine);
    }

    public String getServerUrl() {
        return languageServerPanel.getServerUrl();
    }

    private void setServerUrl(@Nullable String url) {
        languageServerPanel.setServerUrl(url);
    }

    public boolean isIncludeSystemEnvironmentVariables() {
        return languageServerPanel.isIncludeSystemEnvironmentVariables();
    }

    public void setEnvData(@Nullable EnvironmentVariablesData envData) {
        languageServerPanel.setEnvData(envData);
    }

    public @Nullable EnvironmentVariablesData getEnvData() {
        return languageServerPanel.getEnvData();
    }

    public @Nullable Map<String, String> getUserEnvironmentVariables() {
        return languageServerPanel.getUserEnvironmentVariables();
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

    public @Nullable String getConfigurationContent() {
        return languageServerPanel.getConfigurationContent();
    }

    public void setConfigurationContent(@Nullable String configurationContent) {
        languageServerPanel.setConfigurationContent(configurationContent);
    }

    public boolean isExpandConfiguration() {
        return languageServerPanel.isExpandConfiguration();
    }

    public void setExpandConfiguration(boolean expandConfiguration) {
        languageServerPanel.setExpandConfiguration(expandConfiguration);
    }

    public String getConfigurationSchemaContent() {
        return languageServerPanel.getConfigurationSchemaContent();
    }

    public void setConfigurationSchemaContent(String configurationSchemaContent) {
        languageServerPanel.setConfigurationSchemaContent(configurationSchemaContent);
    }

    public String getInitializationOptionsContent() {
        return languageServerPanel.getInitializationOptionsContent();
    }

    public void setInitializationOptionsContent(String initializationOptionsContent) {
        languageServerPanel.setInitializationOptionsContent(initializationOptionsContent);
    }

    public String getExperimentalContent() {
        return languageServerPanel.getExperimentalContent();
    }

    public void setExperimentalContent(String experimentalContent) {
        languageServerPanel.setExperimentalContent(experimentalContent);
    }

    public String getClientConfigurationContent() {
        return languageServerPanel.getClientConfigurationContent();
    }

    public void setClientConfigurationContent(String clientConfigurationContent) {
        languageServerPanel.setClientConfigurationContent(clientConfigurationContent);
    }

    public String getInstallerConfigurationContent() {
        return languageServerPanel.getInstallerConfigurationContent();
    }

    public void setInstallerConfigurationContent(String installerConfigurationContent) {
        languageServerPanel.setInstallerConfigurationContent(installerConfigurationContent);
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
        return languageServerPanel.getCommandLineWidget() != null && languageServerPanel.getCommandLineWidget().hasFocus();
    }


    public interface LanguageServerNameProvider {
        String getDisplayName();
    }
}

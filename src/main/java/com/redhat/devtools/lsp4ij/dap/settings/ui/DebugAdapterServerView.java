/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
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
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.LaunchConfiguration;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPServerMappingsPanel;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.settings.UserDefinedDebugAdapterServerSettings;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * UI view to define a Debug Adapter Server.
 */
public class DebugAdapterServerView implements Disposable {

    private final DebugAdapterServerNameProvider debugAdapterServerNameProvider;
    private final boolean isUserDefined;
    private final Project project;

    public interface DebugAdapterServerNameProvider {
        String getDisplayName();
    }

    private final JPanel myMainPanel;
    private final @NotNull DebugAdapterServerDefinition debugAdapterServer;

    private DebugAdapterServerPanel debugAdapterServerPanel;

    private DAPServerMappingsPanel mappingPanel;

    public DebugAdapterServerView(@NotNull DebugAdapterServerDefinition debugAdapterServer,
                                  @Nullable DebugAdapterServerView.DebugAdapterServerNameProvider dapDescriptorFactoryNameProvider,
                                  @NotNull Project project) {
        this.project = project;
        this.debugAdapterServer = debugAdapterServer;
        this.debugAdapterServerNameProvider = dapDescriptorFactoryNameProvider;
        isUserDefined = debugAdapterServer instanceof UserDefinedDebugAdapterServerDefinition;
        JComponent descriptionPanel = createDescription(debugAdapterServer.getDescription());
        JPanel settingsPanel = createSettings(descriptionPanel, isUserDefined);
        if (!isUserDefined) {
            TitledBorder title = IdeBorderFactory.createTitledBorder(debugAdapterServer.getDisplayName());
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
        String descriptorFactoryId = debugAdapterServer.getId();
        if (isUserDefined) {
            UserDefinedDebugAdapterServerSettings.ItemSettings settings = UserDefinedDebugAdapterServerSettings.getInstance().getSettings(descriptorFactoryId);
            if (settings == null) {
                return true;
            }
            return !(isEquals(getDisplayName(), settings.getServerName())
                    && isEquals(this.getCommandLine(), settings.getCommandLine())
                    && Objects.equals(this.getEnvData().getEnvs(), settings.getUserEnvironmentVariables())
                    && this.getEnvData().isPassParentEnvs() == settings.isIncludeSystemEnvironmentVariables()
                    && this.getConnectTimeout() == settings.getConnectTimeout()
                    && isEquals(this.getDebugServerReadyPattern(), settings.getDebugServerReadyPattern())
                    && isEquals(this.getAttachAddress(), settings.getAttachAddress())
                    && isEquals(this.getAttachPort(), settings.getAttachPort())
                    && Objects.equals(this.getMappings(), settings.getMappings()));
        }
        return false;
    }

    static boolean isEquals(String s1, String s2) {
        // the comparison between null and "" should return true
        s1 = s1 == null ? "" : s1;
        s2 = s2 == null ? "" : s2;
        return Objects.equals(s1, s2);
    }

    /**
     * Update the UI from the registered language server definition + settings.
     */
    public void reset() {
        String debugAdapterServerId = debugAdapterServer.getId();

        if (isUserDefined) {
            // User defined language server
            UserDefinedDebugAdapterServerSettings.ItemSettings settings = UserDefinedDebugAdapterServerSettings.getInstance().getSettings(debugAdapterServerId);
            if (settings != null) {
                this.setCommandLine(settings.getCommandLine());
                this.setEnvData(EnvironmentVariablesData.create(
                        settings.getUserEnvironmentVariables(),
                        settings.isIncludeSystemEnvironmentVariables()));
                this.debugAdapterServerPanel.getDebugServerWaitStrategyPanel()
                        .update(null, settings.getConnectTimeout(), settings.getDebugServerReadyPattern());
                this.setAttachAddress(settings.getAttachAddress());
                this.setAttachPort(settings.getAttachPort());
                
                List<ServerMappingSettings> languageMappings = settings.getMappings()
                        .stream()
                        .filter(mapping -> !StringUtils.isEmpty(mapping.getLanguage()))
                        .collect(Collectors.toList());
                this.setLanguageMappings(languageMappings);

                List<ServerMappingSettings> fileTypeMappings = settings.getMappings()
                        .stream()
                        .filter(mapping -> !StringUtils.isEmpty(mapping.getFileType()))
                        .collect(Collectors.toList());
                this.setFileTypeMappings(fileTypeMappings);

                List<ServerMappingSettings> fileNamePatternMappings = settings.getMappings()
                        .stream()
                        .filter(mapping -> mapping.getFileNamePatterns() != null)
                        .collect(Collectors.toList());
                this.setFileNamePatternMappings(fileNamePatternMappings);
            }
        } else {
            // TODO : extension point
            /*List<LanguageServerFileAssociation> mappings = LanguageServersRegistry.getInstance().findLanguageServerDefinitionFor(languageServerId);
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
            this.setFileNamePatternMappings(fileNamePatternMappings);*/
        }
        debugAdapterServerPanel.setServerId(debugAdapterServerId);
    }

    /**
     * Update the proper debug adapter server from the UI fields.
     */
    public void apply() {
        String debugAdapterServerId = debugAdapterServer.getId();
        UserDefinedDebugAdapterServerSettings.ItemSettings settings = UserDefinedDebugAdapterServerSettings.getInstance().getSettings(debugAdapterServerId);
        if (debugAdapterServer instanceof UserDefinedDebugAdapterServerDefinition userDefinedServer) {
            // Register settings and server definition without firing events
            var settingsChangedEvent = UserDefinedDebugAdapterServerSettings
                    .getInstance()
                    .updateSettings(debugAdapterServerId, settings, false);
            // Update user-defined language server settings
            var serverChangedEvent = DebugAdapterManager.getInstance()
                    .updateServerDefinition(
                            new DebugAdapterManager.UpdateDebugAdapterServerRequest(
                                    userDefinedServer,
                                    getDisplayName(),
                                    getEnvData().getEnvs(),
                                    getEnvData().isPassParentEnvs(),
                                    getCommandLine(),
                                    getConnectTimeout(),
                                    getDebugServerReadyPattern(),
                                    getLanguageMappings(),
                                    getFileTypeMappings(),
                                    getLaunchConfigurations(),
                                    getAttachAddress(),
                                    getAttachPort()),
                            false);
            if (settingsChangedEvent != null) {
                // Settings has changed, fire the event
                UserDefinedDebugAdapterServerSettings
                        .getInstance()
                        .handleChanged(settingsChangedEvent);
            }
            if (serverChangedEvent != null) {
                // Server definition has changed, fire the event
                DebugAdapterManager.getInstance().handleChangeEvent(serverChangedEvent);
            }
        }
    }

    private String getDisplayName() {
        return debugAdapterServerNameProvider != null ? debugAdapterServerNameProvider.getDisplayName() : debugAdapterServer.getDisplayName();
    }

    private JPanel createSettings(JComponent description, boolean launchingServerDefinition) {
        FormBuilder builder = FormBuilder
                .createFormBuilder()
                .setFormLeftIndent(10);
        this.debugAdapterServerPanel = new DebugAdapterServerPanel(builder,
                description,
                launchingServerDefinition ? DebugAdapterServerPanel.EditionMode.EDIT_USER_DEFINED :
                        DebugAdapterServerPanel.EditionMode.EDIT_EXTENSION, project);
        this.mappingPanel = debugAdapterServerPanel.getMappingsPanel();
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

    public void setEnvData(EnvironmentVariablesData envData) {
        if (envData != null) {
            debugAdapterServerPanel.getEnvironmentVariables().setEnvData(envData);
        }
    }

    public @NotNull EnvironmentVariablesData getEnvData() {
        return debugAdapterServerPanel.getEnvironmentVariables().getEnvData();
    }

    public JComponent getComponent() {
        return myMainPanel;
    }

    public String getCommandLine() {
        return debugAdapterServerPanel.getCommandLine();
    }

    public void setCommandLine(String commandLine) {
        debugAdapterServerPanel.setCommandLine(commandLine);
    }
    
    public int getConnectTimeout() {
        return debugAdapterServerPanel.getDebugServerWaitStrategyPanel().getConnectTimeout();
    }

    public String getDebugServerReadyPattern() {
        return debugAdapterServerPanel.getDebugServerWaitStrategyPanel().getTrace();
    }

    public String getAttachAddress() {
        return debugAdapterServerPanel.getAttachAddress();
    }

    public void setAttachAddress(String attachAddress) {
        debugAdapterServerPanel.setAttachAddress(attachAddress);
    }

    public String getAttachPort() {
        return debugAdapterServerPanel.getAttachPort();
    }

    public void setAttachPort(String attachPort) {
        debugAdapterServerPanel.setAttachPort(attachPort);
    }
    
    public ServerTrace getServerTrace() {
        return (ServerTrace) debugAdapterServerPanel.getServerTraceComboBox().getSelectedItem();
    }

    public void setServerTrace(ServerTrace serverTrace) {
        debugAdapterServerPanel.getServerTraceComboBox().setSelectedItem(serverTrace);
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

    @Override
    public void dispose() {
        debugAdapterServerPanel.dispose();
    }

    public List<ServerMappingSettings> getLanguageMappings() {
        return mappingPanel.getLanguageMappings();
    }

    public List<ServerMappingSettings> getFileTypeMappings() {
        return Streams.concat(mappingPanel.getFileTypeMappings().stream(),
                        mappingPanel.getFileNamePatternMappings().stream())
                .toList();
    }

    public List<ServerMappingSettings> getMappings() {
        return Streams.concat(mappingPanel.getLanguageMappings().stream(),
                        mappingPanel.getFileTypeMappings().stream(),
                        mappingPanel.getFileNamePatternMappings().stream())
                .toList();
    }

    public void refreshLaunchConfigurations(List<LaunchConfiguration> launchConfigurations) {
        debugAdapterServerPanel.refreshLaunchConfigurations(launchConfigurations);
    }

    public @Nullable List<LaunchConfiguration> getLaunchConfigurations() {
        return debugAdapterServerPanel.getLaunchConfigurations();
    }

}

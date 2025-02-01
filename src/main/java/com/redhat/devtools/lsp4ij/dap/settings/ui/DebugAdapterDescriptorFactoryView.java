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
package com.redhat.devtools.lsp4ij.dap.settings.ui;

import com.google.common.collect.Streams;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.redhat.devtools.lsp4ij.dap.LaunchConfiguration;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPServerMappingsPanel;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.descriptors.userdefined.UserDefinedDebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.dap.settings.UserDefinedDebugAdapterDescriptorFactorySettings;
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
 * UI view to define a Debug Adapter descriptor factory.
 */
public class DebugAdapterDescriptorFactoryView implements Disposable {

    private final DebugAdapterDescriptorFactoryNameProvider descriptorFactoryNameProvider;
    private final boolean isUserDefined;
    private final Project project;

    public interface DebugAdapterDescriptorFactoryNameProvider {
        String getDisplayName();
    }

    private final JPanel myMainPanel;
    private final @NotNull DebugAdapterDescriptorFactory dapDescriptorFactory;

    private DebugAdapterDescriptorFactoryPanel dapDescriptorFactoryPanel;

    private DAPServerMappingsPanel mappingPanel;

    public DebugAdapterDescriptorFactoryView(@NotNull DebugAdapterDescriptorFactory dapDescriptorFactory,
                                             @Nullable DebugAdapterDescriptorFactoryView.DebugAdapterDescriptorFactoryNameProvider dapDescriptorFactoryNameProvider,
                                             @NotNull Project project) {
        this.project = project;
        this.dapDescriptorFactory = dapDescriptorFactory;
        this.descriptorFactoryNameProvider = dapDescriptorFactoryNameProvider;
        isUserDefined = dapDescriptorFactory instanceof UserDefinedDebugAdapterDescriptorFactory;
        JComponent descriptionPanel = createDescription(dapDescriptorFactory.getDescription());
        JPanel settingsPanel = createSettings(descriptionPanel, isUserDefined);
        if (!isUserDefined) {
            TitledBorder title = IdeBorderFactory.createTitledBorder(dapDescriptorFactory.getDisplayName());
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
        String descriptorFactoryId = dapDescriptorFactory.getId();
        if (isUserDefined) {
            UserDefinedDebugAdapterDescriptorFactorySettings.ItemSettings settings = UserDefinedDebugAdapterDescriptorFactorySettings.getInstance().getSettings(descriptorFactoryId);
            if (settings == null) {
                return true;
            }
            return !(isEquals(getDisplayName(), settings.getServerName())
                    && isEquals(this.getCommandLine(), settings.getCommandLine())
                    && Objects.equals(this.getEnvData().getEnvs(), settings.getUserEnvironmentVariables())
                    && this.getEnvData().isPassParentEnvs() == settings.isIncludeSystemEnvironmentVariables()
                    && this.getConnectTimeout() == settings.getConnectTimeout()
                    && isEquals(this.getWaitForTrace(), settings.getWaitForTrace())
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
        String descriptorFactoryId = dapDescriptorFactory.getId();

        if (isUserDefined) {
            // User defined language server
            UserDefinedDebugAdapterDescriptorFactorySettings.ItemSettings settings = UserDefinedDebugAdapterDescriptorFactorySettings.getInstance().getSettings(descriptorFactoryId);
            if (settings != null) {
                this.setCommandLine(settings.getCommandLine());
                this.setEnvData(EnvironmentVariablesData.create(
                        settings.getUserEnvironmentVariables(),
                        settings.isIncludeSystemEnvironmentVariables()));
                this.dapDescriptorFactoryPanel.getConnectingServerConfigurationPanel()
                        .update(null, settings.getConnectTimeout(), settings.getWaitForTrace());

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
        dapDescriptorFactoryPanel.setServerId(descriptorFactoryId);
    }

    /**
     * Update the proper debug adapter descriptor factory from the UI fields.
     */
    public void apply() {
        String descriptorFactoryId = dapDescriptorFactory.getId();
        UserDefinedDebugAdapterDescriptorFactorySettings.ItemSettings settings = UserDefinedDebugAdapterDescriptorFactorySettings.getInstance().getSettings(descriptorFactoryId);
        if (dapDescriptorFactory instanceof UserDefinedDebugAdapterDescriptorFactory userDefinedFactory) {
            // Register settings and server definition without firing events
            var settingsChangedEvent = UserDefinedDebugAdapterDescriptorFactorySettings
                    .getInstance()
                    .updateSettings(descriptorFactoryId, settings, false);
            // Update user-defined language server settings
            var serverChangedEvent = DebugAdapterManager.getInstance()
                    .updateDescriptorFactory(
                            new DebugAdapterManager.UpdateDebugAdapterDescriptorFactoryRequest(
                                    userDefinedFactory,
                                    getDisplayName(),
                                    getEnvData().getEnvs(),
                                    getEnvData().isPassParentEnvs(),
                                    getCommandLine(),
                                    getConnectTimeout(),
                                    getWaitForTrace(),
                                    getLanguageMappings(),
                                    getFileTypeMappings(),
                                    getLaunchConfigurations()),
                            false);
            if (settingsChangedEvent != null) {
                // Settings has changed, fire the event
                UserDefinedDebugAdapterDescriptorFactorySettings
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
        return descriptorFactoryNameProvider != null ? descriptorFactoryNameProvider.getDisplayName() : dapDescriptorFactory.getDisplayName();
    }

    private JPanel createSettings(JComponent description, boolean launchingServerDefinition) {
        FormBuilder builder = FormBuilder
                .createFormBuilder()
                .setFormLeftIndent(10);
        this.dapDescriptorFactoryPanel = new DebugAdapterDescriptorFactoryPanel(builder,
                description,
                launchingServerDefinition ? DebugAdapterDescriptorFactoryPanel.EditionMode.EDIT_USER_DEFINED :
                        DebugAdapterDescriptorFactoryPanel.EditionMode.EDIT_EXTENSION, project);
        this.mappingPanel = dapDescriptorFactoryPanel.getMappingsPanel();
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
            dapDescriptorFactoryPanel.getEnvironmentVariables().setEnvData(envData);
        }
    }

    public @NotNull EnvironmentVariablesData getEnvData() {
        return dapDescriptorFactoryPanel.getEnvironmentVariables().getEnvData();
    }

    public JComponent getComponent() {
        return myMainPanel;
    }

    public String getCommandLine() {
        return dapDescriptorFactoryPanel.getCommandLine();
    }

    public void setCommandLine(String commandLine) {
        dapDescriptorFactoryPanel.setCommandLine(commandLine);
    }

    public int getConnectTimeout() {
        return dapDescriptorFactoryPanel.getConnectingServerConfigurationPanel().getConnectTimeout();
    }

    public String getWaitForTrace() {
        return dapDescriptorFactoryPanel.getConnectingServerConfigurationPanel().getTrace();
    }

    public ServerTrace getServerTrace() {
        return (ServerTrace) dapDescriptorFactoryPanel.getServerTraceComboBox().getSelectedItem();
    }

    public void setServerTrace(ServerTrace serverTrace) {
        dapDescriptorFactoryPanel.getServerTraceComboBox().setSelectedItem(serverTrace);
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
        dapDescriptorFactoryPanel.dispose();
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
        dapDescriptorFactoryPanel.refreshLaunchConfigurations(launchConfigurations);
    }

    public @Nullable List<LaunchConfiguration> getLaunchConfigurations() {
        return dapDescriptorFactoryPanel.getLaunchConfigurations();
    }

}

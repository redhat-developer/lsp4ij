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

import com.google.gson.JsonParser;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.redhat.devtools.lsp4ij.commands.LSPCommandContext;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.redhat.devtools.lsp4ij.client.SettingsHelper.parseJson;
import static com.redhat.devtools.lsp4ij.settings.LanguageServerView.isEquals;

/**
 * Base class for Language server settings for a given Language server definition.
 *
 * <ul>
 *     <li>Debug port</li>
 *     <li>Suspend and wait for a debugger</li>
 *     <li>Trace LSP requests/responses/notifications</li>
 * </ul>
 */
public abstract class LanguageServerSettings implements PersistentStateComponent<LanguageServerSettings.MyState> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServerSettings.class);
    private final List<LanguageServerSettingsListener> listeners = ContainerUtil.createConcurrentList();
    private volatile MyState myState = new MyState();

    @Nullable
    @Override
    public MyState getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull MyState state) {
        myState = state;
    }

    /**
     * Returns the language server settings for the given language server id and null otherwise.
     *
     * @param languageServerId the language server id.
     * @return the language server settings for the given language server id and null otherwise.
     */
    @Nullable
    public LanguageServerDefinitionSettings getLanguageServerSettings(String languageServerId) {
        return myState.myState.get(languageServerId);
    }

    /**
     * Update the language server settings for the given language server id with the given settings.
     *
     * @param languageServerId the language server id.
     * @param newSettings      the language server settings for the given language server id with the given settings.
     */
    public void updateSettings(@NotNull String languageServerId,
                               @NotNull LanguageServerDefinitionSettings newSettings) {
        updateSettings(languageServerId, newSettings, true);
    }

    /**
     * Update the language server settings for the given language server id with the given settings.
     *
     * @param languageServerId the language server id.
     * @param newSettings      the language server settings for the given language server id with the given settings.
     * @param notify           true if a handle changed must be done and false otherwise.
     */
    @Nullable
    public LanguageServerSettingsListener.LanguageServerSettingsChangedEvent updateSettings(@NotNull String languageServerId,
                                                                                            @NotNull LanguageServerDefinitionSettings newSettings,
                                                                                            boolean notify) {
        LanguageServerDefinitionSettings existingSettings = getLanguageServerSettings(languageServerId);
        if (existingSettings != null) {
            // The settings exist for the given language server id, update it with the new settings.

            // Configuration
            boolean configurationContentChanged = !isEquals(existingSettings.getConfigurationContent(), newSettings.getConfigurationContent());
            if (configurationContentChanged) {
                existingSettings.setConfigurationContent(newSettings.getConfigurationContent());
            }
            boolean expandConfigurationChanged = !existingSettings.isExpandConfiguration() == newSettings.isExpandConfiguration();
            if (expandConfigurationChanged) {
                existingSettings.setExpandConfiguration(newSettings.isExpandConfiguration());
            }
            boolean configurationSchemaContentChanged = !isEquals(existingSettings.getConfigurationSchemaContent(), newSettings.getConfigurationSchemaContent());
            if (configurationSchemaContentChanged) {
                existingSettings.setConfigurationSchemaContent(newSettings.getConfigurationSchemaContent());
            }
            boolean initializationOptionsContentChanged = !isEquals(existingSettings.getInitializationOptionsContent(), newSettings.getInitializationOptionsContent());
            if (initializationOptionsContentChanged) {
                existingSettings.setInitializationOptionsContent(newSettings.getInitializationOptionsContent());
            }
            boolean experimentalContentChanged = !isEquals(existingSettings.getExperimentalContent(), newSettings.getExperimentalContent());
            if (experimentalContentChanged) {
                existingSettings.setExperimentalContent(newSettings.getExperimentalContent());
            }

            // Debug
            boolean debugPortChanged = newSettings.getDebugPort() != null && !(isEquals(existingSettings.getDebugPort(), newSettings.getDebugPort()));
            if (debugPortChanged) {
                existingSettings.setDebugPort(newSettings.getDebugPort());
            }
            boolean debugSuspendChanged = newSettings.isDebugSuspend() != existingSettings.isDebugSuspend();
            if (debugSuspendChanged) {
                existingSettings.setDebugSuspend(newSettings.isDebugSuspend());
            }
            boolean errorReportingKindChanged = newSettings.getErrorReportingKind() != null && !(isEquals(existingSettings.getErrorReportingKind(), newSettings.getErrorReportingKind()));
            if (errorReportingKindChanged) {
                existingSettings.setErrorReportingKind(newSettings.getErrorReportingKind());
            }
            boolean serverTraceChanged = newSettings.getServerTrace() != null && !(isEquals(existingSettings.getServerTrace(), newSettings.getServerTrace()));
            if (serverTraceChanged) {
                existingSettings.setServerTrace(newSettings.getServerTrace());
            }

            if (configurationContentChanged || expandConfigurationChanged || configurationSchemaContentChanged || experimentalContentChanged ||
                    debugPortChanged || debugSuspendChanged || errorReportingKindChanged || serverTraceChanged){
                // There are some changes, fire the changed event.
                return handleChanged(languageServerId, existingSettings, notify,
                        configurationContentChanged, expandConfigurationChanged, configurationSchemaContentChanged, initializationOptionsContentChanged, experimentalContentChanged,
                        debugPortChanged, debugSuspendChanged, errorReportingKindChanged, serverTraceChanged);
            }
        } else {
            // The settings don't exist
            // fire the changed event.
            myState.myState.put(languageServerId, newSettings);
            boolean configurationContentChanged = !StringUtils.isBlank(newSettings.getConfigurationContent());
            boolean expandConfigurationChanged= !newSettings.isExpandConfiguration();
            boolean configurationSchemaContentChanged= !StringUtils.isBlank(newSettings.getConfigurationSchemaContent());
            boolean initializationOptionsContentChanged= !StringUtils.isBlank(newSettings.getInitializationOptionsContent());
            boolean experimentalContentChanged= !StringUtils.isBlank(newSettings.getExperimentalContent());
            boolean debugPortChanged = !(isEquals("", newSettings.getDebugPort()));
            boolean debugSuspendChanged = newSettings.isDebugSuspend();
            boolean errorReportingKindChanged = newSettings.getErrorReportingKind() != null && !(isEquals(ErrorReportingKind.getDefaultValue(), newSettings.getErrorReportingKind()));
            boolean serverTraceChanged = newSettings.getServerTrace() != null && !(isEquals(ServerTrace.getDefaultValue(), newSettings.getServerTrace()));
            if  (configurationContentChanged || expandConfigurationChanged || configurationSchemaContentChanged || experimentalContentChanged ||
                    debugPortChanged || debugSuspendChanged || errorReportingKindChanged || serverTraceChanged) {
                return handleChanged(languageServerId, newSettings, notify,
                        configurationContentChanged, expandConfigurationChanged, configurationSchemaContentChanged, initializationOptionsContentChanged, experimentalContentChanged,
                        debugPortChanged, debugSuspendChanged, errorReportingKindChanged, serverTraceChanged);
            }
        }
        return null;
    }

    @Nullable
    private LanguageServerSettingsListener.LanguageServerSettingsChangedEvent handleChanged(
            String languageServerId,
            LanguageServerDefinitionSettings existing,
            boolean notify,
            boolean configurationContentChanged,
            boolean expandConfigurationChanged,
            boolean configurationSchemaContentChanged,
            boolean initializationOptionsContentChanged,
            boolean experimentalContentChanged,
            boolean debugPortChanged,
            boolean debugSuspendChanged,
            boolean errorReportingKindChanged,
            boolean serverTraceChanged) {
        if (listeners.isEmpty()) {
            return null;
        }
        LanguageServerSettingsListener.LanguageServerSettingsChangedEvent event = new LanguageServerSettingsListener.LanguageServerSettingsChangedEvent(
                languageServerId,
                existing,
                configurationContentChanged,
                expandConfigurationChanged,
                configurationSchemaContentChanged,
                initializationOptionsContentChanged,
                experimentalContentChanged,
                debugPortChanged,
                debugSuspendChanged,
                errorReportingKindChanged,
                serverTraceChanged);
        if (notify) {
            handleChanged(event);
        }
        return event;
    }

    public void handleChanged(LanguageServerSettingsListener.LanguageServerSettingsChangedEvent event) {
        for (LanguageServerSettingsListener listener : this.listeners) {
            try {
                listener.handleChanged(event);
            } catch (Exception e) {
                LOGGER.error("Error while server settings has changed for the language server '{}'", event.languageServerId(), e);
            }
        }
    }

    /**
     * Tells whether the save tip balloon is shown when changing language server configuration
     *
     * @return true if the balloon should be shown, false otherwise
     */
    public boolean showSaveTipOnConfigurationChange() {
        return this.myState.showSaveTipOnConfigurationChange;
    }

    /**
     * Sets the value if the save tip balloon should be shown on language server configuration change
     * There is no way to set this value to true at the moment, it can only be disabled
     *
     * @param value true if the balloon should be shown, false otherwise
     */
    public void showSaveTipOnConfigurationChange(boolean value) {
        this.myState.showSaveTipOnConfigurationChange = value;
    }

    /**
     * Returns true if notification error must be shown for the executed by command and false otherwise.
     *
     * @param executedBy command executed by.
     * @return true if notification error must be shown for the executed by command and false otherwise.
     */
    public boolean isShowNotificationErrorForCommand(LSPCommandContext.ExecutedBy executedBy) {
        return this.myState.showNotificationErrorForCommand.get(executedBy.name());
    }

    /**
     * Set true if notification error must be shown for the executed by command and false otherwise.
     *
     * @param executedBy command executed by.
     * @param enabled    the enabled state.
     */
    public void setShowNotificationErrorForCommand(LSPCommandContext.ExecutedBy executedBy, boolean enabled) {
        this.myState.showNotificationErrorForCommand.put(executedBy.name(), enabled);
    }

    /**
     * Adds the given changeHandler to the list of registered change handlers
     *
     * @param listener the settings listener to add
     */
    public void addSettingsListener(@NotNull LanguageServerSettingsListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes the given changeHandler from the list of registered change handlers
     *
     * @param listener the settings listener to add
     */
    public void removeSettingsListener(@NotNull LanguageServerSettingsListener listener) {
        listeners.remove(listener);
    }

    public static class LanguageServerDefinitionSettings {

        private String configurationContent;
        private volatile Object configuration;
        private boolean expandConfiguration = true;
        private String configurationSchemaContent;

        private String initializationOptionsContent;
        private volatile Object initializationOptions;

        private String experimentalContent;
        private volatile Object experimental;

        private String debugPort;
        private boolean debugSuspend;
        private ServerTrace serverTrace;
        private ErrorReportingKind errorReportingKind;

        public String getConfigurationContent() {
            return configurationContent;
        }

        public void setConfigurationContent(String configurationContent) {
            this.configurationContent = configurationContent;
            this.configuration = null;
        }

        public Object getLanguageServerConfiguration(@Nullable Project project) {
            if (configurationContent != null && !configurationContent.isBlank()) {
                try {
                    if (configurationContent.contains("$")) {
                        // Resolve magic variables like $PROJECT_DIR$
                        String projectConfigurationContent = CommandUtils.resolveCommandLine(configurationContent, project);
                        return parseJson(projectConfigurationContent, isExpandConfiguration());
                    }
                    if (configuration == null) {
                        configuration = parseJson(configurationContent, isExpandConfiguration());
                    }
                } catch (Exception e) {
                    //LOGGER.error("Error while parsing JSON configuration for the language server '{}'", getServerId(), e);
                }
            }
            return configuration;
        }

        public boolean isExpandConfiguration() {
            return expandConfiguration;
        }

        public void setExpandConfiguration(boolean expandConfiguration) {
            this.expandConfiguration = expandConfiguration;
            configuration = null;
        }

        public String getConfigurationSchemaContent() {
            return configurationSchemaContent;
        }

        public void setConfigurationSchemaContent(String configurationSchemaContent) {
            this.configurationSchemaContent = configurationSchemaContent;
        }

        public String getInitializationOptionsContent() {
            return initializationOptionsContent;
        }

        public void setInitializationOptionsContent(String initializationOptionsContent) {
            this.initializationOptionsContent = initializationOptionsContent;
            this.initializationOptions = null;
        }

        public Object getLanguageServerInitializationOptions(@Nullable Project project) {
            if (initializationOptionsContent != null && !initializationOptionsContent.isEmpty()) {
                try {
                    if (initializationOptionsContent.contains("$")) {
                        // Resolve magic variables like $PROJECT_DIR$
                        String projectInitializationOptionsContent = CommandUtils.resolveCommandLine(initializationOptionsContent, project);
                        return JsonParser.parseReader(new StringReader(projectInitializationOptionsContent));
                    }
                    if (initializationOptions == null) {
                        initializationOptions = JsonParser.parseReader(new StringReader(initializationOptionsContent));
                    }
                } catch (Exception e) {
                    //LOGGER.error("Error while parsing JSON Initialization Options for the language server '{}'", getServerId(), e);
                }
            }
            return initializationOptions;
        }

        public String getExperimentalContent() {
            return experimentalContent;
        }

        public void setExperimentalContent(String experimentalContent) {
            this.experimentalContent = experimentalContent;
            this.experimental = null;
        }

        public Object getLanguageServerExperimental(@Nullable Project project) {
            if (experimentalContent != null && !experimentalContent.isEmpty()) {
                try {
                    if (experimentalContent.contains("$")) {
                        // Resolve magic variables like $PROJECT_DIR$
                        String projectExperimentalContent = CommandUtils.resolveCommandLine(experimentalContent, project);
                        return JsonParser.parseReader(new StringReader(projectExperimentalContent));
                    }
                    if (experimental == null) {
                        experimental = JsonParser.parseReader(new StringReader(experimentalContent));
                    }
                } catch (Exception e) {
                    //LOGGER.error("Error while parsing JSON Initialization Options for the language server '{}'", getServerId(), e);
                }
            }
            return experimental;
        }

        public String getDebugPort() {
            return debugPort;
        }

        public void setDebugPort(String debugPort) {
            this.debugPort = debugPort;
        }

        public boolean isDebugSuspend() {
            return debugSuspend;
        }

        public void setDebugSuspend(boolean debugSuspend) {
            this.debugSuspend = debugSuspend;
        }

        public ServerTrace getServerTrace() {
            return serverTrace;
        }

        public LanguageServerDefinitionSettings setServerTrace(ServerTrace serverTrace) {
            this.serverTrace = serverTrace;
            return this;
        }

        public ErrorReportingKind getErrorReportingKind() {
            return errorReportingKind;
        }

        public LanguageServerDefinitionSettings setErrorReportingKind(ErrorReportingKind errorReportingKind) {
            this.errorReportingKind = errorReportingKind;
            return this;
        }


    }

    public static class MyState {
        @Tag("state")
        @XCollection
        public Map<String, LanguageServerDefinitionSettings> myState = new TreeMap<>();
        public Map<String, Boolean> showNotificationErrorForCommand;
        private boolean showSaveTipOnConfigurationChange = true;

        MyState() {
            showNotificationErrorForCommand = Stream.of(LSPCommandContext.ExecutedBy.values())
                    .collect(Collectors.toMap(Enum::name, s -> true));
        }
    }

}

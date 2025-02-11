/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.launching;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User defined launch configuration language server settings for a given Language server definition to define:
 *
 * <ul>
 *     <li>The server name</li>
 *     <li>The process command to start the language server</li>
 *     <li>Language / file type mappings/li>
 * </ul>
 */
@State(
        name = "UserDefinedLanguageServerSettingsState",
        storages = @Storage("UserDefinedLanguageServerSettings.xml")
)
public class UserDefinedLanguageServerSettings implements PersistentStateComponent<UserDefinedLanguageServerSettings.MyState> {

    private final List<Runnable> myChangeHandlers = ContainerUtil.createConcurrentList();
    private volatile MyState myState = new MyState();

    public static UserDefinedLanguageServerSettings getInstance() {
        return ApplicationManager.getApplication().getService(UserDefinedLanguageServerSettings.class);
    }

    @Nullable
    @Override
    public MyState getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull MyState state) {
        myState = state;
    }

    public UserDefinedLanguageServerItemSettings getLaunchConfigSettings(String languageServerId) {
        return myState.myState.get(languageServerId);
    }

    public void setLaunchConfigSettings(String languageServerId, UserDefinedLanguageServerItemSettings settings) {
        myState.myState.put(languageServerId, settings);
        fireStateChanged();
    }

    public void removeServerDefinition(String languageServerId) {
        myState.myState.remove(languageServerId);
        fireStateChanged();
    }

    public Collection<UserDefinedLanguageServerItemSettings> getUserDefinedLanguageServerSettings() {
        return myState.myState.values();
    }

    /**
     * Adds the given changeHandler to the list of registered change handlers
     *
     * @param changeHandler the changeHandler to remove
     */
    public void addChangeHandler(@NotNull Runnable changeHandler) {
        myChangeHandlers.add(changeHandler);
    }

    /**
     * Removes the given changeHandler from the list of registered change handlers
     *
     * @param changeHandler the changeHandler to remove
     */
    public void removeChangeHandler(@NotNull Runnable changeHandler) {
        myChangeHandlers.remove(changeHandler);
    }

    /**
     * Notifies all registered change handlers when the state changed
     */
    private void fireStateChanged() {
        for (Runnable handler : myChangeHandlers) {
            handler.run();
        }
    }

    public static class UserDefinedLanguageServerItemSettings {

        private String serverId;

        private String templateId;

        private String serverName;

        private String commandLine;

        private Map<String, String> userEnvironmentVariables;

        private boolean includeSystemEnvironmentVariables = true;

        private String configurationContent;

        private String configurationSchemaContent;

        private String initializationOptionsContent;

        private String clientConfigurationContent = "{}";

        @XCollection(elementTypes = ServerMappingSettings.class)
        private List<ServerMappingSettings> mappings;

        public String getServerId() {
            return serverId;
        }

        public void setServerId(String serverId) {
            this.serverId = serverId;
        }

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String templateId) {
            this.templateId = templateId;
        }

        public String getServerName() {
            return serverName;
        }

        public void setServerName(String serverName) {
            this.serverName = serverName;
        }

        public String getCommandLine() {
            return commandLine;
        }

        public void setCommandLine(String commandLine) {
            this.commandLine = commandLine;
        }

        /**
         * Returns the User environment variables used to start the language server process.
         *
         * @return the User environment variables used to start the language server process.
         */
        @NotNull
        public Map<String, String> getUserEnvironmentVariables() {
            return userEnvironmentVariables != null ? userEnvironmentVariables : Collections.emptyMap();
        }

        /**
         * Set the User environment variables used to start the language server process.
         *
         * @param userEnvironmentVariables the User environment variables.
         */
        public void setUserEnvironmentVariables(Map<String, String> userEnvironmentVariables) {
            this.userEnvironmentVariables = userEnvironmentVariables;
        }

        /**
         * Returns true if System environment variables must be included when language server process starts and false otherwise.
         *
         * @return true if System environment variables must be included when language server process starts and false otherwise.
         */
        public boolean isIncludeSystemEnvironmentVariables() {
            return includeSystemEnvironmentVariables;
        }

        /**
         * Set true if System environment variables must be included when language server process starts and false otherwise.
         *
         * @param includeSystemEnvironmentVariables true if System environment variables must be included when language server process starts and false otherwise.
         */
        public void setIncludeSystemEnvironmentVariables(boolean includeSystemEnvironmentVariables) {
            this.includeSystemEnvironmentVariables = includeSystemEnvironmentVariables;
        }

        public List<ServerMappingSettings> getMappings() {
            return mappings != null ? mappings : Collections.emptyList();
        }

        public void setMappings(List<ServerMappingSettings> mappings) {
            this.mappings = mappings;
        }

        public String getConfigurationContent() {
            return configurationContent;
        }

        public void setConfigurationContent(String configurationContent) {
            this.configurationContent = configurationContent;
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
        }

        public String getClientConfigurationContent() {
            return clientConfigurationContent;
        }

        public void setClientConfigurationContent(String clientConfigurationContent) {
            this.clientConfigurationContent = clientConfigurationContent;
        }
    }

    public static class MyState {
        @Tag("state")
        @XCollection
        public Map<String, UserDefinedLanguageServerItemSettings> myState = new TreeMap<>();

        MyState() {
        }
    }
}

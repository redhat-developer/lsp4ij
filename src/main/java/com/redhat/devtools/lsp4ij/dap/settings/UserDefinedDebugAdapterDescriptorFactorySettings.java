/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.dap.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.redhat.devtools.lsp4ij.dap.LaunchConfiguration;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User defined debug adapter descriptor factory settings.
 */
@State(
        name = "debugAdapterDescriptorFactorySettingsState",
        storages = @Storage("DebugAdapterDescriptorFactorySettings.xml")
)
public class UserDefinedDebugAdapterDescriptorFactorySettings implements PersistentStateComponent<UserDefinedDebugAdapterDescriptorFactorySettings.MyState> {

    private final List<Runnable> myChangeHandlers = ContainerUtil.createConcurrentList();
    private volatile MyState myState = new MyState();

    public static UserDefinedDebugAdapterDescriptorFactorySettings getInstance() {
        return ApplicationManager.getApplication().getService(UserDefinedDebugAdapterDescriptorFactorySettings.class);
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

    /**
     * Returns the Debug Adapter descriptor factory settings for the given factory Id and null otherwise.
     *
     * @param descriptorFactoryId the Debug Adapter descriptor factory id.
     * @return the Debug Adapter descriptor factory settings for the given factory Id and null otherwise.
     */
    @Nullable
    public ItemSettings getSettings(@NotNull String descriptorFactoryId) {
        return myState.myState.get(descriptorFactoryId);
    }

    /**
     * Register the Debug Adapter descriptor factory settings for the given factory Id.
     * @param descriptorFactoryId the Debug Adapter descriptor factory id.
     * @param settings the Debug Adapter descriptor factory settings.
     */
    public void setSettings(@NotNull String descriptorFactoryId, @NotNull ItemSettings settings) {
        myState.myState.put(descriptorFactoryId, settings);
        fireStateChanged();
    }

    public void removeSettings(String descriptorFactoryId) {
        myState.myState.remove(descriptorFactoryId);
        fireStateChanged();
    }

    public Collection<ItemSettings> getSettings() {
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

    public Object updateSettings(@NotNull String descriptorFactoryId,
                                 @NotNull UserDefinedDebugAdapterDescriptorFactorySettings.ItemSettings settings,
                                 boolean notify) {

        return null;
    }

    public void handleChanged(Object settingsChangedEvent) {
    }

    public static class ItemSettings {

        private String serverId;

        private String serverName;

        private Map<String, String> userEnvironmentVariables;
        private boolean includeSystemEnvironmentVariables = true;
        private String commandLine;
        private int connectTimeout;
        private String debugServerReadyPattern;

        @XCollection(elementTypes = ServerMappingSettings.class)
        private List<ServerMappingSettings> mappings;

        @XCollection(elementTypes = LaunchConfiguration.class)
        private List<LaunchConfiguration> launchConfigurations;

        public String getServerId() {
            return serverId;
        }

        public void setServerId(String serverId) {
            this.serverId = serverId;
        }

        public String getServerName() {
            return serverName;
        }

        public void setServerName(String serverName) {
            this.serverName = serverName;
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

        public String getCommandLine() {
            return commandLine;
        }

        public void setCommandLine(String commandLine) {
            this.commandLine = commandLine;
        }

        public int getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public String getDebugServerReadyPattern() {
            return debugServerReadyPattern;
        }

        public void setDebugServerReadyPattern(String debugServerReadyPattern) {
            this.debugServerReadyPattern = debugServerReadyPattern;
        }

        public List<ServerMappingSettings> getMappings() {
            return mappings != null ? mappings : Collections.emptyList();
        }

        public void setMappings(List<ServerMappingSettings> mappings) {
            this.mappings = mappings;
        }

        public List<LaunchConfiguration> getLaunchConfigurations() {
            return launchConfigurations;
        }

        public void setLaunchConfigurations(List<LaunchConfiguration> launchConfigurations) {
            this.launchConfigurations = launchConfigurations;
        }

    }

    public static class MyState {
        @Tag("state")
        @XCollection
        public Map<String, ItemSettings> myState = new TreeMap<>();

        MyState() {
        }
    }
}

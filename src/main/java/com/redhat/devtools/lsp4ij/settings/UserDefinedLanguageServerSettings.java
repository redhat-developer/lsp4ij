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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.redhat.devtools.lsp4ij.commands.LSPCommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.redhat.devtools.lsp4ij.settings.LanguageServerView.isEquals;

/**
 * User defined language server settings for a given Language server definition
 *
 * <ul>
 *     <li>Debug port</li>
 *     <li>Suspend and wait for a debugger</li>
 *     <li>Trace LSP requests/responses/notifications</li>
 * </ul>
 */
@State(
        name = "LanguageServerSettingsState",
        storages = @Storage("LanguageServersSettings.xml")
)
public class UserDefinedLanguageServerSettings implements PersistentStateComponent<UserDefinedLanguageServerSettings.MyState> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDefinedLanguageServerSettings.class);

    private volatile MyState myState = new MyState();

    private final List<UserDefinedLanguageServerSettingsListener> listeners = ContainerUtil.createConcurrentList();

    public static UserDefinedLanguageServerSettings getInstance(@NotNull Project project) {
        return project.getService(UserDefinedLanguageServerSettings.class);
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
    public UserDefinedLanguageServerSettingsListener.LanguageServerSettingsChangedEvent updateSettings(@NotNull String languageServerId,
                                                                                                       @NotNull LanguageServerDefinitionSettings newSettings,
                                                                                                       boolean notify) {
        LanguageServerDefinitionSettings existing = getLanguageServerSettings(languageServerId);
        if (existing != null) {
            // The settings exist for the given language server id, update it with the new settings.
            boolean debugPortChanged = newSettings.getDebugPort() != null && !(isEquals(existing.getDebugPort(), newSettings.getDebugPort()));
            if (debugPortChanged) {
                existing.setDebugPort(newSettings.getDebugPort());
            }
            boolean debugSuspendChanged = newSettings.isDebugSuspend() != newSettings.isDebugSuspend();
            if (debugSuspendChanged) {
                existing.setDebugSuspend(existing.isDebugSuspend());
            }
            boolean errorReportingKindChanged = newSettings.getErrorReportingKind() != null && !(isEquals(existing.getErrorReportingKind(), newSettings.getErrorReportingKind()));
            if (errorReportingKindChanged) {
                existing.setErrorReportingKind(newSettings.getErrorReportingKind());
            }
            boolean serverTraceChanged = newSettings.getServerTrace() != null && !(isEquals(existing.getServerTrace(), newSettings.getServerTrace()));
            if (serverTraceChanged) {
                existing.setServerTrace(newSettings.getServerTrace());
            }
            if (notify && (debugPortChanged || debugSuspendChanged || errorReportingKindChanged || serverTraceChanged)) {
                // There are some changes, fire the changed event.
                return handleChanged(languageServerId, existing, notify, debugPortChanged, debugSuspendChanged, errorReportingKindChanged, serverTraceChanged);
            }
        } else {
            // fire the changed event.
            myState.myState.put(languageServerId, newSettings);
            boolean debugPortChanged = !(isEquals("", newSettings.getDebugPort()));
            boolean debugSuspendChanged = newSettings.isDebugSuspend();
            boolean errorReportingKindChanged = newSettings.getErrorReportingKind() != null && !(isEquals(ErrorReportingKind.getDefaultValue(), newSettings.getErrorReportingKind()));
            boolean serverTraceChanged = newSettings.getServerTrace() != null && !(isEquals(ServerTrace.getDefaultValue(), newSettings.getServerTrace()));
            if (debugPortChanged || debugSuspendChanged || errorReportingKindChanged || serverTraceChanged) {
                return handleChanged(languageServerId, newSettings, notify, true, true, true, true);
            }
        }
        return null;
    }

    @Nullable
    private UserDefinedLanguageServerSettingsListener.LanguageServerSettingsChangedEvent handleChanged(
            String languageServerId,
            LanguageServerDefinitionSettings existing,
            boolean notify,
            boolean debugPortChanged,
            boolean debugSuspendChanged,
            boolean errorReportingKindChanged,
            boolean serverTraceChanged) {
        if (listeners.isEmpty()) {
            return null;
        }
        UserDefinedLanguageServerSettingsListener.LanguageServerSettingsChangedEvent event = new UserDefinedLanguageServerSettingsListener.LanguageServerSettingsChangedEvent(
                languageServerId,
                existing,
                debugPortChanged,
                debugSuspendChanged,
                errorReportingKindChanged,
                serverTraceChanged);
        if (notify) {
            handleChanged(event);
        }
        return event;
    }

    public void handleChanged(UserDefinedLanguageServerSettingsListener.LanguageServerSettingsChangedEvent event) {
        for (UserDefinedLanguageServerSettingsListener listener : this.listeners) {
            try {
                listener.handleChanged(event);
            } catch (Exception e) {
                LOGGER.error("Error while server settings has changed for the language server '" + event.languageServerId() + "'", e);
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

    public static class LanguageServerDefinitionSettings {

        private String debugPort;

        private boolean debugSuspend;

        private ServerTrace serverTrace;

        private ErrorReportingKind errorReportingKind;

        public String getDebugPort() {
            return debugPort;
        }

        public LanguageServerDefinitionSettings setDebugPort(String debugPort) {
            this.debugPort = debugPort;
            return this;
        }

        public boolean isDebugSuspend() {
            return debugSuspend;
        }

        public LanguageServerDefinitionSettings setDebugSuspend(boolean debugSuspend) {
            this.debugSuspend = debugSuspend;
            return this;
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

        MyState() {
            showNotificationErrorForCommand = Stream.of(LSPCommandContext.ExecutedBy.values())
                    .collect(Collectors.toMap(s -> s.name(), s -> true));
        }

        private boolean showSaveTipOnConfigurationChange = true;
    }

    /**
     * Adds the given changeHandler to the list of registered change handlers
     *
     * @param listener the settings listener to add
     */
    public void addSettingsListener(@NotNull UserDefinedLanguageServerSettingsListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes the given changeHandler from the list of registered change handlers
     *
     * @param listener the settings listener to add
     */
    public void removeChangeHandler(@NotNull UserDefinedLanguageServerSettingsListener listener) {
        listeners.remove(listener);
    }

}

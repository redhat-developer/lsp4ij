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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

    private volatile MyState myState = new MyState();

    private final List<Runnable> myChangeHandlers = ContainerUtil.createConcurrentList();

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

    public LanguageServerDefinitionSettings getLanguageServerSettings(String languageSeverId) {
        return myState.myState.get(languageSeverId);
    }

    public void setLanguageServerSettings(String languageSeverId, LanguageServerDefinitionSettings settings) {
        myState.myState.put(languageSeverId, settings);
        fireStateChanged();
    }

    public static class LanguageServerDefinitionSettings {

        private String debugPort;

        private boolean debugSuspend;

        private ServerTrace serverTrace;

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

        public void setServerTrace(ServerTrace serverTrace) {
            this.serverTrace = serverTrace;
        }
    }

    public static class MyState {
        @Tag("state")
        @XCollection
        public Map<String, LanguageServerDefinitionSettings> myState = new TreeMap<>();

        MyState() {
        }

    }

    /**
     * Adds the given changeHandler to the list of registered change handlers
     * @param changeHandler the changeHandler to remove
     */
    public void addChangeHandler(@NotNull Runnable changeHandler) {
        myChangeHandlers.add(changeHandler);
    }

    /**
     * Removes the given changeHandler from the list of registered change handlers
     * @param changeHandler the changeHandler to remove
     */
    public void removeChangeHandler(@NotNull Runnable changeHandler) {
        myChangeHandlers.remove(changeHandler);
    }

    /**
     * Notifies all registered change handlers when the state changed
     */
    public void fireStateChanged() {
        for (Runnable handler : myChangeHandlers) {
            handler.run();
        }
    }

}

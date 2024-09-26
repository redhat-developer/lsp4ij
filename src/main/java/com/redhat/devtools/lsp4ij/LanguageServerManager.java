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
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.server.LanguageServerException;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class LanguageServerManager {

    private final Project project;

    /**
     * The start options.
     */
    public static class StartOptions {

        public static final StartOptions DEFAULT = new StartOptions();

        private boolean forceStart;

        private boolean willEnable;

        public StartOptions() {
            setWillEnable(true)
                    .setForceStart(false);
        }

        /**
         * Returns true if the language server should be started even if there are no open files corresponding to the language server and false otherwise.
         *
         * @return true if the language server should be started even if there are no open files corresponding to the language server and false otherwise.
         */
        public boolean isForceStart() {
            return forceStart;
        }

        /**
         * Set true if the language server should be started even if there are no open files corresponding to the language server and false otherwise.
         *
         * @param forceStart true if the language server should be started even if there are no open files corresponding to the language server and false otherwise.
         * @return the start options instance.
         */
        public StartOptions setForceStart(boolean forceStart) {
            this.forceStart = forceStart;
            return this;
        }

        /**
         * Returns true if the enabled state must be set before startup and false otherwise.
         *
         * <p>
         * If enable and start is set to false, and the language server is disabled, the start method will throw  {@link LanguageServerException}.
         * </p>
         *
         * @return true if the enabled state must be set before startup and false otherwise.
         */
        public boolean isWillEnable() {
            return willEnable;
        }

        /**
         * Set true if the enabled state must be set before startup and false otherwise.
         *
         * <p>
         * If enable and start is set to false, and the language server is disabled, the start method will throw  {@link LanguageServerException}.
         * </p>
         *
         * @param willEnable true if the enabled state must be set before startup and false otherwise.
         * @return the start options.
         */
        public StartOptions setWillEnable(boolean willEnable) {
            this.willEnable = willEnable;
            return this;
        }
    }

    /**
     * The stop options.
     */
    public static class StopOptions {

        public static final StopOptions DEFAULT = new StopOptions();

        private boolean willDisable;

        public StopOptions() {
            setWillDisable(true);
        }

        /**
         * Returns true if the language server must be disabled after stopping the language server and false otherwise.
         *
         * @return true if the language server must be disabled after stopping the language server and false otherwise.
         */
        public boolean isWillDisable() {
            return willDisable;
        }

        /**
         * Set true if the language server must be disabled after stopping the language server and false otherwise.
         *
         * @param willDisable true if the language server must be disabled after stopping the language server and false otherwise.
         * @return the stop options.
         */
        public StopOptions setWillDisable(boolean willDisable) {
            this.willDisable = willDisable;
            return this;
        }
    }

    /**
     * Returns the language server manager instance for the given project.
     *
     * @param project the project.
     * @return the language server manager instance for the given project.
     */
    public static LanguageServerManager getInstance(@NotNull Project project) {
        return project.getService(LanguageServerManager.class);
    }

    private LanguageServerManager(Project project) {
        this.project = project;
    }

    // --------------------- Server status

    /**
     * Returns the server status of the given language server id and null otherwise.
     *
     * @param languageServerId the language server id.
     * @return the server status of the given language server id and null otherwise.
     */
    @Nullable
    public ServerStatus getServerStatus(@NotNull String languageServerId) {
        var serverDefinition = LanguageServersRegistry.getInstance().getServerDefinition(languageServerId);
        if (serverDefinition == null) {
            return null;
        }
        return getServerStatus(serverDefinition);
    }

    /**
     * Returns the server status of the given language server.
     *
     * @param serverDefinition the language server definition.
     * @return the server status of the given language server.
     */
    @NotNull
    private ServerStatus getServerStatus(@NotNull LanguageServerDefinition serverDefinition) {
        for (var ls : LanguageServiceAccessor.getInstance(project).getStartedServers()) {
            if (serverDefinition.equals(ls.getServerDefinition())) {
                return ls.getServerStatus();
            }
        }
        return ServerStatus.none;
    }

    // --------------------- Start language server

    /**
     * Start the given language server id with default {@link StartOptions#DEFAULT}
     *
     * @param languageServerId the language server id to start.
     */
    public void start(@NotNull String languageServerId) {
        start(languageServerId, StartOptions.DEFAULT);
    }

    /**
     * Start the given language server id with the given start options.
     *
     * @param languageServerId the language server id to start.
     */
    public void start(@NotNull String languageServerId,
                      @NotNull StartOptions options) {
        var serverDefinition = LanguageServersRegistry.getInstance().getServerDefinition(languageServerId);
        if (serverDefinition == null) {
            return;
        }
        start(serverDefinition, options);
    }

    /**
     * Start the given language server with default {@link StartOptions#DEFAULT}
     *
     * @param serverDefinition the language server to start.
     */
    public void start(@NotNull LanguageServerDefinition serverDefinition) {
        start(serverDefinition, StartOptions.DEFAULT);
    }

    /**
     * Start the given language server with the given start options.
     *
     * @param serverDefinition the language server to start.
     */
    public void start(@NotNull LanguageServerDefinition serverDefinition,
                      @NotNull StartOptions options) {
        if (!serverDefinition.isEnabled(project) && !options.isWillEnable()) {
            throw new LanguageServerException("Server definition '" + serverDefinition.getId() + "' cannot be forced to be enable.");
        }
        // 1. Try to start the language server which has been already started.
        boolean started = false;
        for (var ls : LanguageServiceAccessor.getInstance(project).getStartedServers()) {
            if (serverDefinition.equals(ls.getServerDefinition())) {
                ls.restart();
                started = true;
            }
        }
        if (started) {
            return;
        }
        // 2. The language server has never started, start it.
        LanguageServiceAccessor.getInstance(project).findAndStartLanguageServerIfNeeded(serverDefinition, options.isForceStart(), project);
    }

    // --------------------- Stop language server

    /**
     * Stop the given language server id with default {@link StopOptions#DEFAULT}
     *
     * @param languageServerId the language server id to stop.
     */
    public void stop(@NotNull String languageServerId) {
        stop(languageServerId, StopOptions.DEFAULT);
    }

    /**
     * Stop the given language server id with the given stop options.
     *
     * @param languageServerId the language server id to stop.
     */

    public void stop(@NotNull String languageServerId,
                     @NotNull StopOptions options) {
        var serverDefinition = LanguageServersRegistry.getInstance().getServerDefinition(languageServerId);
        if (serverDefinition == null) {
            return;
        }
        stop(serverDefinition, options);
    }

    /**
     * Stop the given language server with the given start options.
     *
     * @param serverDefinition the language server to stop.
     */
    public void stop(@NotNull LanguageServerDefinition serverDefinition,
                     @NotNull StopOptions options) {
        // 1. Stop language server
        boolean stopped = false;
        for (var ls : LanguageServiceAccessor.getInstance(project).getStartedServers()) {
            if (serverDefinition.equals(ls.getServerDefinition())) {
                stopped = true;
                if (options.isWillDisable()) {
                    ls.stopAndDisable();
                } else {
                    ls.stop();
                }
            }
        }
        if (options.isWillDisable() && !stopped) {
            // 2. Disable language server
            serverDefinition.setEnabled(false, project);
        }
    }

    /**
     * Returns the initialized language server item from the given language server id and null otherwise.
     *
     * @param languageServerId the language server id.
     * @return the initialized language server item from the given language server id and null otherwise.
     */
    public CompletableFuture<@Nullable LanguageServerItem> getLanguageServer(@NotNull String languageServerId) {
        LanguageServerDefinition serverDefinition = LanguageServersRegistry.getInstance().getServerDefinition(languageServerId);
        if (serverDefinition == null) {
            return CompletableFuture.completedFuture(null);
        }
        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(Set.of(serverDefinition), null, null)
                .thenApply(servers -> {
                    if (servers.isEmpty()) {
                        return null;
                    }
                    return servers.get(0);
                });
    }

}

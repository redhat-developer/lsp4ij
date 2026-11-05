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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.installation.ServerInstallationContext;
import com.redhat.devtools.lsp4ij.server.LanguageServerException;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

public class LanguageServerManager {

    private final Project project;

    private LanguageServerManager(Project project) {
        this.project = project;
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

    /**
     * Returns the server status of the given language server id and null otherwise. For a definition with several
     * language server instances, this is the status of the oldest one; {@link #getServerStatus(String, WorkspaceFolder)}
     * gives the status of the instance with a given root.
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
        // The started servers keep the order in which they were created, so this is the oldest instance
        for (var ls : LanguageServiceAccessor.getInstance(project).getStartedServers()) {
            if (serverDefinition.equals(ls.getServerDefinition())) {
                return ls.getServerStatus();
            }
        }
        return ServerStatus.none;
    }

    /**
     * Returns the server status of the language server instance with the given root, for a definition with several
     * instances, see {@link com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy#hasServerInstances()}.
     * For a definition without instances, this is {@link #getServerStatus(String)}.
     *
     * @param languageServerId the language server id.
     * @param instanceRoot     the root of the language server instance.
     * @return the server status of the instance, {@link ServerStatus#none} if there is none, and null if there is no
     * language server with the given id.
     */
    @ApiStatus.Experimental
    @Nullable
    public ServerStatus getServerStatus(@NotNull String languageServerId,
                                        @NotNull WorkspaceFolder instanceRoot) {
        var serverDefinition = LanguageServersRegistry.getInstance().getServerDefinition(languageServerId);
        if (serverDefinition == null) {
            return null;
        }
        if (!serverDefinition.hasServerInstances()) {
            return getServerStatus(serverDefinition);
        }
        var instance = LanguageServiceAccessor.getInstance(project).findInstance(serverDefinition, instanceRoot);
        return instance != null ? instance.getServerStatus() : ServerStatus.none;
    }

    // --------------------- Server status

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

    // --------------------- Start language server

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
                if (options.isForceRestart() || ls.getServerStatus() != ServerStatus.started) {
                    ls.restart();
                }
                started = true;
            }
        }
        if (started) {
            return;
        }
        // 2. The language server has never started, start it.
        LanguageServiceAccessor.getInstance(project).findAndStartLanguageServerIfNeeded(serverDefinition, options.isForceStart(), project);
    }

    /**
     * Starts the language server instance with the given root, for a definition with several instances, see
     * {@link com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy#hasServerInstances()}. The
     * instance is restarted if it isn't started, or if {@link StartOptions#isForceRestart()} is set. If there is no
     * instance with that root, {@link StartOptions#isForceStart()} creates and starts it; without it, the open files of
     * the language server are connected, as {@link #start(String, StartOptions)} does. For a definition without
     * instances, this is {@link #start(String, StartOptions)}.
     *
     * @param languageServerId the language server id.
     * @param instanceRoot     the root of the language server instance.
     * @param options          the start options.
     */
    @ApiStatus.Experimental
    public void start(@NotNull String languageServerId,
                      @NotNull WorkspaceFolder instanceRoot,
                      @NotNull StartOptions options) {
        var serverDefinition = LanguageServersRegistry.getInstance().getServerDefinition(languageServerId);
        if (serverDefinition == null) {
            return;
        }
        if (!serverDefinition.hasServerInstances()) {
            start(serverDefinition, options);
            return;
        }
        if (!serverDefinition.isEnabled(project) && !options.isWillEnable()) {
            throw new LanguageServerException("Server definition '" + serverDefinition.getId() + "' cannot be forced to be enable.");
        }
        var accessor = LanguageServiceAccessor.getInstance(project);
        var instance = options.isForceStart()
                ? accessor.getOrCreateInstance(serverDefinition, instanceRoot)
                : accessor.findInstance(serverDefinition, instanceRoot);
        if (instance == null) {
            // As without a root: the instance starts if one of its files is open
            accessor.findAndStartLanguageServerIfNeeded(serverDefinition, false, project);
            return;
        }
        if (options.isForceRestart() || instance.getServerStatus() != ServerStatus.started) {
            instance.restart();
        }
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
     * Stops the language server instance with the given root, for a definition with several instances, see
     * {@link com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy#hasServerInstances()}. For a
     * definition without instances, this is {@link #stop(String, StopOptions)}.
     *
     * @param languageServerId the language server id.
     * @param instanceRoot     the root of the language server instance.
     * @param options          the stop options.
     */
    @ApiStatus.Experimental
    public void stop(@NotNull String languageServerId,
                     @NotNull WorkspaceFolder instanceRoot,
                     @NotNull StopOptions options) {
        var serverDefinition = LanguageServersRegistry.getInstance().getServerDefinition(languageServerId);
        if (serverDefinition == null) {
            return;
        }
        if (!serverDefinition.hasServerInstances()) {
            stop(serverDefinition, options);
            return;
        }
        var instance = LanguageServiceAccessor.getInstance(project).findInstance(serverDefinition, instanceRoot);
        if (instance == null) {
            return;
        }
        if (options.isWillDisable()) {
            instance.stopAndDisable();
        } else {
            instance.stop();
        }
    }

    // --------------------- Install language server

    /**
     * Install the given language server id with the given server installation context.
     *
     * @param languageServerId the language server id to install.
     */
    public void install(@NotNull String languageServerId,
                        @NotNull ServerInstallationContext context) {
        var serverDefinition = LanguageServersRegistry.getInstance().getServerDefinition(languageServerId);
        if (serverDefinition == null) {
            return;
        }
        install(serverDefinition, context);
    }

    /**
     * Install the given language server definition with the given server installation context.
     *
     * @param serverDefinition the language server to install.     *
     * @param context          the server installation context.
     */
    public void install(@NotNull LanguageServerDefinition serverDefinition,
                        @NotNull ServerInstallationContext context) {
        if (serverDefinition.hasServerInstances()) {
            installForInstances(serverDefinition, context);
            return;
        }
        boolean found = false;
        for (var ls : LanguageServiceAccessor.getInstance(project).getStartedServers()) {
            if (serverDefinition.equals(ls.getServerDefinition())) {
                // The language server has already been started, use this instance to install it
                found = true;
                ls.install(context);
            }
        }
        if (!found) {
            // The language server has never been started, create a new instance and install it
            var ls = LanguageServiceAccessor.getInstance(project)
                    .forceCreateServer(serverDefinition);
            ls.install(context);
        }
    }

    /**
     * Installs a language server with several instances: the installer runs once, as it installs the language server
     * for all of them. The instances that run are stopped for it, and started again once it is done, as a language
     * server without instances is. Without an instance, a language server is created only for the installation: one
     * without a root would serve every file of the definition.
     */
    private void installForInstances(@NotNull LanguageServerDefinition serverDefinition,
                                     @NotNull ServerInstallationContext context) {
        var instances = instancesOf(serverDefinition);
        if (instances.isEmpty()) {
            var installation = new LanguageServerWrapper(project, serverDefinition);
            installation.install(context)
                    .whenComplete((status, error) -> installation.stopAndDispose(false));
            return;
        }
        List<LanguageServerWrapper> running = new ArrayList<>();
        for (var instance : instances) {
            if (instance.getServerStatus() == ServerStatus.starting || instance.getServerStatus() == ServerStatus.started) {
                running.add(instance);
            }
        }
        // The installing instance stops and starts again by itself
        var installing = running.isEmpty() ? instances.get(0) : running.get(0);
        var others = running.stream().filter(instance -> instance != installing).toList();
        CompletableFuture.allOf(others.stream().map(LanguageServerWrapper::stop).toArray(CompletableFuture[]::new))
                .thenCompose(unused -> installing.install(context))
                .thenAccept(status -> others.forEach(LanguageServerWrapper::start));
    }

    /**
     * Returns the language server instances of the given definition, the oldest first.
     */
    private @NotNull List<LanguageServerWrapper> instancesOf(@NotNull LanguageServerDefinition serverDefinition) {
        return LanguageServiceAccessor.getInstance(project).getStartedServers()
                .stream()
                .filter(languageServer -> serverDefinition.equals(languageServer.getServerDefinition()))
                .toList();
    }

    // --------------------- Get language server

    /**
     * Returns the initialized language server item from the given language server id and null otherwise. For a
     * definition with several language server instances, this is the oldest one, which is started if it stopped;
     * {@link #getLanguageServer(String, VirtualFile)} gives the instance that serves a file, and
     * {@link #getLanguageServers(String)} gives all of them.
     *
     * @param languageServerId the language server id.
     * @return the initialized language server item from the given language server id and null otherwise.
     */
    public CompletableFuture<@Nullable LanguageServerItem> getLanguageServer(@NotNull String languageServerId) {
        LanguageServerDefinition serverDefinition = LanguageServersRegistry.getInstance().getServerDefinition(languageServerId);
        if (serverDefinition == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (serverDefinition.hasServerInstances()) {
            // Without a file, nothing tells which instance is meant: take the oldest one, and create none
            var instances = instancesOf(serverDefinition);
            return instances.isEmpty() ? CompletableFuture.completedFuture(null) : getInitializedServer(instances.get(0));
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

    /**
     * Returns the initialized language server item that serves the given file, and null if there is none. For a
     * definition with several language server instances, see
     * {@link com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy#hasServerInstances()}, this is
     * the instance that has the file connected, or the instance of the file's root, which is created and started if
     * needed. The file isn't connected to the language server. For a definition without instances, this is
     * {@link #getLanguageServer(String)}.
     *
     * @param languageServerId the language server id.
     * @param file             the file.
     * @return the initialized language server item that serves the given file, and null if there is none.
     */
    @ApiStatus.Experimental
    public CompletableFuture<@Nullable LanguageServerItem> getLanguageServer(@NotNull String languageServerId,
                                                                            @NotNull VirtualFile file) {
        var serverDefinition = LanguageServersRegistry.getInstance().getServerDefinition(languageServerId);
        if (serverDefinition == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!serverDefinition.hasServerInstances()) {
            return getLanguageServer(languageServerId);
        }
        // A workspace folder strategy can need read access, and do I/O
        CompletableFuture<LanguageServerWrapper> instance = new CompletableFuture<>();
        ReadAction.nonBlocking(() -> LanguageServiceAccessor.getInstance(project).getOrCreateInstanceOf(file, serverDefinition))
                .expireWith(project)
                .submit(AppExecutorUtil.getAppExecutorService())
                .onSuccess(instance::complete)
                .onError(error -> {
                    if (error instanceof CancellationException || error instanceof ProcessCanceledException) {
                        instance.complete(null);
                    } else {
                        instance.completeExceptionally(error);
                    }
                });
        return instance.thenCompose(languageServer -> languageServer != null
                ? getInitializedServer(languageServer)
                : CompletableFuture.completedFuture(null));
    }

    /**
     * Returns the initialized language server items of the given language server id: all instances of a definition
     * with several, the oldest first, and the one language server of a definition without instances. Instances that
     * stopped are started again.
     *
     * @param languageServerId the language server id.
     * @return the initialized language server items of the given language server id.
     */
    @ApiStatus.Experimental
    public CompletableFuture<@NotNull List<LanguageServerItem>> getLanguageServers(@NotNull String languageServerId) {
        var serverDefinition = LanguageServersRegistry.getInstance().getServerDefinition(languageServerId);
        if (serverDefinition == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        var accessor = LanguageServiceAccessor.getInstance(project);
        return accessor.getLanguageServers(Set.of(serverDefinition), null, null)
                .thenApply(servers -> {
                    // In the order of the instances, instead of the order in which they were initialized
                    var order = new ArrayList<>(accessor.getStartedServers());
                    var sorted = new ArrayList<>(servers);
                    sorted.sort(Comparator.comparingInt(server -> order.indexOf(server.getServerWrapper())));
                    return sorted;
                });
    }

    private static @NotNull CompletableFuture<@Nullable LanguageServerItem> getInitializedServer(@NotNull LanguageServerWrapper languageServer) {
        if (!languageServer.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        return languageServer.getInitializedServer()
                .thenApply(server -> server != null ? new LanguageServerItem(server, languageServer) : null)
                // As for the language servers of a request: a stop or restart while it starts is no error
                .exceptionally(LanguageServiceAccessor::handleFutureException);
    }

    /**
     * The start options.
     */
    public static class StartOptions {

        public static final StartOptions DEFAULT = new StartOptions();

        private boolean forceRestart;

        private boolean forceStart;

        private boolean willEnable;

        public StartOptions() {
            setWillEnable(true)
                    .setForceStart(false)
                    .setForceRestart(true);
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
         * <p>
         * For a definition with several language server instances, nothing tells which instance to create without a
         * file, so this starts none if none exists yet: {@link LanguageServerManager#start(String, WorkspaceFolder, StartOptions)} starts the
         * instance with a given root.
         * </p>
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

        public boolean isForceRestart() {
            return forceRestart;
        }

        public StartOptions setForceRestart(boolean forceRestart) {
            this.forceRestart = forceRestart;
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


}

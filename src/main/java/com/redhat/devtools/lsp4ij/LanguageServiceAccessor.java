/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerFileAssociation;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Language server accessor.
 */
public class LanguageServiceAccessor implements Disposable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServiceAccessor.class);

    private final Project project;

    private final LanguageServerDefinitionListener serverDefinitionListener = new LanguageServerDefinitionListener() {

        @Override
        public void handleAdded(@NotNull LanguageServerDefinitionListener.LanguageServerAddedEvent event) {
            // Do nothing
        }

        @Override
        public void handleRemoved(@NotNull LanguageServerDefinitionListener.LanguageServerRemovedEvent event) {
            // Dispose all servers which are removed
            List<LanguageServerWrapper> serversToDispose = startedServers
                    .stream()
                    .filter(server -> event.serverDefinitions.contains(server.getServerDefinition()))
                    .collect(Collectors.toList());
            serversToDispose.forEach(LanguageServerWrapper::dispose);
            // Remove all servers which are removed from the cache
            synchronized (startedServers) {
                startedServers.removeAll(serversToDispose);
            }
        }

        @Override
        public void handleChanged(@NotNull LanguageServerChangedEvent event) {
            if (event.commandChanged ||
                    event.userEnvironmentVariablesChanged ||
                    event.includeSystemEnvironmentVariablesChanged ||
                    event.mappingsChanged) {
                // Restart all servers where command or mappings has changed
                List<LanguageServerWrapper> serversToRestart = startedServers
                        .stream()
                        .filter(server -> event.serverDefinition.equals(server.getServerDefinition()))
                        .collect(Collectors.toList());
                serversToRestart.forEach(LanguageServerWrapper::restart);
            }
        }
    };

    public static LanguageServiceAccessor getInstance(@NotNull Project project) {
        return project.getService(LanguageServiceAccessor.class);
    }

    private LanguageServiceAccessor(Project project) {
        this.project = project;
        LanguageServersRegistry.getInstance().addLanguageServerDefinitionListener(serverDefinitionListener);
    }

    private final Set<LanguageServerWrapper> startedServers = new HashSet<>();

    public void checkCurrentlyOpenFiles() {
        VirtualFile[] files = FileEditorManager.getInstance(project).getOpenFiles();
        for (VirtualFile file : files) {
            getMatchedLanguageServersWrappers(file).thenAccept(wrappers -> {
                if (wrappers != null) {
                    for (LanguageServerWrapper wrapper : wrappers) {
                        try {
                            wrapper.connect(file);
                        } catch (IOException ex) {
                            LOGGER.warn(ex.getLocalizedMessage(), ex);
                        }
                        wrapper.getInitializedServer();
                    }
                }
            });
        }
    }

    @NotNull
    public CompletableFuture<List<LanguageServerItem>> getLanguageServers(@NotNull VirtualFile file,
                                                                          @Nullable Predicate<ServerCapabilities> filter) {
        URI uri = LSPIJUtils.toUri(file);
        if (uri == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        // Collect started (or not) language servers which matches the given file.
        CompletableFuture<Collection<LanguageServerWrapper>> matchedServers = getMatchedLanguageServersWrappers(file);
        if (matchedServers.isDone() && matchedServers.getNow(Collections.emptyList()).isEmpty()) {
            // None language servers matches the given file
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        // Returns the language servers which match the given file, start them and connect the file to each matched language server
        final List<LanguageServerItem> servers = Collections.synchronizedList(new ArrayList<>());
        try {
            return matchedServers
                    .thenComposeAsync(result -> CompletableFuture.allOf(result
                            .stream()
                            .map(wrapper ->
                                    wrapper.getInitializedServer()
                                            .thenComposeAsync(server -> {
                                                if (server != null && wrapper.isEnabled() && (filter == null || filter.test(wrapper.getServerCapabilities()))) {
                                                    try {
                                                        return wrapper.connect(file);
                                                    } catch (IOException ex) {
                                                        LOGGER.warn(ex.getLocalizedMessage(), ex);
                                                    }
                                                }
                                                return CompletableFuture.completedFuture(null);
                                            }).thenAccept(server -> {
                                                if (server != null) {
                                                    servers.add(new LanguageServerItem(server, wrapper));
                                                }
                                            })).toArray(CompletableFuture[]::new)))
                    .thenApply(theVoid -> servers);
        } catch (final ProcessCanceledException cancellation) {
            throw cancellation;
        } catch (final Exception e) {
            LOGGER.warn(e.getLocalizedMessage(), e);
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    /**
     * Return the started servers.
     *
     * @return the started servers.
     */
    public Set<LanguageServerWrapper> getStartedServers() {
        return startedServers;
    }

    public void projectClosing(Project project) {
        // On project closing, we dispose all language servers
        disposeAllServers();
    }

    /**
     * Get the requested language server instance for the given file. Starts the
     * language server if not already started.
     *
     * @param file                  the file for which the initialized LanguageServer shall be returned
     * @param lsDefinition          the language server definition
     * @param capabilitiesPredicate a predicate to check capabilities
     * @return a LanguageServer for the given file, which is defined with provided
     * server ID and conforms to specified request. If
     * {@code capabilitesPredicate} does not test positive for the server's
     * capabilities, {@code null} is returned.
     */
    public CompletableFuture<LanguageServer> getInitializedLanguageServer(@NotNull VirtualFile file,
                                                                          @NotNull Project project,
                                                                          @NotNull LanguageServerDefinition lsDefinition,
                                                                          Predicate<ServerCapabilities> capabilitiesPredicate)
            throws IOException {
        URI initialPath = LSPIJUtils.toUri(file);
        LanguageServerWrapper wrapper = getLSWrapperForConnection(file, project, lsDefinition, initialPath);
        if (wrapper != null && capabilitiesComply(wrapper, capabilitiesPredicate)) {
            wrapper.connect(file);
            return wrapper.getInitializedServer();
        }
        return null;
    }

    /**
     * Checks if the given {@code wrapper}'s capabilities comply with the given
     * {@code capabilitiesPredicate}.
     *
     * @param wrapper               the server that's capabilities are tested with
     *                              {@code capabilitiesPredicate}
     * @param capabilitiesPredicate predicate testing the capabilities of {@code wrapper}.
     * @return The result of applying the capabilities of {@code wrapper} to
     * {@code capabilitiesPredicate}, or {@code false} if
     * {@code capabilitiesPredicate == null} or
     * {@code wrapper.getServerCapabilities() == null}
     */
    private static boolean capabilitiesComply(LanguageServerWrapper wrapper,
                                              Predicate<ServerCapabilities> capabilitiesPredicate) {
        return capabilitiesPredicate == null
                || wrapper.getServerCapabilities() == null /* null check is workaround for https://github.com/TypeFox/ls-api/issues/47 */
                || capabilitiesPredicate.test(wrapper.getServerCapabilities());
    }

    @NotNull
    private CompletableFuture<Collection<LanguageServerWrapper>> getMatchedLanguageServersWrappers(@NotNull VirtualFile file) {
        MatchedLanguageServerDefinitions mappings = getMatchedLanguageServerDefinitions(file, project);
        if (mappings == MatchedLanguageServerDefinitions.NO_MATCH) {
            // There are no mapping for the given file
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        LinkedHashSet<LanguageServerWrapper> matchedServers = new LinkedHashSet<>();

        // Collect sync server definitions
        var serverDefinitions = mappings.getMatched();
        collectLanguageServersFromDefinition(file, project, serverDefinitions, matchedServers);

        CompletableFuture<Set<LanguageServerDefinition>> async = mappings.getAsyncMatched();
        if (async != null) {
            // Collect async server definitions
            return async
                    .thenApply(asyncServerDefinitions -> {
                        collectLanguageServersFromDefinition(file, project, asyncServerDefinitions, matchedServers);
                        return matchedServers;
                    });
        }
        return CompletableFuture.completedFuture(matchedServers);
    }

    /**
     * Get or create a language server wrapper for the given server definitions and add then to the given  matched servers.
     *
     * @param file              the file.
     * @param fileProject       the file project.
     * @param serverDefinitions the server definitions.
     * @param matchedServers    the list to update with get/created language server.
     */
    private void collectLanguageServersFromDefinition(@NotNull VirtualFile file, @NotNull Project fileProject, @NotNull Set<LanguageServerDefinition> serverDefinitions, @NotNull Set<LanguageServerWrapper> matchedServers) {
        synchronized (startedServers) {
            for (var serverDefinition : serverDefinitions) {
                boolean useExistingServer = false;
                // Loop for started language servers
                for (var startedServer : startedServers) {
                    if (startedServer.getServerDefinition().equals(serverDefinition)
                            && startedServer.canOperate(file)) {
                        // A started language server match the file, use it
                        matchedServers.add(startedServer);
                        useExistingServer = true;
                        break;
                    }
                }
                if (!useExistingServer) {
                    // There are none started servers which matches the file, create and add it.
                    LanguageServerWrapper wrapper = new LanguageServerWrapper(fileProject, serverDefinition);
                    startedServers.add(wrapper);
                    matchedServers.add(wrapper);
                }
            }
        }
    }

    /**
     * Store the matched language server definitions for a given file.
     */
    private static class MatchedLanguageServerDefinitions {

        public static final MatchedLanguageServerDefinitions NO_MATCH = new MatchedLanguageServerDefinitions(Collections.emptySet(), null);

        private final Set<LanguageServerDefinition> matched;

        private final CompletableFuture<Set<LanguageServerDefinition>> asyncMatched;

        public MatchedLanguageServerDefinitions(@NotNull Set<LanguageServerDefinition> matchedLanguageServersDefinition, CompletableFuture<Set<LanguageServerDefinition>> async) {
            this.matched = matchedLanguageServersDefinition;
            this.asyncMatched = async;
        }

        /**
         * Return the matched server definitions get synchronously.
         *
         * @return the matched server definitions get synchronously.
         */
        public @NotNull Set<LanguageServerDefinition> getMatched() {
            return matched;
        }

        /**
         * Return the matched server definitions get asynchronously or null otherwise.
         *
         * @return the matched server definitions get asynchronously or null otherwise.
         */
        public CompletableFuture<Set<LanguageServerDefinition>> getAsyncMatched() {
            return asyncMatched;
        }
    }

    /**
     * Returns the matched language server definitions for the given file.
     *
     * @param file        the file.
     * @param fileProject the file project.
     * @return the matched language server definitions for the given file.
     */
    private MatchedLanguageServerDefinitions getMatchedLanguageServerDefinitions(@NotNull VirtualFile file, @NotNull Project fileProject) {

        Set<LanguageServerDefinition> syncMatchedDefinitions = null;
        Set<LanguageServerFileAssociation> asyncMatchedDefinitions = null;

        // look for running language servers via content-type
        Queue<Object> languages = new LinkedList<>();
        Set<Object> processedContentTypes = new HashSet<>();
        Language language = LSPIJUtils.getFileLanguage(file, project);
        if (language != null) {
            languages.add(language);
        }
        FileType fileType = file.getFileType();
        if (fileType != null) {
            languages.add(fileType);
        }

        while (!languages.isEmpty()) {
            Object contentType = languages.poll();
            if (processedContentTypes.contains(contentType)) {
                continue;
            }
            Language currentLanguage = null;
            FileType currentFileType = null;
            if (contentType instanceof FileType) {
                currentFileType = (FileType) contentType;
            } else {
                currentLanguage = (Language) contentType;
            }
            // Loop for server/language mapping
            for (LanguageServerFileAssociation mapping : LanguageServersRegistry.getInstance()
                    .findLanguageServerDefinitionFor(currentLanguage, currentFileType, file)) {
                if (mapping == null || !mapping.isEnabled() || (syncMatchedDefinitions != null && syncMatchedDefinitions.contains(mapping.getServerDefinition()))) {
                    // the mapping is disabled
                    // or the server definition has been already added
                    continue;
                }
                if (mapping.shouldBeMatchedAsynchronously(fileProject)) {
                    // Async mapping
                    // Mapping must be done asynchronously because the match of DocumentMatcher of the mapping need to be done asynchronously
                    // This usecase comes from for instance when custom match need to collect classes from the Java project and requires read only action.
                    if (asyncMatchedDefinitions == null) {
                        asyncMatchedDefinitions = new HashSet<>();
                    }
                    asyncMatchedDefinitions.add(mapping);
                } else {
                    // Sync mapping
                    if (match(file, fileProject, mapping)) {
                        if (syncMatchedDefinitions == null) {
                            syncMatchedDefinitions = new HashSet<>();
                        }
                        syncMatchedDefinitions.add(mapping.getServerDefinition());
                    }
                }
            }
        }
        if (syncMatchedDefinitions != null || asyncMatchedDefinitions != null) {
            // Some match...
            CompletableFuture<Set<LanguageServerDefinition>> async = null;
            if (asyncMatchedDefinitions != null) {
                // Async match, compute a future which process all matchAsync and return a list of server definitions
                final Set<LanguageServerDefinition> serverDefinitions = Collections.synchronizedSet(new HashSet<>());
                async = CompletableFuture.allOf(asyncMatchedDefinitions
                                .stream()
                                .map(mapping -> {
                                            return mapping
                                                    .matchAsync(file, fileProject)
                                                    .thenApply(result -> {
                                                        if (result) {
                                                            serverDefinitions.add(mapping.getServerDefinition());
                                                        }
                                                        return null;
                                                    });
                                        }
                                )
                                .toArray(CompletableFuture[]::new))
                        .thenApply(theVoid -> serverDefinitions);
            }
            return new MatchedLanguageServerDefinitions(syncMatchedDefinitions != null ? syncMatchedDefinitions : Collections.emptySet(), async);
        }
        // No match...
        return MatchedLanguageServerDefinitions.NO_MATCH;
    }

    private static boolean match(VirtualFile file, Project fileProject, LanguageServerFileAssociation mapping) {
        if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
            return ReadAction.compute(() -> mapping.match(file, fileProject));
        }
        return mapping.match(file, fileProject);
    }

    private LanguageServerWrapper getLSWrapperForConnection(@NotNull VirtualFile file,
                                                            @NotNull Project project,
                                                            LanguageServerDefinition serverDefinition,
                                                            URI initialPath) throws IOException {
        if (!serverDefinition.isEnabled()) {
            // don't return a language server wrapper for the given server definition
            return null;
        }
        LanguageServerWrapper wrapper = null;

        synchronized (startedServers) {
            for (LanguageServerWrapper startedWrapper : getStartedLSWrappers(file)) {
                if (startedWrapper.getServerDefinition().equals(serverDefinition)) {
                    wrapper = startedWrapper;
                    break;
                }
            }
            if (wrapper == null) {
                wrapper = new LanguageServerWrapper(project, serverDefinition, initialPath);
                wrapper.start();
            }

            startedServers.add(wrapper);
        }
        return wrapper;
    }

    private List<LanguageServerWrapper> getStartedLSWrappers(
            VirtualFile file) {
        return getStartedLSWrappers(wrapper -> wrapper.canOperate(file));
    }

    private List<LanguageServerWrapper> getStartedLSWrappers(Predicate<LanguageServerWrapper> predicate) {
        return startedServers.stream().filter(predicate)
                .collect(Collectors.toList());
        // TODO multi-root: also return servers which support multi-root?
    }

    /**
     * Gets list of running LS satisfying a capability predicate. This does not
     * start any matching language servers, it returns the already running ones.
     *
     * @param request
     * @return list of Language Servers
     */
    @NotNull
    public List<LanguageServer> getActiveLanguageServers(Predicate<ServerCapabilities> request) {
        return getLanguageServers(null, request, true);
    }

    /**
     * Gets list of LS initialized for given project
     *
     * @param onlyActiveLS true if this method should return only the already running
     *                     language servers, otherwise previously started language servers
     *                     will be re-activated
     * @return list of Language Servers
     */
    @NotNull
    public List<LanguageServer> getLanguageServers(@Nullable Project project,
                                                   Predicate<ServerCapabilities> request, boolean onlyActiveLS) {
        List<LanguageServer> serverInfos = new ArrayList<>();
        for (LanguageServerWrapper wrapper : startedServers) {
            if ((!onlyActiveLS || wrapper.isActive()) && (project == null || wrapper.canOperate(project))) {
                @Nullable
                LanguageServer server = wrapper.getServer();
                if (server == null) {
                    continue;
                }
                if (request == null
                        || wrapper.getServerCapabilities() == null /* null check is workaround for https://github.com/TypeFox/ls-api/issues/47 */
                        || request.test(wrapper.getServerCapabilities())) {
                    serverInfos.add(server);
                }
            }
        }
        return serverInfos;
    }

    public boolean checkCapability(LanguageServer languageServer, Predicate<ServerCapabilities> condition) {
        return startedServers.stream().filter(wrapper -> wrapper.isActive() && wrapper.getServer() == languageServer)
                .anyMatch(wrapper -> condition.test(wrapper.getServerCapabilities()));
    }

    public Optional<LanguageServerDefinition> resolveServerDefinition(LanguageServer languageServer) {
        synchronized (startedServers) {
            return startedServers.stream().filter(wrapper -> languageServer.equals(wrapper.getServer())).findFirst().map(wrapper -> wrapper.getServerDefinition());
        }
    }

    @Override
    public void dispose() {
        LanguageServersRegistry.getInstance().removeLanguageServerDefinitionListener(serverDefinitionListener);
        disposeAllServers();
    }

    private void disposeAllServers() {
        synchronized (startedServers) {
            startedServers.forEach(ls -> {
                if (project.equals(ls.getProject())) {
                    ls.dispose();
                }
            });
            startedServers.clear();
        }
    }

}
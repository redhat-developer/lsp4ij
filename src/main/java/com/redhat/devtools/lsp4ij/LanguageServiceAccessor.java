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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerFileAssociation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Language server accessor.
 * <p>
 * This class is considered internal and should not be used in other plugins.
 * <p>
 * If adopters need to collect some language servers, LSP4IJ must provide an API for that.
 */
@ApiStatus.Internal
public class LanguageServiceAccessor implements Disposable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServiceAccessor.class);

    private final Project project;

    private final LanguageServerDefinitionListener serverDefinitionListener = new LanguageServerDefinitionListener() {

        @Override
        public void handleAdded(@NotNull LanguageServerDefinitionListener.LanguageServerAddedEvent event) {
            // Check all projects for files that match the added language server and start it if necessary
            checkCurrentlyOpenFiles(event.serverDefinitions);
        }

        @Override
        public void handleRemoved(@NotNull LanguageServerDefinitionListener.LanguageServerRemovedEvent event) {
            // Dispose all servers which are removed
            List<LanguageServerWrapper> serversToDispose = startedServers
                    .stream()
                    .filter(server -> event.serverDefinitions.contains(server.getServerDefinition()))
                    .toList();
            serversToDispose.forEach(LanguageServerWrapper::dispose);
            // Remove all servers which are removed from the cache
            synchronized (startedServers) {
                serversToDispose.forEach(startedServers::remove);
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
                        .toList();
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

    /**
     * Check each project for open files and start an LS if matching one is found and is not started yet
     *
     * @param definitions definition of the language server to match to the wrapper
     */
    private void checkCurrentlyOpenFiles(Collection<LanguageServerDefinition> definitions) {
        if (definitions.isEmpty()) {
            return;
        }
        for (LanguageServerDefinition definition : definitions) {
            Project[] projects = ProjectManager.getInstance().getOpenProjects();
            for (Project p : projects) {
                try {
                    findAndStartLanguageServerIfNeeded(definition, false, p);
                } catch (Exception e) {
                    LOGGER.error("Error while starting language server for the language server '" + definition.getDisplayName() + "'.", e);
                }
            }
        }
    }

    public void findAndStartLanguageServerIfNeeded(LanguageServerDefinition definition, boolean forceStart, Project project) {
        if (forceStart) {
            // The language server must be started even if there is no open file corresponding to it.
            LinkedHashSet<LanguageServerWrapper> matchedServers = new LinkedHashSet<>();
            collectLanguageServersFromDefinition(null, Set.of(definition), matchedServers, null);
            for (var ls : matchedServers) {
                ls.restart();
            }
        } else {
            // The language server should only be started if there is an open file corresponding to it.
            VirtualFile[] files = FileEditorManager.getInstance(project).getOpenFiles();
            for (VirtualFile file : files) {
                findAndStartLsForFile(file, definition);
            }
        }
    }

    /**
     * Try to find a ls wrapper for the file and definition. Connect the file, if a matching one is found
     *
     * @param file       to handle
     * @param definition definition used to match to the wrapper
     */
    private void findAndStartLsForFile(@NotNull VirtualFile file,
                                       @NotNull LanguageServerDefinition definition) {
        getLanguageServers(file, null, null, definition);
    }

    /**
     * Returns true if the given file matches one of started language server with the given filter and false otherwise.
     *
     * @param file   the file.
     * @param filter the filter.
     * @return true if the given file matches one of started language server with the given filter and false otherwise.
     */
    public boolean hasAny(@NotNull VirtualFile file,
                          @NotNull Predicate<LanguageServerWrapper> filter) {
        var startedServers = getStartedServers();
        if (startedServers.isEmpty()) {
            return false;
        }
        MatchedLanguageServerDefinitions mappings = getMatchedLanguageServerDefinitions(file, project, true);
        if (mappings == MatchedLanguageServerDefinitions.NO_MATCH) {
            return false;
        }
        var matched = mappings.getMatched();
        for (var startedServer : startedServers) {
            if (ServerStatus.started.equals(startedServer.getServerStatus()) &&
                    matched.contains(startedServer.getServerDefinition()) && filter.test(startedServer)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    public CompletableFuture<@NotNull List<LanguageServerItem>> getLanguageServers(@Nullable Predicate<LSPClientFeatures> beforeStartingServerFilter,
                                                                                   @Nullable Predicate<LSPClientFeatures> afterStartingServerFilter) {
        Set<LanguageServerDefinition> serverDefinitions = new HashSet<>(LanguageServersRegistry.getInstance().getServerDefinitions());
        return getLanguageServers(serverDefinitions, beforeStartingServerFilter, afterStartingServerFilter);
    }

    @NotNull
    CompletableFuture<@NotNull List<LanguageServerItem>> getLanguageServers(@NotNull Set<LanguageServerDefinition> serverDefinitions,
                                                                            @Nullable Predicate<LSPClientFeatures> beforeStartingServerFilter,
                                                                            @Nullable Predicate<LSPClientFeatures> afterStartingServerFilter) {
        Set<LanguageServerWrapper> matchedServers = new LinkedHashSet<>();
        collectLanguageServersFromDefinition(null, serverDefinitions, matchedServers, beforeStartingServerFilter);
        final List<LanguageServerItem> servers = Collections.synchronizedList(new ArrayList<>());
        try {
            return CompletableFuture.allOf(matchedServers
                            .stream()
                            .filter(LanguageServerWrapper::isEnabled)
                            .map(wrapper ->
                                    wrapper.getInitializedServer()
                                            .thenComposeAsync(server -> {
                                                if (server != null &&
                                                        (afterStartingServerFilter == null || afterStartingServerFilter.test(wrapper.getClientFeatures()))) {
                                                    servers.add(new LanguageServerItem(server, wrapper));
                                                }
                                                return CompletableFuture.completedFuture(null);
                                            }))
                            .toArray(CompletableFuture[]::new))
                    .thenApply(theVoid -> servers);
        } catch (ProcessCanceledException cancellation) {
            throw cancellation;
        } catch (Exception e) {
            LOGGER.warn(e.getLocalizedMessage(), e);
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    @NotNull
    public CompletableFuture<@NotNull List<LanguageServerItem>> getLanguageServers(@NotNull VirtualFile file,
                                                                                   @Nullable Predicate<LSPClientFeatures> beforeStartingServerFilter,
                                                                                   @Nullable Predicate<LSPClientFeatures> afterStartingServerFilter) {
        return getLanguageServers(file, beforeStartingServerFilter, afterStartingServerFilter, null);
    }

    @NotNull
    private CompletableFuture<@NotNull List<LanguageServerItem>> getLanguageServers(@NotNull VirtualFile file,
                                                                                    @Nullable Predicate<LSPClientFeatures> beforeStartingServerFilter,
                                                                                    @Nullable Predicate<LSPClientFeatures> afterStartingServerFilter,
                                                                                    @Nullable LanguageServerDefinition matchServerDefinition) {
        // Collect started (or not) language servers which matches the given file.
        CompletableFuture<Collection<LanguageServerWrapper>> matchedServers = getMatchedLanguageServersWrappers(file, matchServerDefinition, beforeStartingServerFilter);
        if (matchedServers.isDone() && matchedServers.getNow(Collections.emptyList()).isEmpty()) {
            // None language servers matches the given file
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        // getLanguageServers is generally called with Read Access.
        // We get the Document instance now, to avoid creating a new Read Action thread to get it from the file.
        // The document instance is only used when the file is opened, to add
        // LSP document listener to manage didOpen, didChange, etc.
        Document document = ApplicationManager.getApplication().isReadAccessAllowed() ? LSPIJUtils.getDocument(file) : null;

        // Returns the language servers which match the given file, start them and connect the file to each matched language server
        final List<LanguageServerItem> servers = Collections.synchronizedList(new ArrayList<>());
        try {
            return matchedServers
                    .thenComposeAsync(result -> CompletableFuture.allOf(result
                            .stream()
                            .filter(LanguageServerWrapper::isEnabled)
                            .filter(wrapper->wrapper.getClientFeatures().isEnabled(file))
                            .map(wrapper ->
                                    wrapper.getInitializedServer()
                                            .thenComposeAsync(server -> {
                                                if (server != null &&
                                                        (afterStartingServerFilter == null || afterStartingServerFilter.test(wrapper.getClientFeatures()))) {
                                                    return wrapper.connect(file, document);
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
        return Collections.unmodifiableSet(startedServers);
    }

    public void projectClosing(Project project) {
        // On project closing, we dispose all language servers
        disposeAllServers();
    }

    @NotNull
    private CompletableFuture<Collection<LanguageServerWrapper>> getMatchedLanguageServersWrappers(
            @NotNull VirtualFile file,
            @Nullable LanguageServerDefinition matchServerDefinition,
            @Nullable Predicate<LSPClientFeatures> beforeStartingServerFilter) {
        MatchedLanguageServerDefinitions mappings = getMatchedLanguageServerDefinitions(file, project, false);
        if (mappings == MatchedLanguageServerDefinitions.NO_MATCH) {
            // There are no mapping for the given file
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        Set<LanguageServerWrapper> matchedServers = new LinkedHashSet<>();

        // Collect sync server definitions
        var serverDefinitions = mappings.getMatched();
        if (matchServerDefinition != null) {
            if (!serverDefinitions.contains(matchServerDefinition)) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            collectLanguageServersFromDefinition(file, Set.of(matchServerDefinition), matchedServers, beforeStartingServerFilter);
        } else {
            collectLanguageServersFromDefinition(file, serverDefinitions, matchedServers, beforeStartingServerFilter);
        }

        CompletableFuture<Set<LanguageServerDefinition>> async = mappings.getAsyncMatched();
        if (async != null) {
            // Collect async server definitions
            return async
                    .thenApply(asyncServerDefinitions -> {
                        collectLanguageServersFromDefinition(file, asyncServerDefinitions, matchedServers, beforeStartingServerFilter);
                        return matchedServers;
                    });
        }
        return CompletableFuture.completedFuture(matchedServers);
    }

    /**
     * Get or create a language server wrapper for the given server definitions and add then to the given  matched servers.
     *
     * @param file                       the file.
     * @param serverDefinitions          the server definitions.
     * @param matchedServers             the list to update with get/created language server.
     * @param beforeStartingServerFilter
     */
    private void collectLanguageServersFromDefinition(@Nullable VirtualFile file,
                                                      @NotNull Set<LanguageServerDefinition> serverDefinitions,
                                                      @NotNull Set<LanguageServerWrapper> matchedServers,
                                                      @Nullable Predicate<LSPClientFeatures> beforeStartingServerFilter) {
        synchronized (startedServers) {
            for (var serverDefinition : serverDefinitions) {
                boolean useExistingServer = false;
                // Loop for started language servers
                for (var startedServer : startedServers) {
                    if (startedServer.getServerDefinition().equals(serverDefinition)
                            && (file == null || startedServer.canOperate(file))
                            && (beforeStartingServerFilter == null || beforeStartingServerFilter.test(startedServer.getClientFeatures()))) {
                        // A started language server match the file, use it
                        matchedServers.add(startedServer);
                        useExistingServer = true;
                        break;
                    }
                }
                if (!useExistingServer) {
                    // There are none started servers which matches the file, create and add it.
                    LanguageServerWrapper wrapper = new LanguageServerWrapper(project, serverDefinition);
                    if (beforeStartingServerFilter == null || beforeStartingServerFilter.test(wrapper.getClientFeatures())) {
                        startedServers.add(wrapper);
                        matchedServers.add(wrapper);
                    }
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
     * @param ignoreMatch true if {@link DocumentMatcher} must be ignored when mapping matches the given file and false otherwise.
     * @return the matched language server definitions for the given file.
     */
    private MatchedLanguageServerDefinitions getMatchedLanguageServerDefinitions(@NotNull VirtualFile file,
                                                                                 @NotNull Project fileProject,
                                                                                 boolean ignoreMatch) {

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
        languages.add(fileType);

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
                if (mapping == null || !mapping.isEnabled(project) || (syncMatchedDefinitions != null && syncMatchedDefinitions.contains(mapping.getServerDefinition()))) {
                    // the mapping is disabled
                    // or the server definition has been already added
                    continue;
                }
                if (ignoreMatch) {
                    if (syncMatchedDefinitions == null) {
                        syncMatchedDefinitions = new HashSet<>();
                    }
                    syncMatchedDefinitions.add(mapping.getServerDefinition());
                } else {
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
        }
        if (syncMatchedDefinitions != null || asyncMatchedDefinitions != null) {
            // Some match...
            CompletableFuture<Set<LanguageServerDefinition>> async = null;
            if (asyncMatchedDefinitions != null) {
                // Async match, compute a future which process all matchAsync and return a list of server definitions
                final Set<LanguageServerDefinition> serverDefinitions = Collections.synchronizedSet(new HashSet<>());
                async = CompletableFuture.allOf(asyncMatchedDefinitions
                                .stream()
                                .map(mapping -> mapping
                                        .matchAsync(file, fileProject)
                                        .thenApply(result -> {
                                            if (result) {
                                                serverDefinitions.add(mapping.getServerDefinition());
                                            }
                                            return null;
                                        })
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
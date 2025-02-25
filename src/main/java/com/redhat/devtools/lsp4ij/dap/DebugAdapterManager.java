/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import com.redhat.devtools.lsp4ij.dap.configurations.DebuggableFile;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.definitions.extension.DebugAdapterServerExtensionPointBean;
import com.redhat.devtools.lsp4ij.dap.definitions.extension.ExtensionDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterServerListener;
import com.redhat.devtools.lsp4ij.dap.settings.UserDefinedDebugAdapterServerSettings;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.server.definition.ServerFileNamePatternMapping;
import com.redhat.devtools.lsp4ij.server.definition.ServerFileTypeMapping;
import com.redhat.devtools.lsp4ij.server.definition.ServerLanguageMapping;
import com.redhat.devtools.lsp4ij.server.definition.ServerMapping;
import com.redhat.devtools.lsp4ij.server.definition.extension.FileNamePatternMappingExtensionPointBean;
import com.redhat.devtools.lsp4ij.server.definition.extension.FileTypeMappingExtensionPointBean;
import com.redhat.devtools.lsp4ij.server.definition.extension.LanguageMappingExtensionPointBean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static com.redhat.devtools.lsp4ij.dap.DAPIJUtils.getFilePath;

/**
 * This class is responsible for managing Debug Adapter Protocol (DAP) servers and configurations.
 * It provides methods to add, remove, and update server definitions, as well as checking if files are debuggable
 * within a specific project. It also supports notifications for listeners when servers are added or removed.
 */

public class DebugAdapterManager implements DebuggableFile {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugAdapterManager.class);

    public static DebugAdapterManager getInstance() {
        return ApplicationManager.getApplication().getService(DebugAdapterManager.class);
    }

    private final Map<String, DebugAdapterServerDefinition> serverDefinitions = new HashMap<>();

    private final Collection<DebugAdapterServerListener> listeners = new CopyOnWriteArrayList<>();

    public DebugAdapterManager() {
        initialize();
    }

    /**
     * Adds a listener that will be notified when a Debug Adapter Server is added or removed.
     *
     * @param listener the listener to add
     */
    public void addDebugAdapterServerListener(@NotNull DebugAdapterServerListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener that was previously added.
     *
     * @param listener the listener to remove
     */
    public void removeDebugAdapterServerListener(@NotNull DebugAdapterServerListener listener) {
        listeners.remove(listener);
    }

    /**
     * Adds a new Debug Adapter Server to the manager and notifies listeners.
     *
     * @param serverDefinition the server definition to add
     */
    public void addDebugAdapterServer(@NotNull DebugAdapterServerDefinition serverDefinition) {
        addDebugAdapterServerWithoutNotification(serverDefinition);
        // Notifications
        var event = new DebugAdapterServerListener.AddedEvent(Collections.singleton(serverDefinition));
        for (var listener : this.listeners) {
            try {
                listener.handleAdded(event);
            } catch (Exception e) {
                LOGGER.error("Error while adding Debug Adapter Server with id='" + serverDefinition.getId() + "'", e);
            }
        }
    }

    /**
     * Adds a new Debug Adapter Server without triggering notifications.
     *
     * @param serverDefinition the server definition to add
     */
    public void addDebugAdapterServerWithoutNotification(@NotNull DebugAdapterServerDefinition serverDefinition) {
        String debugAdapterServerId = serverDefinition.getId();
        serverDefinitions.put(debugAdapterServerId, serverDefinition);
        // Update settings
        if (serverDefinition instanceof UserDefinedDebugAdapterServerDefinition definitionFromSettings) {
            UserDefinedDebugAdapterServerSettings.ItemSettings settings = new UserDefinedDebugAdapterServerSettings.ItemSettings();
            settings.setServerId(definitionFromSettings.getId());
            settings.setServerName(definitionFromSettings.getDisplayName());
            settings.setUserEnvironmentVariables(definitionFromSettings.getUserEnvironmentVariables());
            settings.setIncludeSystemEnvironmentVariables(definitionFromSettings.isIncludeSystemEnvironmentVariables());
            settings.setCommandLine(definitionFromSettings.getCommandLine());
            settings.setConnectTimeout(definitionFromSettings.getConnectTimeout());
            settings.setDebugServerReadyPattern(definitionFromSettings.getDebugServerReadyPattern());
            List<ServerMappingSettings> newMappings = Stream.concat(definitionFromSettings.getLanguageMappings().stream(),
                            definitionFromSettings.getFileTypeMappings().stream())
                    .toList();
            settings.setMappings(newMappings);
            settings.setLaunchConfigurations(definitionFromSettings.getLaunchConfigurations());
            settings.setAttachAddress(definitionFromSettings.getAttachAddress());
            settings.setAttachPort(definitionFromSettings.getAttachPort());
            UserDefinedDebugAdapterServerSettings.getInstance().setSettings(debugAdapterServerId, settings);
        }
    }

    /**
     * Removes a Debug Adapter Server from the manager and notifies listeners.
     *
     * @param serverDefinition the server definition to remove
     */
    public void removeDebugAdapterServer(@NotNull DebugAdapterServerDefinition serverDefinition) {
        String serverId = serverDefinition.getId();
        serverDefinitions.remove(serverId);
        // Update settings
        UserDefinedDebugAdapterServerSettings.getInstance().removeSettings(serverId);
        // Notifications
        var event = new DebugAdapterServerListener.RemovedEvent(Collections.singleton(serverDefinition));
        for (var listener : this.listeners) {
            try {
                listener.handleRemoved(event);
            } catch (Exception e) {
                LOGGER.error("Error while DAP server definition is added with id='" + serverDefinition.getId() + "'", e);
            }
        }
    }

    /**
     * Retrieves a Debug Adapter Server by its unique identifier.
     *
     * @param serverId the identifier of the server
     * @return the Debug Adapter Server definition, or {@code null} if no such server is found
     */
    @Nullable
    public DebugAdapterServerDefinition getDebugAdapterServerById(@NotNull String serverId) {
        return serverDefinitions.get(serverId);
    }

    /**
     * Retrieves all the registered Debug Adapter Servers.
     *
     * @return a collection of all registered Debug Adapter Servers
     */
    public Collection<DebugAdapterServerDefinition> getDebugAdapterServers() {
        return Collections.unmodifiableCollection(serverDefinitions.values());
    }

    public record UpdateDebugAdapterServerRequest(
            @NotNull UserDefinedDebugAdapterServerDefinition serverDefinition,
            @Nullable String name,
            @Nullable Map<String, String> userEnvironmentVariables,
            boolean includeSystemEnvironmentVariables,
            @Nullable String commandLine,
            int connectTimeout,
            @Nullable String debugServerReadyPattern,
            @NotNull List<ServerMappingSettings> languageMappings,
            @NotNull List<ServerMappingSettings> fileTypeMappings,
            @Nullable List<LaunchConfiguration> launchConfigurations,
            @Nullable String attachAddress,
            @Nullable String attachPort) {
    }

    /**
     * Updates the server definition with new parameters.
     *
     * @param request the request containing the new parameters for the server definition
     * @param notify whether or not to notify listeners of the update
     * @return the update event, or {@code null} if no significant changes were made
     */
    @Nullable
    public DebugAdapterServerListener.ChangedEvent updateServerDefinition(@NotNull DebugAdapterManager.UpdateDebugAdapterServerRequest request,
                                                                          boolean notify) {
        var serverDefinition = request.serverDefinition();
        String serverId = request.serverDefinition().getId();
        serverDefinition.setName(request.name());
        serverDefinition.setCommandLine(request.commandLine());
        serverDefinition.setConnectTimeout(request.connectTimeout());
        serverDefinition.setDebugServerReadyPattern(request.debugServerReadyPattern());
        serverDefinition.setUserEnvironmentVariables(request.userEnvironmentVariables());
        serverDefinition.setIncludeSystemEnvironmentVariables(request.includeSystemEnvironmentVariables());

        serverDefinition.setLanguageMappings(request.languageMappings());
        serverDefinition.setFileTypeMappings(request.fileTypeMappings());

        serverDefinition.setLaunchConfigurations(request.launchConfigurations());

        List<ServerMappingSettings> mappings = Stream.concat(request.languageMappings().stream(), request.fileTypeMappings().stream()).toList();
        UserDefinedDebugAdapterServerSettings.ItemSettings settings = UserDefinedDebugAdapterServerSettings.getInstance().getSettings(serverId);
        boolean nameChanged = !Objects.equals(settings.getServerName(), request.name());
        boolean userEnvironmentVariablesChanged = !Objects.equals(settings.getUserEnvironmentVariables(), request.userEnvironmentVariables());
        boolean includeSystemEnvironmentVariablesChanged = settings.isIncludeSystemEnvironmentVariables() != request.includeSystemEnvironmentVariables();
        boolean commandChanged = !Objects.equals(settings.getCommandLine(), request.commandLine());
        boolean connectTimeoutChanged = !Objects.equals(settings.getConnectTimeout(), request.connectTimeout());
        boolean debugServerReadyPatternChanged = !Objects.equals(settings.getDebugServerReadyPattern(), request.debugServerReadyPattern());
        boolean mappingsChanged = !Objects.deepEquals(settings.getMappings(), mappings);
        boolean launchConfigurationChanged = !Objects.equals(settings.getLaunchConfigurations(), request.launchConfigurations());
        boolean attachAddressChanged = !Objects.equals(settings.getAttachAddress(), request.attachAddress());
        boolean attachPortChanged = !Objects.equals(settings.getAttachAddress(), request.attachPort());

        // Not checking whether client config changed because that shouldn't result in a LanguageServerChangedEvent

        settings.setServerName(request.name());
        settings.setUserEnvironmentVariables(request.userEnvironmentVariables());
        settings.setIncludeSystemEnvironmentVariables(request.includeSystemEnvironmentVariables());
        settings.setCommandLine(request.commandLine());
        settings.setConnectTimeout(request.connectTimeout);
        settings.setDebugServerReadyPattern(request.debugServerReadyPattern);
        settings.setMappings(mappings);
        settings.setLaunchConfigurations(request.launchConfigurations());

        if (nameChanged || userEnvironmentVariablesChanged || includeSystemEnvironmentVariablesChanged ||
                commandChanged || connectTimeoutChanged || debugServerReadyPatternChanged ||
                mappingsChanged || launchConfigurationChanged ||
        attachAddressChanged || attachPortChanged) {
            // Notifications
            DebugAdapterServerListener.ChangedEvent event = new DebugAdapterServerListener.ChangedEvent(
                    serverDefinition,
                    nameChanged,
                    commandChanged,
                    userEnvironmentVariablesChanged,
                    includeSystemEnvironmentVariablesChanged,
                    connectTimeoutChanged,
                    debugServerReadyPatternChanged,
                    mappingsChanged,
                    launchConfigurationChanged,
                    attachAddressChanged,
                    attachPortChanged);
            if (notify) {
                handleChangeEvent(event);
            }
            return event;
        }
        return null;
    }

    public void handleChangeEvent(DebugAdapterServerListener.ChangedEvent event) {
        for (DebugAdapterServerListener listener : this.listeners) {
            try {
                listener.handleChanged(event);
            } catch (Exception e) {
                LOGGER.error("Error while updaing user defined debug adapter descriptor factory with id='" + event.serverDefinition.getId() + "'", e);
            }
        }
    }

    @Override
    public boolean isDebuggableFile(@NotNull VirtualFile file,
                                    @NotNull Project project) {
        // Search canDebug inside the factories
        if (findDebugAdapterServerFor(file, project) != null) {
            return true;
        }

        // Search canDebug in existing DAP run configuration
        return findExistingConfigurationFor(file, project, false) != null;
    }

    /**
     * Returns the existing run configuration for the given file and null otherwise.
     *
     * @param file    the file.
     * @param project the project.
     * @return the existing run configuration  for the given file and null otherwise.
     */
    private static RunConfiguration findExistingConfigurationFor(@NotNull VirtualFile file,
                                                                 @NotNull Project project,
                                                                 boolean checkFile) {
        List<RunConfiguration> all = RunManager.getInstance(project).getAllConfigurationsList();
        for (var runConfiguration : all) {
            if (runConfiguration instanceof DAPRunConfiguration dapConfig) {
                if (dapConfig.isDebuggableFile(file, project)) {
                    if (checkFile) {
                        String existingFile = ((DAPRunConfiguration) runConfiguration).getFile();
                        if (!getFilePath(file).equals(existingFile)) {
                            return null;
                        }
                    }
                    return runConfiguration;
                }
            }
        }
        return null;
    }

    /**
     * Returns the DAP descriptor factory for the given file and null otherwise.
     *
     * @param file    the file.
     * @param project the project.
     * @return the DAP descriptor factory for the given file and null otherwise.
     */
    @Nullable
    private DebugAdapterServerDefinition findDebugAdapterServerFor(@NotNull VirtualFile file,
                                                                   @NotNull Project project) {
        for (var serverDefinition : serverDefinitions.values()) {
            var factory = serverDefinition.getFactory();
            if (factory.isDebuggableFile(file, project)) {
                return serverDefinition;
            }
        }
        return null;
    }

    public boolean prepareConfiguration(@NotNull RunConfiguration configuration,
                                        @NotNull VirtualFile file,
                                        @NotNull Project project) {
        RunConfiguration existingConfiguration = findExistingConfigurationFor(file, project, true);
        if (existingConfiguration != null
                && existingConfiguration instanceof DAPRunConfiguration existingDapConfiguration
                && configuration instanceof DAPRunConfiguration dapConfiguration) {
            existingDapConfiguration.copyTo(dapConfiguration);
            return true;
        }
        DebugAdapterServerDefinition serverDefinition = findDebugAdapterServerFor(file, project);
        if (serverDefinition != null) {
            return serverDefinition
                    .getFactory()
                    .prepareConfiguration(configuration, file, project);
        }
        return false;
    }

    private void initialize() {
        // Load Debug Adapter descriptor factory from extension point
        loadFromExtensionPoint();

        // Load Debug Adapter descriptor factory from settings
        loadFromSettings();
    }

    private void loadFromExtensionPoint() {
        Map<String /* debug adapter server id */, List<ServerMapping>> mappings = new HashMap<>();

        // Load language mappings from extensions point
        for (LanguageMappingExtensionPointBean extension : LanguageMappingExtensionPointBean.EP_NAME.getExtensions()) {
            Language language = Language.findLanguageByID(extension.language);
            if (language != null) {
                String serverId = extension.serverId;
                List<ServerMapping> mappingsForServer = mappings.computeIfAbsent(serverId, k -> new ArrayList<>());
                @Nullable String languageId = extension.languageId;
                mappingsForServer.add(new ServerLanguageMapping(language, serverId, languageId, extension.getDocumentMatcher()));
            }
        }

        // Load fileType mappings from extensions point
        for (FileTypeMappingExtensionPointBean extension : FileTypeMappingExtensionPointBean.EP_NAME.getExtensions()) {
            FileType fileType = FileTypeManager.getInstance().findFileTypeByName(extension.fileType);
            if (fileType != null) {
                String serverId = extension.serverId;
                List<ServerMapping> mappingsForServer = mappings.computeIfAbsent(serverId, k -> new ArrayList<>());
                @Nullable String languageId = extension.languageId;
                mappingsForServer.add(new ServerFileTypeMapping(fileType, extension.serverId, languageId, extension.getDocumentMatcher()));
            }
        }

        // Load file name patterns mappings from extensions point
        for (FileNamePatternMappingExtensionPointBean extension : FileNamePatternMappingExtensionPointBean.EP_NAME.getExtensions()) {
            String serverId = extension.serverId;
            List<ServerMapping> mappingsForServer = mappings.computeIfAbsent(serverId, k -> new ArrayList<>());
            @Nullable String languageId = extension.languageId;
            List<String> patterns = Arrays.asList(extension.patterns.split(";"));
            mappingsForServer.add(new ServerFileNamePatternMapping(patterns, extension.serverId, languageId, extension.getDocumentMatcher()));
        }

        // Load debug adapter servers from extensions point
        for (var server : DebugAdapterServerExtensionPointBean.EP_NAME.getExtensions()) {
            String serverId = server.getId();
            if (serverId != null && !serverId.isEmpty()) {
                List<ServerMapping> mappingsForServer = mappings.get(serverId);
                mappings.remove(serverId);
                var serverDefinition = new ExtensionDebugAdapterServerDefinition(server, mappingsForServer != null ? mappingsForServer : Collections.emptyList());
                addDebugAdapterServerWithoutNotification(serverDefinition);
            }
        }
    }

    private void loadFromSettings() {
        try {
            for (var setting : UserDefinedDebugAdapterServerSettings.getInstance().getSettings()) {
                String debugAdapterServerId = setting.getServerId();
                List<ServerMappingSettings> languageMappings = new ArrayList<>();
                List<ServerMappingSettings> fileTypeMappings = new ArrayList<>();
                var mappingSettings = setting.getMappings();
                if (mappingSettings != null && !mappingSettings.isEmpty()) {
                    for (var mapping : mappingSettings) {
                        String mappingLanguage = mapping.getLanguage();
                        if (!StringUtils.isEmpty(mappingLanguage)) {
                            Language language = Language.findLanguageByID(mappingLanguage);
                            if (language != null) {
                                languageMappings.add(mapping);
                            }
                        } else {
                            boolean fileTypeMappingCreated = false;
                            String mappingFileType = mapping.getFileType();
                            if (!StringUtils.isEmpty(mappingFileType)) {
                                FileType fileType = FileTypeManager.getInstance().findFileTypeByName(mappingFileType);
                                if (fileType != null) {
                                    // Register file type mapping from settings
                                    fileTypeMappings.add(mapping);
                                    fileTypeMappingCreated = true;
                                }
                            }
                            if (!fileTypeMappingCreated) {
                                List<String> patterns = mapping.getFileNamePatterns();
                                if (patterns != null) {
                                    // Register file name patterns mapping from settings
                                    fileTypeMappings.add(mapping);
                                }
                            }
                        }
                    }
                }

                // Register Debug Adapter server from settings
                var serverDefinition = new UserDefinedDebugAdapterServerDefinition(debugAdapterServerId,
                        setting.getServerName(),
                        setting.getCommandLine(),
                        languageMappings,
                        fileTypeMappings);
                serverDefinition.setUserEnvironmentVariables(setting.getUserEnvironmentVariables());
                serverDefinition.setIncludeSystemEnvironmentVariables(setting.isIncludeSystemEnvironmentVariables());
                serverDefinition.setConnectTimeout(setting.getConnectTimeout());
                serverDefinition.setDebugServerReadyPattern(setting.getDebugServerReadyPattern());
                serverDefinition.setLaunchConfigurations(setting.getLaunchConfigurations());
                serverDefinition.setAttachAddress(setting.getAttachAddress());
                serverDefinition.setAttachPort(setting.getAttachPort());
                addDebugAdapterServerWithoutNotification(serverDefinition);
            }
        } catch (Exception e) {
            LOGGER.error("Error while loading user defined debug adapter server from settings", e);
        }
    }

}

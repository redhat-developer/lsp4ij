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
package com.redhat.devtools.lsp4ij.dap.descriptors;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.dap.LaunchConfiguration;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import com.redhat.devtools.lsp4ij.dap.descriptors.userdefined.UserDefinedDebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.dap.settings.UserDefinedDebugAdapterDescriptorFactorySettings;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static com.redhat.devtools.lsp4ij.dap.DAPIJUtils.getFilePath;

/**
 * Debug Adapter Protocol (DAP) manager.
 */
public class DebugAdapterManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugAdapterManager.class);

    public static DebugAdapterManager getInstance() {
        return ApplicationManager.getApplication().getService(DebugAdapterManager.class);
    }

    private final Collection<DebugAdapterDescriptorFactoryListener> listeners = new CopyOnWriteArrayList<>();

    private final Map<String, DebugAdapterDescriptorFactory> factories = new HashMap<>();

    public DebugAdapterManager() {
        initialize();
    }

    public void addDebugAdapterDescriptorFactoryListener(@NotNull DebugAdapterDescriptorFactoryListener listener) {
        listeners.add(listener);
    }

    public void removeDebugAdapterDescriptorFactoryListener(@NotNull DebugAdapterDescriptorFactoryListener listener) {
        listeners.remove(listener);
    }

    public void addDebugAdapterDescriptorFactory(@NotNull DebugAdapterDescriptorFactory factory) {
        addDebugAdapterDescriptorFactoryWithoutNotification(factory);
        // Notifications
        var event = new DebugAdapterDescriptorFactoryListener.DebugAdapterDescriptorFactoryAddedEvent(Collections.singleton(factory));
        for (var listener : this.listeners) {
            try {
                listener.handleAdded(event);
            } catch (Exception e) {
                LOGGER.error("Error while adding Debug Adapter Descriptor Factory with id='" + factory.getId() + "'", e);
            }
        }
    }

    public void addDebugAdapterDescriptorFactoryWithoutNotification(@NotNull DebugAdapterDescriptorFactory factory) {
        String factoryId = factory.getId();
        factories.put(factoryId, factory);
        if (factory instanceof UserDefinedDebugAdapterDescriptorFactory userDefinedFactory) {
            // Update settings
            UserDefinedDebugAdapterDescriptorFactorySettings.ItemSettings settings = new UserDefinedDebugAdapterDescriptorFactorySettings.ItemSettings();
            settings.setServerId(userDefinedFactory.getId());
            settings.setServerName(userDefinedFactory.getDisplayName());
            settings.setUserEnvironmentVariables(userDefinedFactory.getUserEnvironmentVariables());
            settings.setIncludeSystemEnvironmentVariables(userDefinedFactory.isIncludeSystemEnvironmentVariables());
            settings.setCommandLine(userDefinedFactory.getCommandLine());
            settings.setConnectTimeout(userDefinedFactory.getConnectTimeout());
            settings.setDebugServerReadyPattern(userDefinedFactory.getDebugServerReadyPattern());
            List<ServerMappingSettings> mappings = Stream.concat(userDefinedFactory.getLanguageMappings().stream(),
                            userDefinedFactory.getFileTypeMappings().stream())
                    .toList();
            settings.setMappings(mappings);
            settings.setLaunchConfigurations(userDefinedFactory.getLaunchConfigurations());
            UserDefinedDebugAdapterDescriptorFactorySettings.getInstance().setSettings(factoryId, settings);
        }
    }

    public void removeDebugAdapterDescriptorFactory(@NotNull DebugAdapterDescriptorFactory factory) {
        String factoryId = factory.getId();
        factories.remove(factoryId);
        // Update settings
        UserDefinedDebugAdapterDescriptorFactorySettings.getInstance().removeSettings(factoryId);
        // Notifications
        var event = new DebugAdapterDescriptorFactoryListener.DebugAdapterDescriptorFactoryRemovedEvent(Collections.singleton(factory));
        for (var listener : this.listeners) {
            try {
                listener.handleRemoved(event);
            } catch (Exception e) {
                LOGGER.error("Error while DAP server descriptor is added with id='" + factory.getId() + "'", e);
            }
        }
    }

    @Nullable
    public DebugAdapterDescriptorFactory getFactoryById(@NotNull String factoryId) {
        return factories.get(factoryId);
    }

    public Collection<DebugAdapterDescriptorFactory> getFactories() {
        return Collections.unmodifiableCollection(factories.values());
    }

    public record UpdateDebugAdapterDescriptorFactoryRequest(
            @NotNull UserDefinedDebugAdapterDescriptorFactory descriptorFactory,
            @Nullable String name,
            @Nullable Map<String, String> userEnvironmentVariables,
            boolean includeSystemEnvironmentVariables,
            @Nullable String commandLine,
            int connectTimeout,
            @Nullable String debugServerReadyPattern,
            @NotNull List<ServerMappingSettings> languageMappings,
            @NotNull List<ServerMappingSettings> fileTypeMappings,
            @Nullable List<LaunchConfiguration> launchConfigurations) {
    }

    @Nullable
    public DebugAdapterDescriptorFactoryListener.DebugAdapterDescriptorFactoryChangedEvent updateDescriptorFactory(@NotNull DebugAdapterManager.UpdateDebugAdapterDescriptorFactoryRequest request,
                                                                                                                   boolean notify) {
        var descriptorFactory = request.descriptorFactory();
        String descriptorFactoryId = request.descriptorFactory().getId();
        descriptorFactory.setName(request.name());
        descriptorFactory.setCommandLine(request.commandLine());
        descriptorFactory.setConnectTimeout(request.connectTimeout());
        descriptorFactory.setDebugServerReadyPattern(request.debugServerReadyPattern());
        descriptorFactory.setUserEnvironmentVariables(request.userEnvironmentVariables());
        descriptorFactory.setIncludeSystemEnvironmentVariables(request.includeSystemEnvironmentVariables());

        descriptorFactory.setLanguageMappings(request.languageMappings());
        descriptorFactory.setFileTypeMappings(request.fileTypeMappings());

        descriptorFactory.setLaunchConfigurations(request.launchConfigurations());

        List<ServerMappingSettings> mappings = Stream.concat(request.languageMappings().stream(), request.fileTypeMappings().stream()).toList();
        UserDefinedDebugAdapterDescriptorFactorySettings.ItemSettings settings = UserDefinedDebugAdapterDescriptorFactorySettings.getInstance().getSettings(descriptorFactoryId);
        boolean nameChanged = !Objects.equals(settings.getServerName(), request.name());
        boolean userEnvironmentVariablesChanged = !Objects.equals(settings.getUserEnvironmentVariables(), request.userEnvironmentVariables());
        boolean includeSystemEnvironmentVariablesChanged = settings.isIncludeSystemEnvironmentVariables() != request.includeSystemEnvironmentVariables();
        boolean commandChanged = !Objects.equals(settings.getCommandLine(), request.commandLine());
        boolean connectTimeoutChanged = !Objects.equals(settings.getConnectTimeout(), request.connectTimeout());
        boolean debugServerReadyPatternChanged = !Objects.equals(settings.getDebugServerReadyPattern(), request.debugServerReadyPattern());
        boolean mappingsChanged = !Objects.deepEquals(settings.getMappings(), mappings);
        boolean launchConfigurationChanged = !Objects.equals(settings.getLaunchConfigurations(), request.launchConfigurations());
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
                mappingsChanged || launchConfigurationChanged) {
            // Notifications
            DebugAdapterDescriptorFactoryListener.DebugAdapterDescriptorFactoryChangedEvent event = new DebugAdapterDescriptorFactoryListener.DebugAdapterDescriptorFactoryChangedEvent(
                    descriptorFactory,
                    nameChanged,
                    commandChanged,
                    userEnvironmentVariablesChanged,
                    includeSystemEnvironmentVariablesChanged,
                    connectTimeoutChanged,
                    debugServerReadyPatternChanged,
                    mappingsChanged,
                    launchConfigurationChanged);
            if (notify) {
                handleChangeEvent(event);
            }
            return event;
        }
        return null;
    }

    public void handleChangeEvent(DebugAdapterDescriptorFactoryListener.DebugAdapterDescriptorFactoryChangedEvent event) {
        for (DebugAdapterDescriptorFactoryListener listener : this.listeners) {
            try {
                listener.handleChanged(event);
            } catch (Exception e) {
                LOGGER.error("Error while updaing user defined debug adapter descriptor factory with id='" + event.descriptorFactory.getId() + "'", e);
            }
        }
    }

    public boolean canDebug(@NotNull VirtualFile file,
                            @NotNull Project project) {
        // Search canDebug inside the factories
        if (findFactoryFor(file, project) != null) {
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
                if (dapConfig.canDebug(file)) {
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
    private DebugAdapterDescriptorFactory findFactoryFor(@NotNull VirtualFile file,
                                                         @NotNull Project project) {
        for (var factory : factories.values()) {
            if (factory.canDebug(file, project)) {
                return factory;
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
        DebugAdapterDescriptorFactory factory = findFactoryFor(file, project);
        if (factory != null) {
            return factory.prepareConfiguration(configuration, file, project);
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
        // TODO
    }

    private void loadFromSettings() {
        try {
            for (var setting : UserDefinedDebugAdapterDescriptorFactorySettings.getInstance().getSettings()) {
                String factoryId = setting.getServerId();
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

                // Register Debug Adapter descriptor factory from settings
                var factory = new UserDefinedDebugAdapterDescriptorFactory(factoryId,
                        setting.getServerName(),
                        setting.getCommandLine(),
                        languageMappings,
                        fileTypeMappings);
                factory.setUserEnvironmentVariables(setting.getUserEnvironmentVariables());
                factory.setIncludeSystemEnvironmentVariables(setting.isIncludeSystemEnvironmentVariables());
                factory.setConnectTimeout(setting.getConnectTimeout());
                factory.setDebugServerReadyPattern(setting.getDebugServerReadyPattern());
                factory.setLaunchConfigurations(setting.getLaunchConfigurations());
                addDebugAdapterDescriptorFactoryWithoutNotification(factory);
            }
        } catch (Exception e) {
            LOGGER.error("Error while loading user defined debug adapter descriptor factory from settings", e);
        }
    }

}

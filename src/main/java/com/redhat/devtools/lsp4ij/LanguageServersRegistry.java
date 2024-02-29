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

import com.intellij.codeInsight.hints.NoSettings;
import com.intellij.codeInsight.hints.ProviderInfo;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.internal.SimpleLanguageUtils;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.operations.color.LSPColorProvider;
import com.redhat.devtools.lsp4ij.operations.inlayhint.LSPInlayHintsProvider;
import com.redhat.devtools.lsp4ij.server.definition.*;
import com.redhat.devtools.lsp4ij.server.definition.extension.*;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static com.redhat.devtools.lsp4ij.server.definition.extension.LanguageMappingExtensionPointBean.DEFAULT_DOCUMENT_MATCHER;

/**
 * Language servers registry.
 */
public class LanguageServersRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServersRegistry.class);

    public static LanguageServersRegistry getInstance() {
        return ApplicationManager.getApplication().getService(LanguageServersRegistry.class);
    }

    private final Map<String, LanguageServerDefinition> serverDefinitions = new HashMap<>();

    private final List<LanguageServerFileAssociation> fileAssociations = new ArrayList<>();

    private final Collection<LanguageServerDefinitionListener> listeners = new CopyOnWriteArrayList<>();

    private final List<ProviderInfo<? extends Object>> inlayHintsProviders = new ArrayList<>();

    private LanguageServersRegistry() {
        initialize();
    }

    private void initialize() {


        // Load language servers / mappings from user extension point
        loadServersAndMappingsFromExtensionPoint();
        // Load language servers / mappings from user settings
        loadServersAndMappingFromSettings();

        updateInlayHintsProviders();
    }

    private void loadServersAndMappingsFromExtensionPoint() {
        Map<String /* server id */, List<ServerMapping>> mappings = new HashMap<>();

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

        // Load language servers from extensions point
        for (ServerExtensionPointBean server : ServerExtensionPointBean.EP_NAME.getExtensions()) {
            String serverId = server.id;
            if (serverId != null && !serverId.isEmpty()) {
                List<ServerMapping> mappingsForServer = mappings.get(serverId);
                mappings.remove(serverId);
                addServerDefinitionWithoutNotification(new ExtensionLanguageServerDefinition(server), mappingsForServer);
            }
        }

        for (List<ServerMapping> mappingsPerServer : mappings.values()) {
            for (ServerMapping mapping : mappingsPerServer) {
                LOGGER.warn(getServerNotAvailableMessage(mapping));
            }
        }
    }

    private void loadServersAndMappingFromSettings() {
        try {
            for (var launch : UserDefinedLanguageServerSettings.getInstance().getUserDefinedLanguageServerSettings()) {
                String serverId = launch.getServerId();
                List<ServerMapping> mappings = toServerMappings(serverId, launch.getMappings());
                // Register server definition from settings
                addServerDefinitionWithoutNotification(new UserDefinedLanguageServerDefinition(
                        serverId,
                        launch.getServerName(),
                        "",
                        launch.getCommandLine(),
                        launch.getConfigurationContent(),
                        launch.getInitializationOptionsContent()),
                        mappings);
            }
        } catch (Exception e) {
            LOGGER.error("Error while loading user defined language servers from settings", e);
        }
    }

    private void updateInlayHintsProviders() {
        // register LSPInlayHintInlayHintsProvider + LSPCodelensInlayHintsProvider automatically for all languages
        // which are associated with a language server.
        Set<Language> distinctLanguages = fileAssociations
                .stream()
                .map(LanguageServerFileAssociation::getLanguage)
                .filter(language -> language != null)
                .collect(Collectors.toSet());
        // When a file is not linked to a language
        //  - if the file is associated to a textmate to support syntax coloration
        //  - otherwise if the file is associated (by default) to plain/text file type
        // the language received in InlayHintProviders
        // is "textmate" / "TEXT" language, we add them to support
        // LSP codeLens, inlayHint, color for a file which is not linked to a language.
        for (var simpleLanguage : SimpleLanguageUtils.getSupportedSimpleLanguages()) {
            distinctLanguages.add(simpleLanguage);
        }
        // When a file is not linked to a language (just with a file type) and not linked to a textmate,
        // the language received in InlayHintProviders is plain/text, we add it to support
        // LSP inlayHint, color for a file which is not linked to a language.
        distinctLanguages.add(PlainTextLanguage.INSTANCE);
        LSPInlayHintsProvider lspInlayHintsProvider = new LSPInlayHintsProvider();
        LSPColorProvider lspColorProvider = new LSPColorProvider();
        inlayHintsProviders.clear();
        for (Language language : distinctLanguages) {
            inlayHintsProviders.add(new ProviderInfo<NoSettings>(language, lspInlayHintsProvider));
            inlayHintsProviders.add(new ProviderInfo<NoSettings>(language, lspColorProvider));
        }
    }

    private static String getServerNotAvailableMessage(ServerMapping mapping) {
        StringBuilder message = new StringBuilder("server '");
        message.append(mapping.getServerId());
        message.append("' for mapping IntelliJ ");
        if (mapping instanceof ServerLanguageMapping languageMapping) {
            message.append("language '");
            message.append(languageMapping.getLanguage());
            message.append("'");
        } else if (mapping instanceof ServerFileTypeMapping fileTypeMapping) {
            message.append("file type '");
            message.append(fileTypeMapping.getFileType());
            message.append("'");
        } else if (mapping instanceof ServerFileNamePatternMapping fileNamePatternMapping) {
            message.append("file name pattern '");
            message.append(fileNamePatternMapping.getFileNamePatterns()
                    .stream()
                    .collect(Collectors.joining(",")));
            message.append("'");
        }
        message.append(" not available");
        return message.toString();
    }

    /**
     * @param language the language
     * @param fileType the file type.
     * @param file
     * @return the {@link LanguageServerDefinition}s <strong>directly</strong> associated to the given content-type.
     * This does <strong>not</strong> include the one that match transitively as per content-type hierarchy
     */
    List<LanguageServerFileAssociation> findLanguageServerDefinitionFor(final @Nullable Language language, @Nullable FileType fileType, @NotNull VirtualFile file) {
        return fileAssociations.stream()
                .filter(mapping -> mapping.match(language, fileType, file.getName()))
                .collect(Collectors.toList());
    }

    public List<LanguageServerFileAssociation> findLanguageServerDefinitionFor(final @NotNull String serverId) {
        return fileAssociations.stream()
                .filter(mapping -> serverId.equals(mapping.getServerDefinition().id))
                .collect(Collectors.toList());
    }

    public void registerAssociation(@NotNull LanguageServerDefinition serverDefinition, @NotNull ServerMapping mapping) {
        if (mapping instanceof ServerLanguageMapping languageMapping) {
            @NotNull Language language = languageMapping.getLanguage();
            @Nullable String languageId = mapping.getLanguageId();
            if (!StringUtils.isEmpty(languageId)) {
                serverDefinition.registerAssociation(language, languageId);
            }
            fileAssociations.add(new LanguageServerFileAssociation(language, serverDefinition, mapping.getDocumentMatcher(), languageId));
        } else if (mapping instanceof ServerFileTypeMapping fileTypeMapping) {
            @NotNull FileType fileType = fileTypeMapping.getFileType();
            @Nullable String languageId = mapping.getLanguageId();
            if (!StringUtils.isEmpty(languageId)) {
                serverDefinition.registerAssociation(fileType, languageId);
            }
            fileAssociations.add(new LanguageServerFileAssociation(fileType, serverDefinition, mapping.getDocumentMatcher(), languageId));
        } else if (mapping instanceof ServerFileNamePatternMapping fileNamePatternMapping) {
            List<FileNameMatcher> matchers = fileNamePatternMapping.getFileNameMatchers();
            @Nullable String languageId = mapping.getLanguageId();
            if (!StringUtils.isEmpty(languageId)) {
                serverDefinition.registerAssociation(matchers, languageId);
            }
            fileAssociations.add(new LanguageServerFileAssociation(matchers, serverDefinition, mapping.getDocumentMatcher(), languageId));
        }
    }

    /**
     * Returns the language server definition for the given language server id and null otherwise.
     *
     * @param languageServerId the language server id.
     * @return the language server definition for the given language server id and null otherwise.
     */
    public @Nullable LanguageServerDefinition getServerDefinition(@NotNull String languageServerId) {
        return serverDefinitions.get(languageServerId);
    }

    /**
     * Returns the registered server definitions.
     *
     * @return the registered server definitions.
     */
    public Collection<LanguageServerDefinition> getServerDefinitions() {
        return serverDefinitions.values();
    }

    public void addServerDefinition(@NotNull LanguageServerDefinition definition, @Nullable List<ServerMappingSettings> mappings) {
        addServerDefinitionWithoutNotification(definition, toServerMappings(definition.id, mappings));
        updateInlayHintsProviders();
        LanguageServerDefinitionListener.LanguageServerAddedEvent event = new LanguageServerDefinitionListener.LanguageServerAddedEvent(Collections.singleton(definition));
        for (LanguageServerDefinitionListener listener : this.listeners) {
            try {
                listener.handleAdded(event);
            } catch (Exception e) {
                LOGGER.error("Error while server definition is added of the language server '" + definition.id + "'", e);
            }
        }
    }


    private void addServerDefinitionWithoutNotification(@NotNull LanguageServerDefinition definition, @NotNull List<ServerMapping> mappings) {
        serverDefinitions.put(definition.id, definition);
        // Update associations
        updateAssociations(definition, mappings);
        // Update settings
        if (definition instanceof UserDefinedLanguageServerDefinition definitionFromSettings) {
            UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings = new UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings();
            settings.setServerId(definitionFromSettings.id);
            settings.setServerName(definitionFromSettings.getDisplayName());
            settings.setCommandLine(definitionFromSettings.getCommandLine());
            if (mappings != null) {
                settings.setMappings(toServerMappingSettings(mappings));
            }
            settings.setConfigurationContent(definitionFromSettings.getConfigurationContent());
            settings.setInitializationOptionsContent(definitionFromSettings.getInitializationOptionsContent());
            UserDefinedLanguageServerSettings.getInstance().setLaunchConfigSettings(definition.id, settings);
        }
    }

    private void updateAssociations(@NotNull LanguageServerDefinition definition, @NotNull List<ServerMapping> mappings) {
        if (mappings != null) {
            for (ServerMapping mapping : mappings) {
                registerAssociation(definition, mapping);
            }
        }
    }

    @NotNull
    private static List<ServerMappingSettings> toServerMappingSettings(@NotNull List<ServerMapping> mappings) {
        return mappings
                .stream()
                .map(mapping -> {
                    if (mapping instanceof ServerLanguageMapping languageMapping) {
                        return ServerMappingSettings.createLanguageMappingSettings(languageMapping.getLanguage().getID(), languageMapping.getLanguageId());
                    } else if (mapping instanceof ServerFileTypeMapping fileTypeMapping) {
                        return ServerMappingSettings.createFileTypeMappingSettings(fileTypeMapping.getFileType().getName(), fileTypeMapping.getLanguageId());
                    } else if (mapping instanceof ServerFileNamePatternMapping fileNamePatternMapping) {
                        return ServerMappingSettings.createFileNamePatternsMappingSettings(fileNamePatternMapping.getFileNamePatterns(), fileNamePatternMapping.getLanguageId());
                    }
                    // should never occur
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static List<ServerMapping> toServerMappings(String serverId, List<ServerMappingSettings> mappingSettings) {
        List<ServerMapping> mappings = new ArrayList<>();
        if (!mappingSettings.isEmpty()) {
            for (var mapping : mappingSettings) {
                String languageId = mapping.getLanguageId();
                String mappingLanguage = mapping.getLanguage();
                if (!StringUtils.isEmpty(mappingLanguage)) {
                    Language language = Language.findLanguageByID(mappingLanguage);
                    if (language != null) {
                        mappings.add(new ServerLanguageMapping(language, serverId, languageId, DEFAULT_DOCUMENT_MATCHER));
                    }
                } else {
                    boolean fileTypeMappingCreated = false;
                    String mappingFileType = mapping.getFileType();
                    if (!StringUtils.isEmpty(mappingFileType)) {
                        FileType fileType = FileTypeManager.getInstance().findFileTypeByName(mappingFileType);
                        if (fileType != null) {
                            // Register file type mapping from settings
                            mappings.add(new ServerFileTypeMapping(fileType, serverId, languageId, DEFAULT_DOCUMENT_MATCHER));
                            fileTypeMappingCreated = true;
                        }
                    }
                    if (!fileTypeMappingCreated) {
                        List<String> patterns = mapping.getFileNamePatterns();
                        if (patterns != null) {
                            // Register file name patterns mapping from settings
                            mappings.add(new ServerFileNamePatternMapping(patterns, serverId, languageId, DEFAULT_DOCUMENT_MATCHER));
                        }
                    }
                }
            }
        }
        return mappings;
    }

    public void removeServerDefinition(LanguageServerDefinition definition) {
        // remove server
        serverDefinitions.remove(definition.id);
        // remove associations
        removeAssociationsFor(definition);

        updateInlayHintsProviders();
        // Update settings
        UserDefinedLanguageServerSettings.getInstance().removeServerDefinition(definition.id);
        // Notifications
        LanguageServerDefinitionListener.LanguageServerRemovedEvent event = new LanguageServerDefinitionListener.LanguageServerRemovedEvent(Collections.singleton(definition));
        for (LanguageServerDefinitionListener listener : this.listeners) {
            try {
                listener.handleRemoved(event);
            } catch (Exception e) {
                LOGGER.error("Error while server definition is removed of the language server '" + definition.id + "'", e);
            }
        }
    }

    private void removeAssociationsFor(LanguageServerDefinition definition) {
        List<LanguageServerFileAssociation> mappingsToRemove = fileAssociations
                .stream()
                .filter(mapping -> definition.equals(mapping.getServerDefinition()))
                .collect(Collectors.toList());
        fileAssociations.removeAll(mappingsToRemove);
    }


    public void updateServerDefinition(@NotNull UserDefinedLanguageServerDefinition definition,
                                       @Nullable String name,
                                       @Nullable String commandLine,
                                       @NotNull List<ServerMappingSettings> mappings,
                                       @Nullable String configurationContent,
                                       @Nullable String initializationOptionsContent) {
        definition.setName(name);
        definition.setCommandLine(commandLine);
        definition.setConfigurationContent(configurationContent);
        definition.setInitializationOptionsContent(initializationOptionsContent);

        // remove associations
        removeAssociationsFor(definition);
        // Update associations
        updateAssociations(definition, toServerMappings(definition.id, mappings));

        UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings = UserDefinedLanguageServerSettings.getInstance().getLaunchConfigSettings(definition.id);
        boolean nameChanged = !Objects.equals(settings.getServerName(), name);
        boolean commandChanged = !Objects.equals(settings.getCommandLine(), commandLine);
        boolean mappingsChanged = !Objects.deepEquals(settings.getMappings(), mappings);
        boolean configurationContentChanged = !Objects.equals(settings.getConfigurationContent(), configurationContent);
        boolean initializationOptionsContentChanged = !Objects.equals(settings.getInitializationOptionsContent(), initializationOptionsContent);

        settings.setServerName(name);
        settings.setCommandLine(commandLine);
        settings.setConfigurationContent(configurationContent);
        settings.setInitializationOptionsContent(initializationOptionsContent);
        settings.setMappings(mappings);

        if (nameChanged || commandChanged || mappingsChanged || configurationContentChanged || initializationOptionsContentChanged) {
            // Notifications
            LanguageServerDefinitionListener.LanguageServerChangedEvent event = new LanguageServerDefinitionListener.LanguageServerChangedEvent(definition, nameChanged, commandChanged, mappingsChanged, configurationContentChanged, initializationOptionsContentChanged);
            for (LanguageServerDefinitionListener listener : this.listeners) {
                try {
                    listener.handleChanged(event);
                } catch (Exception e) {
                    LOGGER.error("Error while server definition has changed of the language server '" + definition.id + "'", e);
                }
            }
        }
    }

    public void addLanguageServerDefinitionListener(LanguageServerDefinitionListener listener) {
        this.listeners.add(listener);
    }

    public void removeLanguageServerDefinitionListener(LanguageServerDefinitionListener listener) {
        this.listeners.remove(listener);
    }

    /**
     * Returns true if the language of the file is supported by a language server and false otherwise.
     *
     * @param file the file.
     * @return true if the language of the file is supported by a language server and false otherwise.
     */
    public boolean isFileSupported(@Nullable PsiFile file) {
        if (file == null) {
            return false;
        }
        return isFileSupported(file.getVirtualFile(), file.getProject());
    }

    /**
     * Returns true if the language of the file is supported by a language server and false otherwise.
     *
     * @param file    the file.
     * @param project the project.
     * @return true if the language of the file is supported by a language server and false otherwise.
     */
    public boolean isFileSupported(@Nullable VirtualFile file, @NotNull Project project) {
        if (file == null) {
            return false;
        }
        Language language = LSPIJUtils.getFileLanguage(file, project);
        FileType fileType = file.getFileType();
        return fileAssociations
                .stream()
                .anyMatch(mapping -> mapping.match(language, fileType, file.getName()));
    }

    /**
     * Returns the LSP codeLens / inlayHint inlay hint providers for all languages which are associated with a language server.
     *
     * @return the LSP codeLens / inlayHint inlay hint providers for all languages which are associated with a language server.
     */
    public List<ProviderInfo<? extends Object>> getInlayHintProviderInfos() {
        return inlayHintsProviders;
    }

}
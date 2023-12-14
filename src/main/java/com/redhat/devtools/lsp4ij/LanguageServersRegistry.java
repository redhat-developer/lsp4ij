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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.operations.codelens.LSPCodelensProvider;
import com.redhat.devtools.lsp4ij.operations.color.LSPColorProvider;
import com.redhat.devtools.lsp4ij.operations.inlayhint.LSPInlayHintsProvider;
import com.redhat.devtools.lsp4ij.server.definition.*;
import com.redhat.devtools.lsp4ij.server.definition.extension.ExtensionLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.extension.FileTypeMappingExtensionPointBean;
import com.redhat.devtools.lsp4ij.server.definition.extension.LanguageMappingExtensionPointBean;
import com.redhat.devtools.lsp4ij.server.definition.extension.ServerExtensionPointBean;
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

    private final List<ContentTypeToLanguageServerDefinition> connections = new ArrayList<>();

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
                List<ServerMapping> mappingsForServer = mappings.get(serverId);
                if (mappingsForServer == null) {
                    mappingsForServer = new ArrayList<>();
                    mappings.put(serverId, mappingsForServer);
                }
                @NotNull String languageId = StringUtils.isEmpty(extension.languageId) ? language.getID() : extension.languageId;
                mappingsForServer.add(new ServerLanguageMapping(language, serverId, languageId, extension.getDocumentMatcher()));
            }
        }

        // Load fileType mappings from extensions point
        for (FileTypeMappingExtensionPointBean extension : FileTypeMappingExtensionPointBean.EP_NAME.getExtensions()) {
            FileType fileType = FileTypeManager.getInstance().findFileTypeByName(extension.fileType);
            if (fileType != null) {
                String serverId = extension.serverId;
                List<ServerMapping> mappingsForServer = mappings.get(serverId);
                if (mappingsForServer == null) {
                    mappingsForServer = new ArrayList<>();
                    mappings.put(serverId, mappingsForServer);
                }
                @NotNull String languageId = StringUtils.isEmpty(extension.languageId) ? fileType.getName() : extension.languageId;
                mappingsForServer.add(new ServerFileTypeMapping(fileType, extension.serverId, languageId, extension.getDocumentMatcher()));
            }
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
        for (var launch : UserDefinedLanguageServerSettings.getInstance().getUserDefinedLanguageServerSettings()) {
            String serverId = launch.getServerId();

            List<ServerMapping> mappings = new ArrayList<>();
            // Collect mappings
            for (var mapping : launch.getMappings()) {
                if (mapping != null) {
                    String mappingLanguage = mapping.getLanguage();
                    if (!StringUtils.isEmpty(mappingLanguage)) {
                        Language language = Language.findLanguageByID(mappingLanguage);
                        if (language != null) {
                            // Register language mapping from settings
                            @NotNull String languageId = StringUtils.isEmpty(mapping.getLanguageId()) ? language.getID() : mapping.getLanguageId();
                            mappings.add(new ServerLanguageMapping(language, serverId, languageId, DEFAULT_DOCUMENT_MATCHER));
                        }
                    } else {
                        String mappingFileType = mapping.getFileType();
                        if (!StringUtils.isEmpty(mappingFileType)) {
                            FileType fileType = FileTypeManager.getInstance().findFileTypeByName(mappingFileType);
                            if (fileType != null) {
                                // Register file type mapping from settings
                                @NotNull String languageId = StringUtils.isEmpty(mapping.getLanguageId()) ? fileType.getName() : mapping.getLanguageId();
                                mappings.add(new ServerFileTypeMapping(fileType, serverId, languageId, DEFAULT_DOCUMENT_MATCHER));
                            }
                        }
                    }
                }
            }

            // Register server definition from settings
            addServerDefinitionWithoutNotification(new UserDefinedLanguageServerDefinition(serverId, launch.getServerName(), "", launch.getCommandLine()), mappings);
        }
    }

    private void updateInlayHintsProviders() {
        // register LSPInlayHintInlayHintsProvider + LSPCodelensInlayHintsProvider automatically for all languages
        // which are associated with a language server.
        Set<Language> distinctLanguages = connections
                .stream()
                .map(ContentTypeToLanguageServerDefinition::getLanguage)
                .filter(language -> language != null)
                .collect(Collectors.toSet());
        // When a file is not linked to a language (just with a file type),
        // the language received in InlayHintProviders is plain/text, we add it to support
        // LSP codeLens, inlayHint, color for a file which is not linked to a language.
        distinctLanguages.add(PlainTextLanguage.INSTANCE);
        LSPInlayHintsProvider lspInlayHintsProvider = new LSPInlayHintsProvider();
        LSPCodelensProvider lspCodeLensProvider = new LSPCodelensProvider();
        LSPColorProvider lspColorProvider = new LSPColorProvider();
        for (Language language : distinctLanguages) {
            inlayHintsProviders.add(new ProviderInfo<NoSettings>(language, lspInlayHintsProvider));
            inlayHintsProviders.add(new ProviderInfo<NoSettings>(language, lspCodeLensProvider));
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
        }
        message.append(" not available");
        return message.toString();
    }

    /**
     * @param language the language
     * @param fileType the file type.
     * @return the {@link LanguageServerDefinition}s <strong>directly</strong> associated to the given content-type.
     * This does <strong>not</strong> include the one that match transitively as per content-type hierarchy
     */
    List<ContentTypeToLanguageServerDefinition> findLanguageServerDefinitionFor(final @Nullable Language language, @Nullable FileType fileType) {
        return connections.stream()
                .filter(mapping -> mapping.match(language, fileType))
                .collect(Collectors.toList());
    }

    public List<ContentTypeToLanguageServerDefinition> findLanguageServerDefinitionFor(final @NotNull String serverId) {
        return connections.stream()
                .filter(mapping -> serverId.equals(mapping.getServerDefinition().id))
                .collect(Collectors.toList());
    }

    public void registerAssociation(@NotNull LanguageServerDefinition serverDefinition, @NotNull ServerMapping mapping) {
        if (mapping instanceof ServerLanguageMapping languageMapping) {
            @NotNull Language language = languageMapping.getLanguage();
            @NotNull String languageId = mapping.getLanguageId();
            serverDefinition.registerAssociation(language, languageId);
            connections.add(new ContentTypeToLanguageServerDefinition(language, serverDefinition, mapping.getDocumentMatcher()));
        } else if (mapping instanceof ServerFileTypeMapping fileTypeMapping) {
            @NotNull FileType fileType = fileTypeMapping.getFileType();
            @NotNull String languageId = mapping.getLanguageId();
            serverDefinition.registerAssociation(fileType, languageId);
            connections.add(new ContentTypeToLanguageServerDefinition(fileType, serverDefinition, mapping.getDocumentMatcher()));
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
        addServerDefinitionWithoutNotification(definition, toServerMapping(definition.id, mappings));
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
                    }
                    // should never occur
                    return null;
                })
                .collect(Collectors.toList());
    }

    private static List<ServerMapping> toServerMapping(String serverId, List<ServerMappingSettings> mappingSettings) {
        List<ServerMapping> mappings = new ArrayList<>();
        if (!mappingSettings.isEmpty()) {
            for (var mappingInfo : mappingSettings) {
                String userLanguageId = mappingInfo.getLanguageId();
                String mappingLanguage = mappingInfo.getLanguage();
                if (!StringUtils.isEmpty(mappingLanguage)) {
                    Language language = Language.findLanguageByID(mappingLanguage);
                    if (language != null) {
                        @NotNull String languageId = StringUtils.isEmpty(userLanguageId) ? language.getID() : userLanguageId;
                        mappings.add(new ServerLanguageMapping(language, serverId, languageId, DEFAULT_DOCUMENT_MATCHER));
                    }
                } else {
                    String mappingFileType = mappingInfo.getFileType();
                    if (!StringUtils.isEmpty(mappingFileType)) {
                        FileType fileType = FileTypeManager.getInstance().findFileTypeByName(mappingFileType);
                        if (fileType != null) {
                            @NotNull String languageId = StringUtils.isEmpty(userLanguageId) ? fileType.getName() : userLanguageId;
                            mappings.add(new ServerFileTypeMapping(fileType, serverId, languageId, DEFAULT_DOCUMENT_MATCHER));
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
        List<ContentTypeToLanguageServerDefinition> mappingsToRemove = connections
                .stream()
                .filter(mapping -> definition.equals(mapping.getServerDefinition()))
                .collect(Collectors.toList());
        connections.removeAll(mappingsToRemove);
    }


    public void updateServerDefinition(@NotNull UserDefinedLanguageServerDefinition definition,
                                       @Nullable String name,
                                       @Nullable String commandLine,
                                       @NotNull List<ServerMappingSettings> mappings) {
        definition.setName(name);
        definition.setCommandLine(commandLine);

        // remove associations
        removeAssociationsFor(definition);
        // Update associations
        updateAssociations(definition, toServerMapping(definition.id, mappings));

        UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings = UserDefinedLanguageServerSettings.getInstance().getLaunchConfigSettings(definition.id);
        boolean nameChanged = !Objects.equals(settings.getServerName(), name);
        boolean commandChanged = !Objects.equals(settings.getCommandLine(), commandLine);
        boolean mappingsChanged = !Objects.deepEquals(settings.getMappings(), mappings);

        settings.setServerName(name);
        settings.setCommandLine(commandLine);
        settings.setMappings(mappings);

        if (nameChanged || commandChanged || mappingsChanged) {
            // Notifications
            LanguageServerDefinitionListener.LanguageServerChangedEvent event = new LanguageServerDefinitionListener.LanguageServerChangedEvent(definition, nameChanged, commandChanged, mappingsChanged);
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
        if (language == null && fileType == null) {
            return false;
        }
        return connections
                .stream()
                .anyMatch(mapping -> mapping.match(language, fileType));
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
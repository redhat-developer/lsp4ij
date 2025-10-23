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

import com.intellij.codeInsight.hints.ProviderInfo;
import com.intellij.codeInsight.hints.declarative.InlayProviderInfo;
import com.intellij.lang.Language;
import com.intellij.lang.findUsages.EmptyFindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.redhat.devtools.lsp4ij.features.color.LSPColorProvider;
import com.redhat.devtools.lsp4ij.features.inlayhint.LSPInlayHintsProvider;
import com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensColorsProvider;
import com.redhat.devtools.lsp4ij.internal.SimpleLanguageUtils;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.server.definition.*;
import com.redhat.devtools.lsp4ij.server.definition.extension.*;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.usages.LSPFindUsagesProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static com.redhat.devtools.lsp4ij.templates.ServerMappingSettings.toServerMappingSettings;
import static com.redhat.devtools.lsp4ij.templates.ServerMappingSettings.toServerMappings;

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

    private final Map<String /* languageId (ex : typescript) */,
            List<String> /* file extensions (ex : ts) */> languageIdFileExtensionsCache = new HashMap<>();

    private final Collection<LanguageServerDefinitionListener> listeners = new CopyOnWriteArrayList<>();

    private final Map<String, List<InlayProviderInfo>> declarativeInlayHintsProviders = new HashMap<>();

    private final List<ProviderInfo<?>> inlayHintsProviders = new ArrayList<>();

    private final Set<Language> customLanguageFindUsages = new HashSet<>();

    private LanguageServersRegistry() {
        initialize();
    }

    private void initialize() {
        // Load language servers / mappings / semanticTokensColorsProvider from user extension point
        loadServersAndMappingsFromExtensionPoint();

        // Load language servers / mappings from user settings
        loadServersAndMappingFromSettings();
        updateLanguages();
    }

    private void loadServersAndMappingsFromExtensionPoint() {
        Map<String /* server id */, List<ServerMapping>> mappings = new HashMap<>();
        Map<String /* server id */, SemanticTokensColorsProvider> semanticTokensColorsProviders = new HashMap<>();

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

        // Load semantic tokens colors providers from extensions point
        for (SemanticTokensColorsProviderExtensionPointBean extension : SemanticTokensColorsProviderExtensionPointBean.EP_NAME.getExtensions()) {
            String serverId = extension.serverId;
            try {
                semanticTokensColorsProviders.put(serverId, extension.getSemanticTokensColorsProvider());
            } catch (Exception e) {
                LOGGER.warn("Error while creating custom semanticTokensColorsProvider for server id='{}'.", serverId, e);
            }
        }

        // Load language servers from extensions point
        for (ServerExtensionPointBean server : ServerExtensionPointBean.EP_NAME.getExtensions()) {
            String serverId = server.id;
            if (serverId != null && !serverId.isEmpty()) {
                List<ServerMapping> mappingsForServer = mappings.get(serverId);
                mappings.remove(serverId);
                var serverDefinition = new ExtensionLanguageServerDefinition(server);
                SemanticTokensColorsProvider semanticTokensColorsProvider = semanticTokensColorsProviders.get(serverId);
                if (semanticTokensColorsProvider != null) {
                    serverDefinition.setSemanticTokensColorsProvider(semanticTokensColorsProvider);
                }
                addServerDefinitionWithoutNotification(serverDefinition, mappingsForServer, false);
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
            for (var settings : UserDefinedLanguageServerSettings.getInstance().getUserDefinedLanguageServerSettings()) {
                String serverId = settings.getServerId();
                if (serverId != null) {
                    var globalSettings = GlobalLanguageServerSettings.getInstance().getLanguageServerSettings(serverId);
                    List<ServerMapping> mappings = toServerMappings(serverId, settings.getMappings());
                    // Register server definition from settings
                    addServerDefinitionWithoutNotification(new UserDefinedLanguageServerDefinition(
                                    serverId,
                                    settings.getTemplateId(),
                                    settings.getServerName(),
                                    settings.getServerUrl(),
                                    "",
                                    settings.getCommandLine(),
                                    settings.getUserEnvironmentVariables(),
                                    settings.isIncludeSystemEnvironmentVariables(),
                                    globalSettings != null ? globalSettings.getConfigurationContent() : null,
                                    globalSettings == null || globalSettings.isExpandConfiguration(),
                                    globalSettings != null ? globalSettings.getConfigurationSchemaContent() : null,
                                    globalSettings != null ? globalSettings.getInitializationOptionsContent() : null,
                                    globalSettings != null ? globalSettings.getExperimentalContent() : null,
                                    settings.getClientConfigurationContent(),
                                    settings.getInstallerConfigurationContent()),
                            mappings, false);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error while loading user defined language servers from settings", e);
        }
    }

    /**
     * Collect languages from the declared mappings and associate
     * for each language an instance of LSPInlayHintsProvider, LSPColorProvider
     * LSPFindUsagesProvider to activate inlay hint, color, and find usages for those languages.
     */
    private void updateLanguages() {
        Set<Language> distinctLanguages = getDistinctLanguages();
        // When a file is not linked to a language
        //  - if the file is associated to a textmate to support syntax coloration
        //  - otherwise if the file is associated (by default) to plain/text file type
        // the language received in InlayHintProviders
        // is "textmate" / "TEXT" language, we add them to support
        // LSP codeLens, inlayHint, color for a file which is not linked to a language.
        distinctLanguages.addAll(SimpleLanguageUtils.getSupportedSimpleLanguages());
        // When a file is not linked to a language (just with a file type) and not linked to a textmate,
        // the language received in InlayHintProviders is plain/text, we add it to support
        // LSP inlayHint, color for a file which is not linked to a language.
        distinctLanguages.add(PlainTextLanguage.INSTANCE);
        // register LSPInlayHintsProvider automatically
        // for all languages associated with a language server.
        updateDeclarativeInlayHintsProviders(distinctLanguages);
        // register LSPColorProvider automatically
        // for all languages associated with a language server.
        updateInlayHintsProviders(distinctLanguages);
        // register LSPFindUsagesProvider automatically
        // for all languages associated with a language server.
        updateFindUsagesProvider(distinctLanguages);
    }

    /**
     * Returns all distinct languages which are associated with a language server.
     *
     * @return all distinct languages which are associated with a language server.
     */
    private @NotNull Set<Language> getDistinctLanguages() {
        List<Pair<Language, List<FileNameMatcher>>> languageMatchers = null;
        Set<Language> distinctLanguages = new HashSet<>();
        for (var association : fileAssociations) {
            var language = association.getLanguage();
            if (language != null) {
                // case1: Language mapping
                distinctLanguages.add(language);
            } else {
                var fileType = association.getFileType();
                if (fileType != null) {
                    // case 2: FileType mapping, collect the language is file type is a LanguageFileType
                    if (fileType instanceof LanguageFileType languageFileType) {
                        language = languageFileType.getLanguage();
                        distinctLanguages.add(language);
                    }
                } else {
                    var fileNameMatchers = association.getFileNameMatchers();
                    if (fileNameMatchers != null) {
                        // case 3: file name matchers mapping
                        if (languageMatchers == null) {
                            // Initialize list of Language /  List<FileNameMatcher> by using the standard IntelliJ registered file types.
                            languageMatchers = new ArrayList<>();
                            FileTypeManager fileTypeManager = FileTypeManager.getInstance();
                            for (FileType ft : fileTypeManager.getRegisteredFileTypes()) {
                                if (ft instanceof LanguageFileType languageFileType) {
                                    languageMatchers.add(Pair.create(languageFileType.getLanguage(), fileTypeManager.getAssociations(languageFileType)));
                                }
                            }
                        }
                        // Loop for file name matchers mappings to try to get languages
                        for (var fileNameMatcher : fileNameMatchers) {
                            String fileName = fileNameMatcher.getPresentableString();
                            for (var languageMatcher : languageMatchers) {
                                if (match(fileName, languageMatcher.getSecond())) {
                                    distinctLanguages.add(languageMatcher.getFirst());
                                }
                            }
                        }
                    }
                }
            }
        }
        return distinctLanguages;
    }

    private static boolean match(@NotNull String fileName,
                                 @NotNull List<FileNameMatcher> matchers) {
        for (var matcher : matchers) {
            if (matcher.acceptsCharSequence(fileName)) {
                return true;
            }
        }
        return false;
    }

    private void updateDeclarativeInlayHintsProviders(Set<Language> distinctLanguages) {
        LSPInlayHintsProvider lspInlayHintsProvider = new LSPInlayHintsProvider();
        inlayHintsProviders.clear();
        for (Language language : distinctLanguages) {
            List<InlayProviderInfo> hints = new ArrayList<>();
            hints.add(new InlayProviderInfo(lspInlayHintsProvider, LSPInlayHintsProvider.PROVIDER_ID, Collections.emptySet(), true, LanguageServerBundle.message("lsp.hints.declarative.provider.name")));
            declarativeInlayHintsProviders.put(language.getID(), hints);
        }
    }

    private void updateInlayHintsProviders(Set<Language> distinctLanguages) {
        LSPColorProvider lspColorProvider = new LSPColorProvider();
        inlayHintsProviders.clear();
        for (Language language : distinctLanguages) {
            inlayHintsProviders.add(new ProviderInfo<>(language, lspColorProvider));
        }
    }

    private void updateFindUsagesProvider(Set<Language> distinctLanguages) {
        customLanguageFindUsages.clear();
        // Associate the LSP find usage provider
        // for all languages associated with a language server.
        // and which does not already define a provider for the language.
        LSPFindUsagesProvider provider = new LSPFindUsagesProvider();
        for (Language language : distinctLanguages) {
            var existingProviders = LanguageFindUsages.INSTANCE.allForLanguage(language);
            if (existingProviders.isEmpty() || (existingProviders.size() == 1 && existingProviders.get(0) instanceof EmptyFindUsagesProvider)) {
                LanguageFindUsages.INSTANCE.addExplicitExtension(language, provider);
            } else {
                customLanguageFindUsages.add(language);
            }
        }
    }

    /**
     * Returns true if the given language is associated to a custom FindUsagesProvider and false otherwise.
     *
     * @param language the language.
     * @return true if the given language is associated to a custom FindUsagesProvider and false otherwise.
     */
    public boolean hasCustomLanguageFindUsages(@Nullable Language language) {
        return language != null && customLanguageFindUsages.contains(language);
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
            message.append(String.join(",", fileNamePatternMapping.getFileNamePatterns()));
            message.append("'");
        }
        message.append(" not available");
        return message.toString();
    }

    /**
     * @param language the language
     * @param fileType the file type.
     * @param fileName the file name
     * @return the {@link LanguageServerDefinition}s <strong>directly</strong> associated to the given content-type.
     * This does <strong>not</strong> include the one that match transitively as per content-type hierarchy
     */
    List<LanguageServerFileAssociation> findLanguageServerDefinitionFor(final @Nullable Language language, @Nullable FileType fileType, @NotNull String fileName) {
        return fileAssociations.stream()
                .filter(mapping -> mapping.match(language, fileType, fileName))
                .collect(Collectors.toList());
    }

    public List<LanguageServerFileAssociation> findLanguageServerDefinitionFor(final @NotNull String serverId) {
        return fileAssociations.stream()
                .filter(mapping -> serverId.equals(mapping.getServerDefinition().getId()))
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
                // Update the mapping languageId -> file extensions
                var fileExtensions = languageIdFileExtensionsCache.computeIfAbsent(languageId, k -> new ArrayList<>());
                fileExtensions.addAll(fileNamePatternMapping
                        .getFileNamePatterns()
                        .stream()
                        .map(fileExtension -> {
                            int index = fileExtension.indexOf('.');
                            return index != -1 ? fileExtension.substring(index + 1) : fileExtension;
                        })
                        .toList());

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

    public void addServerDefinition(@NotNull Project project,
                                    @NotNull LanguageServerDefinition serverDefinition,
                                    @Nullable List<ServerMappingSettings> mappings) {
        String languageServerId = serverDefinition.getId();
        addServerDefinitionWithoutNotification(serverDefinition, toServerMappings(languageServerId, mappings), true);
        updateLanguages();
        LanguageServerDefinitionListener.LanguageServerAddedEvent event = new LanguageServerDefinitionListener.LanguageServerAddedEvent(project, Collections.singleton(serverDefinition));
        for (LanguageServerDefinitionListener listener : this.listeners) {
            try {
                listener.handleAdded(event);
            } catch (Exception e) {
                LOGGER.error("Error while server definition is added of the language server '{}'", languageServerId, e);
            }
        }
    }

    private void addServerDefinitionWithoutNotification(@NotNull LanguageServerDefinition serverDefinition,
                                                        @NotNull List<ServerMapping> mappings,
                                                        boolean updateSettings) {
        String languageServerId = serverDefinition.getId();
        serverDefinitions.put(languageServerId, serverDefinition);

        // Update associations
        updateAssociations(serverDefinition, mappings);

        if (updateSettings) {
            // 1. Update user defined language settings
            if (serverDefinition instanceof UserDefinedLanguageServerDefinition userDefinedServer) {
                UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings = new UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings();
                settings.setTemplateId(userDefinedServer.getTemplateId());
                settings.setServerId(languageServerId);
                settings.setServerName(userDefinedServer.getDisplayName());
                settings.setServerUrl(userDefinedServer.getUrl());
                settings.setCommandLine(userDefinedServer.getCommandLine());
                settings.setUserEnvironmentVariables(userDefinedServer.getUserEnvironmentVariables());
                settings.setIncludeSystemEnvironmentVariables(userDefinedServer.isIncludeSystemEnvironmentVariables());
                settings.setMappings(toServerMappingSettings(mappings));
                settings.setClientConfigurationContent(userDefinedServer.getClientConfigurationContent());
                settings.setInstallerConfigurationContent(userDefinedServer.getInstallerConfigurationContent());
                UserDefinedLanguageServerSettings.getInstance().setUserDefinedLanguageServerSettings(languageServerId, settings);
            }
        }

        // 2. Update settings contributor
        var languageServerSettingsContributor = serverDefinition.getLanguageServerSettingsContributor();
        if (languageServerSettingsContributor != null) {

            var serverConfigurationContributor = languageServerSettingsContributor.getServerConfigurationContributor();
            var serverInitializationOptionsContributor = languageServerSettingsContributor.getServerInitializationOptionsContributor();
            var experimentalContributor = languageServerSettingsContributor.getServerExperimentalContributor();
            var globalSettings = GlobalLanguageServerSettings.getInstance().getLanguageServerSettings(languageServerId);
            if (globalSettings == null) {
                globalSettings = new LanguageServerSettings.LanguageServerDefinitionSettings();
            }
            if (StringUtils.isBlank(globalSettings.getConfigurationContent())) {
                globalSettings.setConfigurationContent(serverConfigurationContributor != null ? serverConfigurationContributor.getDefaultConfigurationContent() : null);
            }
            if (globalSettings.isExpandConfiguration()) {
                globalSettings.setExpandConfiguration(serverConfigurationContributor == null || serverConfigurationContributor.isDefaultExpandConfiguration());
            }
            if (StringUtils.isBlank(globalSettings.getConfigurationSchemaContent())) {
                globalSettings.setConfigurationSchemaContent(serverConfigurationContributor != null ? serverConfigurationContributor.getDefaultConfigurationSchemaContent() : null);
            }
            if (StringUtils.isBlank(globalSettings.getInitializationOptionsContent())) {
                globalSettings.setInitializationOptionsContent(serverInitializationOptionsContributor != null ? serverInitializationOptionsContributor.getDefaultInitializationOptionsContent() : null);
            }
            if (StringUtils.isBlank(globalSettings.getExperimentalContent())) {
                globalSettings.setExperimentalContent(experimentalContributor != null ? experimentalContributor.getDefaultExperimentalContent() : null);
            }
            GlobalLanguageServerSettings.getInstance().updateSettings(languageServerId, globalSettings, false);

        }

    }

    private void updateAssociations(@NotNull LanguageServerDefinition definition,
                                    @NotNull List<ServerMapping> mappings) {
        for (ServerMapping mapping : mappings) {
            registerAssociation(definition, mapping);
        }
    }

    public void removeServerDefinition(@NotNull Project project, @NotNull LanguageServerDefinition serverDefinition) {
        String languageServerId = serverDefinition.getId();
        // remove server
        serverDefinitions.remove(languageServerId);
        // remove associations
        removeAssociationsFor(serverDefinition);

        updateLanguages();
        // Update settings
        UserDefinedLanguageServerSettings.getInstance().removeServerDefinition(languageServerId);
        // Notifications
        LanguageServerDefinitionListener.LanguageServerRemovedEvent event = new LanguageServerDefinitionListener.LanguageServerRemovedEvent(project, Collections.singleton(serverDefinition));
        for (LanguageServerDefinitionListener listener : this.listeners) {
            try {
                listener.handleRemoved(event);
            } catch (Exception e) {
                LOGGER.error("Error while server definition is removed of the language server '{}'", languageServerId, e);
            }
        }
    }

    private void removeAssociationsFor(LanguageServerDefinition definition) {
        List<LanguageServerFileAssociation> mappingsToRemove = fileAssociations
                .stream()
                .filter(mapping -> definition.equals(mapping.getServerDefinition()))
                .toList();
        fileAssociations.removeAll(mappingsToRemove);
        definition.removeAssociations();
    }

    public LanguageServerDefinitionListener.@Nullable LanguageServerChangedEvent updateServerDefinition(@NotNull UpdateServerDefinitionRequest request,
                                                                                                        boolean notify) {
        String languageServerId = request.serverDefinition().getId();
        var serverDefinition = request.serverDefinition();
        
        if (serverDefinition instanceof UserDefinedLanguageServerDefinition ls) {
            ls.setName(request.name());
            ls.setUrl(request.url());
            ls.setCommandLine(request.commandLine());
            ls.setUserEnvironmentVariables(request.userEnvironmentVariables());
            ls.setIncludeSystemEnvironmentVariables(request.includeSystemEnvironmentVariables());
            ls.setClientConfigurationContent(request.clientConfigurationContent());
            ls.setInstallerConfigurationContent(request.installerConfigurationContent());

            // remove associations
            removeAssociationsFor(request.serverDefinition());
            // Update associations
            updateAssociations(request.serverDefinition(), toServerMappings(languageServerId, request.mappings()));

        }

        UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings = UserDefinedLanguageServerSettings.getInstance().getUserDefinedLanguageServerSettings(languageServerId);
        boolean nameChanged = !Objects.equals(settings.getServerName(), request.name());
        boolean commandChanged = !Objects.equals(settings.getCommandLine(), request.commandLine());
        boolean userEnvironmentVariablesChanged = !Objects.equals(settings.getUserEnvironmentVariables(), request.userEnvironmentVariables());
        boolean includeSystemEnvironmentVariablesChanged = settings.isIncludeSystemEnvironmentVariables() != request.includeSystemEnvironmentVariables();
        boolean mappingsChanged = !Objects.deepEquals(settings.getMappings(), request.mappings());
        if (mappingsChanged) {
            updateLanguages();
        }

        boolean clientConfigurationContentChanged = !Objects.equals(settings.getClientConfigurationContent(), request.clientConfigurationContent());
        boolean installerConfigurationContentChanged = !Objects.equals(settings.getInstallerConfigurationContent(), request.installerConfigurationContent());

        settings.setServerName(request.name());
        settings.setServerUrl(request.url());
        settings.setCommandLine(request.commandLine());
        settings.setUserEnvironmentVariables(request.userEnvironmentVariables());
        settings.setIncludeSystemEnvironmentVariables(request.includeSystemEnvironmentVariables());
        settings.setClientConfigurationContent(request.clientConfigurationContent());
        settings.setInstallerConfigurationContent(request.installerConfigurationContent());
        settings.setMappings(request.mappings());

        if (nameChanged || commandChanged || userEnvironmentVariablesChanged ||
                includeSystemEnvironmentVariablesChanged ||
                mappingsChanged || clientConfigurationContentChanged || installerConfigurationContentChanged) {
            // Notifications
            LanguageServerDefinitionListener.LanguageServerChangedEvent event = new LanguageServerDefinitionListener.LanguageServerChangedEvent(
                    LanguageServerDefinitionListener.LanguageServerDefinitionEvent.UpdatedBy.USER,
                    request.project(),
                    request.serverDefinition(),
                    nameChanged,
                    commandChanged,
                    userEnvironmentVariablesChanged,
                    includeSystemEnvironmentVariablesChanged,
                    mappingsChanged,
                    clientConfigurationContentChanged,
                    installerConfigurationContentChanged);
            if (notify) {
                handleChangeEvent(event);
            }
            return event;
        }
        return null;
    }

    public void handleChangeEvent(@NotNull LanguageServerDefinitionListener.LanguageServerChangedEvent event) {
        for (LanguageServerDefinitionListener listener : this.listeners) {
            try {
                listener.handleChanged(event);
            } catch (Exception e) {
                LOGGER.error("Error while server definition has changed of the language server '{}'", event.serverDefinition.getId(), e);
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
        if (file == null || file.getVirtualFile() == null) {
            return false;
        }
        Language language = LSPIJUtils.getFileLanguage(file);
        FileType fileType = file.getFileType();
        return isFileSupported(language, fileType, file.getName(), null, file, file.getProject());
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
        return isFileSupported(language, fileType, file.getName(), file, null, project);
    }

    private boolean isFileSupported(@Nullable Language language,
                                    @Nullable FileType fileType,
                                    @NotNull String filename,
                                    @Nullable VirtualFile file,
                                    @Nullable PsiFile psiFile,
                                    @NotNull Project project) {
        if (fileAssociations
                .stream()
                .anyMatch(mapping -> mapping.match(language, fileType, filename))) {
            @Nullable VirtualFile f = file != null ? file : psiFile.getVirtualFile();
            if (f != null && !f.isInLocalFileSystem()) {
                if (f instanceof LightVirtualFile) {
                    return false;
                }
                @Nullable PsiFile pf = psiFile != null ? psiFile : LSPIJUtils.getPsiFile(file, project);
                return pf == null || pf.isPhysical();
            }
            return true;
        }
        return false;
    }

    /**
     * Returns true if the virtual file and optional language are supported by a language server and false otherwise.
     * This signature should be used only when it is not yet possible yet to check the PSI file because it is in the
     * process of being created via a file view provider factory. If a PSI file is available, one of the other
     * signatures of {@link #isFileSupported} should be used instead.
     *
     * @param virtualFile the virtual file
     * @param language    the language
     * @return true if the virtual file is supported by a configured language server and false otherwise.
     */
    @ApiStatus.Internal
    public boolean isFileSupported(@Nullable VirtualFile virtualFile, @Nullable Language language) {
        if (virtualFile == null) {
            return false;
        }

        FileType fileType = virtualFile.getFileType();
        String fileName = virtualFile.getName();
        if (fileAssociations
                .stream()
                .anyMatch(mapping -> mapping.match(language, fileType, fileName))) {
            return virtualFile.isInLocalFileSystem() || !(virtualFile instanceof LightVirtualFile);
        }

        return false;
    }

    /**
     * @return the LSP codeLens / color inlay hint providers for all languages which are associated with a language server.
     */
    public List<ProviderInfo<?>> getInlayHintProviderInfos() {
        return inlayHintsProviders;
    }

    /**
     * @return the LSP inlayHint inlay hint providers for all languages which are associated with a language server.
     */
    public Map<String, List<InlayProviderInfo>> getDeclarativeInlayHintProviderInfos() {
        return declarativeInlayHintsProviders;
    }

    public record UpdateServerDefinitionRequest(@NotNull Project project,
                                                @NotNull LanguageServerDefinition serverDefinition,
                                                @Nullable String name,
                                                @Nullable String url,
                                                @Nullable String commandLine,
                                                @Nullable Map<String, String> userEnvironmentVariables,
                                                boolean includeSystemEnvironmentVariables,
                                                @NotNull List<ServerMappingSettings> mappings,
                                                @Nullable String clientConfigurationContent,
                                                @Nullable String installerConfigurationContent) {
    }

    /**
     * Returns the list of supported file extensions (ex: ts) for the given language Id (ex : typescript) and null otherwise.
     *
     * @param languageId the language Id (ex : typescript).
     * @return the list of supported file extensions (ex: ts) for the given language Id (ex : typescript) and null otherwise.
     */
    @Nullable
    public List<String> getFileExtensions(String languageId) {
        return languageIdFileExtensionsCache.get(languageId);
    }
}
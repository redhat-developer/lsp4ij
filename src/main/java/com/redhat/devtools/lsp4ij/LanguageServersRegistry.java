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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.operations.codelens.LSPCodelensProvider;
import com.redhat.devtools.lsp4ij.operations.inlayhint.LSPInlayHintsProvider;
import com.redhat.devtools.lsp4ij.server.definition.*;
import com.redhat.devtools.lsp4ij.server.definition.extension.ExtensionLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.extension.FileTypeMappingExtensionPointBean;
import com.redhat.devtools.lsp4ij.server.definition.extension.LanguageMappingExtensionPointBean;
import com.redhat.devtools.lsp4ij.server.definition.extension.ServerExtensionPointBean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Language server registry.
 */
public class LanguageServersRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServersRegistry.class);

    private static LanguageServersRegistry INSTANCE = null;

    public static LanguageServersRegistry getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LanguageServersRegistry();
        }
        return INSTANCE;
    }

    private final Map<String, LanguageServerDefinition> serverDefinitions = new HashMap<>();

    private final List<ContentTypeToLanguageServerDefinition> connections = new ArrayList<>();

    private final List<ProviderInfo<? extends Object>> inlayHintsProviders = new ArrayList<>();

    private LanguageServersRegistry() {
        initialize();
    }

    private void initialize() {
        List<ServerMapping> mappings = new ArrayList<>();

        for (ServerExtensionPointBean server : ServerExtensionPointBean.EP_NAME.getExtensions()) {
            if (server.id != null && !server.id.isEmpty()) {
                serverDefinitions.put(server.id, new ExtensionLanguageServerDefinition(server));
            }
        }

        for (LanguageMappingExtensionPointBean extension : LanguageMappingExtensionPointBean.EP_NAME.getExtensions()) {
            Language language = Language.findLanguageByID(extension.language);
            if (language != null) {
                @NotNull String languageId = StringUtils.isEmpty(extension.languageId) ? language.getID() : extension.languageId;
                mappings.add(new ServerLanguageMapping(language, extension.serverId, languageId, extension.getDocumentMatcher()));
            }
        }

        for (FileTypeMappingExtensionPointBean extension : FileTypeMappingExtensionPointBean.EP_NAME.getExtensions()) {
            FileType fileType = FileTypeManager.getInstance().findFileTypeByName(extension.fileType);
            if (fileType != null) {
                @NotNull String languageId = StringUtils.isEmpty(extension.languageId) ? fileType.getName() : extension.languageId;
                mappings.add(new ServerFileTypeMapping(fileType, extension.serverId, languageId, extension.getDocumentMatcher()));
            }
        }

        for (ServerMapping mapping : mappings) {
            LanguageServerDefinition lsDefinition = serverDefinitions.get(mapping.getServerId());
            if (lsDefinition != null) {
                registerAssociation(lsDefinition, mapping);
            } else {
                LOGGER.warn(getServerNotAvailableMessage(mapping));
            }
        }

        // register LSPInlayHintInlayHintsProvider + LSPCodelensInlayHintsProvider automatically for all languages
        // which are associated with a language server.
        Set<Language> distinctLanguages = mappings
                .stream()
                .filter(mapping -> mapping instanceof ServerLanguageMapping)
                .map(mapping -> ((ServerLanguageMapping) mapping).getLanguage())
                .collect(Collectors.toSet());
        LSPInlayHintsProvider lspInlayHintsProvider = new LSPInlayHintsProvider();
        LSPCodelensProvider lspCodeLensProvider = new LSPCodelensProvider();
        for (Language language : distinctLanguages) {
            inlayHintsProviders.add(new ProviderInfo<NoSettings>(language, lspInlayHintsProvider));
            inlayHintsProviders.add(new ProviderInfo<NoSettings>(language, lspCodeLensProvider));
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
    List<ContentTypeToLanguageServerDefinition> findServerDefinitionFor(final @Nullable Language language, @Nullable FileType fileType) {
        return connections.stream()
                .filter(mapping -> mapping.match(language, fileType))
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


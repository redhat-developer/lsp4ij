/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.launching;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.definition.ServerFileNamePatternMapping;
import com.redhat.devtools.lsp4ij.server.definition.ServerFileTypeMapping;
import com.redhat.devtools.lsp4ij.server.definition.ServerLanguageMapping;
import com.redhat.devtools.lsp4ij.server.definition.ServerMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.redhat.devtools.lsp4ij.server.definition.extension.LanguageMappingExtensionPointBean.DEFAULT_DOCUMENT_MATCHER;

/**
 * Language Server mapping settings used by the launch configuration.
 */
public class ServerMappingSettings {

    @Attribute("language")
    private String language;

    @Tag("fileType")
    private FileTypeSettings fileType;

    @Attribute("languageId")
    private String languageId;

    public ServerMappingSettings() {

    }

    private ServerMappingSettings(@Nullable String language,
                                  @Nullable FileTypeSettings fileType,
                                  @Nullable String languageId) {
        this.language = language;
        this.fileType = fileType;
        this.languageId = languageId;
    }

    public static ServerMappingSettings createLanguageMappingSettings(@NotNull String language, @Nullable String languageId) {
        return new ServerMappingSettings(language, null, languageId);
    }

    public static ServerMappingSettings createFileTypeMappingSettings(@NotNull String fileType, @Nullable String languageId) {
        return new ServerMappingSettings(null, new FileTypeSettings(fileType, null), languageId);
    }

    public static ServerMappingSettings createFileNamePatternsMappingSettings(@NotNull List<String> fileNamePatterns, @Nullable String languageId) {
        return new ServerMappingSettings(null, new FileTypeSettings(null, fileNamePatterns), languageId);
    }

    public String getLanguage() {
        return language;
    }

    public String getFileType() {
        return fileType != null ? fileType.getName() : null;
    }

    public List<String> getFileNamePatterns() {
        return fileType != null ? fileType.getPatterns() : null;
    }

    public String getLanguageId() {
        return languageId;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setFileType(String fileType) {
        this.fileType = new FileTypeSettings(fileType, null);
    }

    public void setFileNamePatterns(List<String> fileNamePatterns) {
        this.fileType = new FileTypeSettings(null, fileNamePatterns);
    }

    public void setLanguageId(String languageId) {
        this.languageId = languageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerMappingSettings that = (ServerMappingSettings) o;
        return Objects.equals(language, that.language) && Objects.equals(fileType, that.fileType) && Objects.equals(languageId, that.languageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(language, fileType, languageId);
    }

    public static List<ServerMapping> toServerMappings(@NotNull String serverId,
                                                       @Nullable List<ServerMappingSettings> mappingSettings) {
        List<ServerMapping> mappings = new ArrayList<>();
        if (mappingSettings != null && !mappingSettings.isEmpty()) {
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

    @NotNull
    public static List<ServerMappingSettings> toServerMappingSettings(@NotNull List<ServerMapping> mappings) {
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


}

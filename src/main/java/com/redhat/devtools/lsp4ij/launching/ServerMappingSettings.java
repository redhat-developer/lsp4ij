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

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
        return new ServerMappingSettings(null, new FileTypeSettings(fileType, null),languageId);
    }

    public static ServerMappingSettings createFileNamePatternsMappingSettings(@NotNull List<String> fileNamePatterns, @Nullable String languageId) {
        return new ServerMappingSettings(null, new FileTypeSettings(null, fileNamePatterns),languageId);
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
}

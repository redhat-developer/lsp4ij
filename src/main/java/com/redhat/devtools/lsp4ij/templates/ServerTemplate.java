/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.templates;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Abstract class for LSP/DAP server template.
 */
public abstract class ServerTemplate {

    // OS keys
    private static final String WINDOWS_KEY = "windows";
    private static final String MAC_KEY = "mac";
    private static final String UNIX_KEY = "unix";
    public static final String DEFAULT_KEY = "default";

    public static final String OS_KEY = SystemInfo.isWindows ? WINDOWS_KEY : (SystemInfo.isMac ? MAC_KEY : (SystemInfo.isUnix ? UNIX_KEY : null));

    // ---------- Commons JSON descriptor file name
    public static final String TEMPLATE_FILE_NAME = "template.json";
    public static final String INSTALLER_FILE_NAME = "installer.json";

    // ---------- Commons JSON property for template.json

    // id, name, url
    public static final String ID_JSON_PROPERTY = "id";
    public static final String NAME_JSON_PROPERTY = "name";
    public static final String URL_JSON_PROPERTY = "url";
    public static final String DEFAULT_JSON_PROPERTY = "default";

    // File mappings
    public static final String LANGUAGE_ID_JSON_PROPERTY = "languageId";
    public static final String FILE_TYPE_JSON_PROPERTY = "fileType";
    public static final String LANGUAGE_JSON_PROPERTY = "language";
    public static final String LANGUAGE_MAPPINGS_JSON_PROPERTY = "languageMappings";
    public static final String PATTERNS_JSON_PROPERTY = "patterns";
    public static final String FILE_TYPE_MAPPINGS_JSON_PROPERTY = "fileTypeMappings";

    private String id;
    private String name;
    private @Nullable String url;
    private Map<String /* OS */, String /* program args */> programArgs;

    private List<ServerMappingSettings> fileTypeMappings;
    private List<ServerMappingSettings> languageMappings;

    private String installerConfiguration;

    // ---------- Commons for id, name, command

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public @Nullable String getUrl() {
        return url;
    }

    public void setUrl(@Nullable String url) {
        this.url = url;
    }

    public String getProgramArgs() {
        return getOSProgramArgs();
    }

    public String getOSProgramArgs() {
        if (programArgs == null) {
            return null;
        }
        String args = programArgs.get(OS_KEY);
        if (args != null) {
            return args;
        }
        return programArgs.get(DEFAULT_KEY);
    }

    public void setProgramArgs(Map<String, String> programArgs) {
        this.programArgs = programArgs;
    }

    // ---------- Commons Methods for file mappings

    public @NotNull List<ServerMappingSettings> getLanguageMappings() {
        if (languageMappings == null) {
            languageMappings = new ArrayList<>();
        }
        return languageMappings;
    }

    public void addLanguageMapping(@NotNull ServerMappingSettings s) {
        if (this.languageMappings == null) {
            this.languageMappings = new ArrayList<>();
        }
        this.languageMappings.add(s);
    }

    public @NotNull List<ServerMappingSettings> getFileTypeMappings() {
        if (fileTypeMappings == null) {
            fileTypeMappings = new ArrayList<>();
        }
        return fileTypeMappings;
    }

    public void addFileTypeMapping(@NotNull ServerMappingSettings s) {
        if (this.fileTypeMappings == null) {
            this.fileTypeMappings = new ArrayList<>();
        }
        this.fileTypeMappings.add(s);
    }

    // ---------- Commons Methods for installation

    public String getInstallerConfiguration() {
        return installerConfiguration;
    }

    public void setInstallerConfiguration(String installerConfiguration) {
        this.installerConfiguration = installerConfiguration;
    }

}

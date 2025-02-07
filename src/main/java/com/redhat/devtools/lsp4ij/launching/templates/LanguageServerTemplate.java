/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.templates;

import com.intellij.openapi.util.SystemInfo;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A language server template.
 */
public class LanguageServerTemplate {

    public static final LanguageServerTemplate NONE = new LanguageServerTemplate() {
        @Override
        public String getName() {
            return "None";
        }
    };

    public static final LanguageServerTemplate NEW_TEMPLATE = new LanguageServerTemplate() {
        @Override
        public String getName() {
            return LanguageServerBundle.message("new.language.server.dialog.import.template.selection");
        }
    };

    public static final String TEMPLATE_FILE_NAME = "template.json";
    public static final String INITIALIZATION_OPTIONS_FILE_NAME = "initializationOptions.json";
    public static final String SETTINGS_FILE_NAME = "settings.json";
    public static final String SETTINGS_SCHEMA_FILE_NAME = "settings.schema.json";
    public static final String CLIENT_SETTINGS_FILE_NAME = "clientSettings.json";
    public static final String README_FILE_NAME = "README.md";

    public static final String ID_JSON_PROPERTY = "id";
    public static final String NAME_JSON_PROPERTY = "name";
    public static final String LANGUAGE_ID_JSON_PROPERTY = "languageId";
    public static final String FILE_TYPE_JSON_PROPERTY = "fileType";
    public static final String DEFAULT_JSON_PROPERTY = "default";
    public static final String PROGRAM_ARGS_JSON_PROPERTY = "programArgs";
    public static final String LANGUAGE_JSON_PROPERTY = "language";
    public static final String LANGUAGE_MAPPINGS_JSON_PROPERTY = "languageMappings";
    public static final String PATTERNS_JSON_PROPERTY = "patterns";
    public static final String FILE_TYPE_MAPPINGS_JSON_PROPERTY = "fileTypeMappings";

    private static final String WINDOWS_KEY = "windows";
    private static final String MAC_KEY = "mac";
    private static final String UNIX_KEY = "unix";
    private static final String DEFAULT_KEY = "default";

    private static final String OS_KEY = SystemInfo.isWindows ? WINDOWS_KEY : (SystemInfo.isMac ? MAC_KEY : (SystemInfo.isUnix ? UNIX_KEY : null));

    private String id;
    private String name;
    private Map<String /* OS */, String /* program args */> programArgs;

    private List<ServerMappingSettings> fileTypeMappings;

    private List<ServerMappingSettings> languageMappings;

    private String description;

    private String configuration;
    private String configurationSchema;
    private String initializationOptions;
    private String clientConfiguration;

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

    public List<ServerMappingSettings> getLanguageMappings() {
        if (languageMappings == null) {
            languageMappings = new ArrayList<>();
        }
        return languageMappings;
    }

    public void addLanguageMapping(ServerMappingSettings s) {
        if (this.languageMappings == null) {
            this.languageMappings = new ArrayList<>();
        }
        this.languageMappings.add(s);
    }

    public List<ServerMappingSettings> getFileTypeMappings() {
        if (fileTypeMappings == null) {
            fileTypeMappings = new ArrayList<>();
        }
        return fileTypeMappings;
    }

    public void addFileTypeMapping(ServerMappingSettings s) {
        if (this.fileTypeMappings == null) {
            this.fileTypeMappings = new ArrayList<>();
        }
        this.fileTypeMappings.add(s);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public String getConfigurationSchema() {
        return configurationSchema;
    }

    public void setConfigurationSchema(String configurationSchema) {
        this.configurationSchema = configurationSchema;
    }

    public String getInitializationOptions() {
        return initializationOptions;
    }

    public void setInitializationOptions(String initializationOptions) {
        this.initializationOptions = initializationOptions;
    }

    public String getClientConfiguration() {
        return clientConfiguration;
    }

    public void setClientConfiguration(String clientConfiguration) {
        this.clientConfiguration = clientConfiguration;
    }
}
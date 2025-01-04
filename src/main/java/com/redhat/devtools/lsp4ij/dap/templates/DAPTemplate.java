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
package com.redhat.devtools.lsp4ij.dap.templates;

import com.intellij.openapi.util.SystemInfo;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A DAP (Debug Adapter Protocol) template.
 */
public class DAPTemplate {

    public static final DAPTemplate NONE = new DAPTemplate() {
        @Override
        public String getName() {
            return "None";
        }
    };

    public static final DAPTemplate NEW_TEMPLATE = new DAPTemplate() {
        @Override
        public String getName() {
            return LanguageServerBundle.message("new.language.server.dialog.import.template.selection");
        }
    };

    public static final String TEMPLATE_FILE_NAME = "template.json";
    public static final String LAUNCH_FILE_NAME = "launch.json";
    public static final String LAUNCH_SCHEMA_FILE_NAME = "launch.schema.json";
    public static final String ATTACH_FILE_NAME = "attach.json";
    public static final String ATTACH_SCHEMA_FILE_NAME = "attach.schema.json";

    public static final String DEFAULT_JSON_PROPERTY = "default";
    public static final String PROGRAM_ARGS_JSON_PROPERTY = "programArgs";
    public static final String WAIT_FOR_TRACE_JSON_PROPERTY = "waitForTrace";
    public static final String LANGUAGE_JSON_PROPERTY = "language";
    public static final String LANGUAGE_MAPPINGS_JSON_PROPERTY = "languageMappings";
    public static final String PATTERNS_JSON_PROPERTY = "patterns";
    public static final String FILE_TYPE_JSON_PROPERTY = "fileType";
    public static final String FILE_TYPE_MAPPINGS_JSON_PROPERTY = "fileTypeMappings";
    public static final String NAME_JSON_PROPERTY = "name";

    private static final String WINDOWS_KEY = "windows";
    private static final String MAC_KEY = "mac";
    private static final String UNIX_KEY = "unix";
    private static final String DEFAULT_KEY = "default";

    private static final String OS_KEY = SystemInfo.isWindows ? WINDOWS_KEY : (SystemInfo.isMac ? MAC_KEY : (SystemInfo.isUnix ? UNIX_KEY : null));

    private String name;
    private Map<String /* OS */, String /* program args */> programArgs;

    private List<ServerMappingSettings> fileTypeMappings;

    private List<ServerMappingSettings> languageMappings;

    private String description;

    private String launchConfiguration;
    private String launchConfigurationSchema;
    private String attachConfiguration;
    private String attachConfigurationSchema;
    private String waitForTrace;

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

    public void setWaitForTrace(String waitForTrace) {
        this.waitForTrace = waitForTrace;
    }

    public String getWaitForTrace() {
        return waitForTrace;
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

    public String getLaunchConfiguration() {
        return launchConfiguration;
    }

    public void setLaunchConfiguration(String launchConfiguration) {
        this.launchConfiguration = launchConfiguration;
    }

    public String getLaunchConfigurationSchema() {
        return launchConfigurationSchema;
    }

    public void setLaunchConfigurationSchema(String launchConfigurationSchema) {
        this.launchConfigurationSchema = launchConfigurationSchema;
    }

    public String getAttachConfiguration() {
        return attachConfiguration;
    }

    public void setAttachConfiguration(String attachConfiguration) {
        this.attachConfiguration = attachConfiguration;
    }

    public String getAttachConfigurationSchema() {
        return attachConfigurationSchema;
    }

    public void setAttachConfigurationSchema(String attachConfigurationSchema) {
        this.attachConfigurationSchema = attachConfigurationSchema;
    }
}
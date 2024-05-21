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
import com.intellij.openapi.util.io.StreamUtil;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.features.documentation.MarkdownConverter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServerTemplate.class);

    private final String WINDOWS_KEY = "windows";
    private final String MAC_KEY = "mac";
    private final String UNIX_KEY = "unix";
    private final String DEFAULT_KEY = "default";

    private final String OS_KEY = SystemInfo.isWindows ? WINDOWS_KEY : (SystemInfo.isMac ? MAC_KEY : (SystemInfo.isUnix ? UNIX_KEY : null));

    private String id;

    private String name;
    private boolean dev;
    private String runtime;
    private Map<String /* OS */, String /* program args */> programArgs;

    private List<ServerMappingSettings> fileTypeMappings;

    private List<ServerMappingSettings> languageMappings;

    private Boolean hasDocumentation;

    private String description;

    private String configuration;
    private String initializationOptions;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDev() {
        return dev;
    }

    public String getRuntime() {
        return runtime;
    }

    public String getProgramArgs() {
        String programArgs = getOSProgramArgs();
        if (programArgs == null) {
            return null;
        }
        return programArgs;
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

    public void setDefaultProgramArg(String defaultArg) {
        if (this.programArgs == null) {
            this.programArgs = new HashMap<>();
        }
        this.programArgs.put(DEFAULT_KEY, defaultArg);
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

    public boolean hasDocumentation() {
        return getId() != null && (hasDocumentation == null || !hasDocumentation.booleanValue());
    }

    public String getDescription() {
        if (description != null) {
            return description.isEmpty() ? null : description;
        }
        if (!hasDocumentation()) {
            return null;
        }
        String docPath = getId() + "/README.md";
        String docContent = loadTemplateResourceContent(docPath);
        if (docContent == null) {
            description = "";
        } else {
            try {
                description = MarkdownConverter.toHTML(docContent);
            } catch (Exception e) {
                description = docContent;
                LOGGER.warn("Error while converting MarkDown language server template documentation to HTML '" + docPath + "'", e);
            }
        }
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getConfiguration() {
        if (configuration != null) {
            return configuration.isEmpty() ? null : configuration;
        }
        String id = getId();
        if (id == null) {
            return null;
        }
        String configurationPath = getId() + "/settings.json";
        String configurationContent = loadTemplateResourceContent(configurationPath);
        configuration = configurationContent != null ? configurationContent : "";
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public String getInitializationOptions() {
        if (initializationOptions != null) {
            return initializationOptions.isEmpty() ? null : initializationOptions;
        }
        String id = getId();
        if (id == null) {
            return null;
        }
        String initializationOptionsPath = getId() + "/initializationOptions.json";
        String initializationOptionsContent = loadTemplateResourceContent(initializationOptionsPath);
        initializationOptions = initializationOptionsContent != null ? initializationOptionsContent : "";
        return initializationOptions;
    }

    public void setInitializationOptions(String initializationOptions) {
        this.initializationOptions = initializationOptions;
    }

    private static String loadTemplateResourceContent(@NotNull String resourcePath) {
        try (Reader reader = LanguageServerTemplateManager.loadTemplateReader(resourcePath)) {
            return reader != null ? StreamUtil.readText(reader) : null;
        } catch (Exception e) {
            LOGGER.warn("Error while loading language server template resource '" + resourcePath + "'", e);
        }
        return null;
    }

}

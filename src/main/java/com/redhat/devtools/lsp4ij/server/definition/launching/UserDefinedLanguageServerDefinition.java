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
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;

/**
 * {@link com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition} implementation to start a
 * language server with a process command defined by the user.
 */
public class UserDefinedLanguageServerDefinition extends LanguageServerDefinition {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDefinedLanguageServerDefinition.class);//$NON-NLS-1$

    private String name;
    private String commandLine;
    private String configurationContent;
    private Object configuration;
    private String initializationOptionsContent;
    private Object initializationOptions;

    public UserDefinedLanguageServerDefinition(@NotNull String id, @NotNull String label, String description, String commandLine, String configurationContent, String initializationOptionsContent) {
        super(id, label, description, true, null, false);
        this.name = label;
        this.commandLine = commandLine;
        this.configurationContent = configurationContent;
        this.initializationOptionsContent = initializationOptionsContent;
    }

    @Override
    public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
        return new UserDefinedStreamConnectionProvider(commandLine, this);
    }

    @Override
    public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project) {
        return new UserDefinedLanguageClient(this, project);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public String getConfigurationContent() {
        return configurationContent;
    }

    public void setConfigurationContent(String configurationContent) {
        this.configurationContent = configurationContent;
        this.configuration = null;
    }

    public String getInitializationOptionsContent() {
        return initializationOptionsContent;
    }

    public void setInitializationOptionsContent(String initializationOptionsContent) {
        this.initializationOptionsContent = initializationOptionsContent;
        this.initializationOptions = null;
    }

    public Object getLanguageServerConfiguration() {
        if (configuration == null && configurationContent != null && !configurationContent.isEmpty()) {
            try {
                configuration = JsonParser.parseReader(new StringReader(configurationContent));
            } catch (Exception e) {
                LOGGER.error("Error while parsing JSON configuration for the language server '" + getId() + "'", e);
            }
        }
        return configuration;
    }

    public Object getLanguageServerInitializationOptions() {
        if (initializationOptions == null && initializationOptionsContent != null && !initializationOptionsContent.isEmpty()) {
            try {
                initializationOptions = JsonParser.parseReader(new StringReader(initializationOptionsContent));
            } catch (Exception e) {
                LOGGER.error("Error while parsing JSON Initialization Options for the language server '" + getId() + "'", e);
            }
        }
        return initializationOptions;
    }

    @Override
    public @NotNull String getDisplayName() {
        return name;
    }

}

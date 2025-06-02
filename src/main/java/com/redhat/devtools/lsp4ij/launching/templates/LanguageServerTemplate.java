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

import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.templates.ServerTemplate;

/**
 * A language server template.
 */
public class LanguageServerTemplate extends ServerTemplate {

    public static final LanguageServerTemplate NEW_TEMPLATE = new LanguageServerTemplate() {
        @Override
        public String getName() {
            return LanguageServerBundle.message("new.language.server.dialog.import.template.selection");
        }
    };

    public static final String INITIALIZATION_OPTIONS_FILE_NAME = "initializationOptions.json";
    public static final String SETTINGS_FILE_NAME = "settings.json";
    public static final String SETTINGS_SCHEMA_FILE_NAME = "settings.schema.json";
    public static final String CLIENT_SETTINGS_FILE_NAME = "clientSettings.json";
    public static final String README_FILE_NAME = "README.md";

    public static final String PROGRAM_ARGS_JSON_PROPERTY = "programArgs";

    private String description;

    private String configuration;
    private String configurationSchema;
    private String initializationOptions;
    private String clientConfiguration;


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
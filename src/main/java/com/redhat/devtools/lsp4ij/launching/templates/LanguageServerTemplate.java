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

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.templates.ServerTemplate;

import java.util.Set;

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
    public static final String EXPERIMENTAL_FILE_NAME = "experimental.json";
    public static final String SETTINGS_FILE_NAME = "settings.json";
    public static final String SETTINGS_SCHEMA_FILE_NAME = "settings.schema.json";
    public static final String CLIENT_SETTINGS_FILE_NAME = "clientSettings.json";
    public static final String README_FILE_NAME = "README.md";

    public static final String DISABLE_PROMOTION_FOR = "disablePromotionFor";
    public static final String PROGRAM_ARGS_JSON_PROPERTY = "programArgs";
    public static final String EXPAND_CONFIGURATION_JSON_PROPERTY = "expandConfiguration";

    private String description;

    private String configuration;
    private boolean expandConfiguration;
    private String configurationSchema;
    private String initializationOptions;
    private String experimental;
    private String clientConfiguration;

    private Set<String> disablePromotionFor;
    private Boolean promotable;

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

    public boolean isExpandConfiguration() {
        return expandConfiguration;
    }

    public void setExpandConfiguration(boolean expandConfiguration) {
        this.expandConfiguration = expandConfiguration;
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

    public String getExperimental() {
        return experimental;
    }

    public void setExperimental(String experimental) {
        this.experimental = experimental;
    }

    public String getClientConfiguration() {
        return clientConfiguration;
    }

    public void setClientConfiguration(String clientConfiguration) {
        this.clientConfiguration = clientConfiguration;
    }

    public void setDisablePromotionFor(Set<String> disablePromotionFor) {
        this.disablePromotionFor = disablePromotionFor;
    }

    public Set<String> getDisablePromotionFor() {
        return disablePromotionFor;
    }

    /**
     * Determines whether this language server template is eligible for promotion.
     * <p>
     * Promotion is disabled if any of the plugins listed in {@code disablePromotionFor}
     * are currently installed in the IDE. This prevents redundant or conflicting LSP suggestions
     * when native IntelliJ plugins are present.
     * </p>
     *
     * @return {@code true} if the template should be promoted; {@code false} otherwise.
     */
    public boolean isPromotable() {
        if (promotable != null) {
            return promotable;
        }
        if (disablePromotionFor == null || disablePromotionFor.isEmpty()) {
            return true;
        }
        promotable = computePromotable();
        return promotable;
    }

    private boolean computePromotable() {
        for (var pluginId : disablePromotionFor) {
            if (isPluginInstalled(pluginId)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPluginInstalled(String pluginIdString) {
        PluginId pluginId = PluginId.getId(pluginIdString);
        return PluginManagerCore.getPlugin(pluginId) != null;
    }
}
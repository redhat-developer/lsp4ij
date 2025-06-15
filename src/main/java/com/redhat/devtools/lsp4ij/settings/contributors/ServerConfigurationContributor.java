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
package com.redhat.devtools.lsp4ij.settings.contributors;

/**
 * Provides default configuration and schema for a language server.
 * <p>
 * Implementations of this interface are used to supply:
 * <ul>
 *   <li>The default JSON configuration to be sent to the language server via the {@code workspace/didChangeConfiguration} notification.</li>
 *   <li>The corresponding JSON Schema used for validation and editing assistance.</li>
 *   <li>Whether the JSON configuration should be "expanded", i.e., transformed from dotted keys into nested objects.</li>
 * </ul>
 * <p>
 * This is commonly used in environments integrating with VS Code-like settings systems.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_didChangeConfiguration">
 *      Language Server Protocol: workspace/didChangeConfiguration</a>
 */
public interface ServerConfigurationContributor {

    /**
     * Returns the default configuration content in JSON format.
     * This configuration will be sent to the language server when initializing or updating its settings.
     *
     * @return the default configuration as a JSON string
     */
    String getDefaultConfigurationContent();

    /**
     * Indicates whether the configuration content should be expanded.
     * <p>
     * For example, a dotted key like {@code "rust-analyzer.trace.server"} can be expanded into:
     * <pre>
     * {
     *   "rust-analyzer": {
     *     "trace": {
     *       "server": "off"
     *     }
     *   }
     * }
     * </pre>
     * <p>
     * This is typically required for language servers that expect deeply nested configuration objects.
     *
     * @return {@code true} if the configuration should be expanded; {@code false} otherwise
     */
    default boolean isDefaultExpandConfiguration() {
        return true;
    }

    /**
     * Returns the JSON Schema content describing the structure and types
     * of the configuration returned by {@link #getDefaultConfigurationContent()}.
     * <p>
     * This schema can be used to provide validation and auto-completion in UI editors.
     *
     * @return the JSON Schema as a string
     */
    String getDefaultConfigurationSchemaContent();
}

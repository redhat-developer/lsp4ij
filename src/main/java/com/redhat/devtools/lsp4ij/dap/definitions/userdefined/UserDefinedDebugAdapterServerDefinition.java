/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.definitions.userdefined;

import com.google.common.collect.Streams;
import com.redhat.devtools.lsp4ij.dap.LaunchConfiguration;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.server.definition.ServerMapping;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.redhat.devtools.lsp4ij.launching.ServerMappingSettings.toServerMappings;

/**
 * Represents a user-defined debug adapter server definition.
 * <p>
 * This class allows users to define custom debug adapter servers with specific configurations,
 * including environment variables, command line options, and mappings for supported languages and file types.
 * It supports defining a custom set of launch configurations and server mappings.
 * </p>
 *
 * <p>Key features:</p>
 * <ul>
 *     <li>Customizable environment variables for the language server process.</li>
 *     <li>Support for including system environment variables.</li>
 *     <li>Configurable command line, timeout, and ready pattern for the debug server.</li>
 *     <li>Allows defining mappings for languages and file types.</li>
 *     <li>Generates server mappings based on user-defined settings.</li>
 * </ul>
 *
 * @see LaunchConfiguration
 * @see ServerMappingSettings
 * @see UserDefinedDebugAdapterDescriptorFactory
 */
public class UserDefinedDebugAdapterServerDefinition extends DebugAdapterServerDefinition {

    public static final UserDefinedDebugAdapterServerDefinition NONE = new UserDefinedDebugAdapterServerDefinition("none", "NONE", "", Collections.emptyList(), Collections.emptyList());

    // Server
    private Map<String, String> userEnvironmentVariables;
    private boolean includeSystemEnvironmentVariables;
    private String commandLine;
    private int connectTimeout;
    private String debugServerReadyPattern;

    // Mappings
    private @NotNull List<ServerMappingSettings> languageMappings;
    private @NotNull List<ServerMappingSettings> fileTypeMappings;

    // Configuration
    private List<LaunchConfiguration> launchConfigurations;
    private List<ServerMapping> serverMappings;
    private String attachAddress;
    private String attachPort;
    /**
     * Creates a user-defined debug adapter server definition.
     *
     * @param id               the unique identifier for the server (must not be null)
     * @param name             the display name of the server (must not be null)
     * @param commandLine      the command line to start the language server (must not be null)
     * @param languageMappings the list of language mappings (must not be null)
     * @param fileTypeMappings the list of file type mappings (must not be null)
     */
    public UserDefinedDebugAdapterServerDefinition(@NotNull String id,
                                                   @NotNull String name,
                                                   @NotNull String commandLine,
                                                   @NotNull List<ServerMappingSettings> languageMappings,
                                                   @NotNull List<ServerMappingSettings> fileTypeMappings) {
        super(id, name);
        this.commandLine = commandLine;
        this.languageMappings = languageMappings;
        this.fileTypeMappings = fileTypeMappings;
    }

    /**
     * Creates a factory for this debug adapter server definition.
     *
     * @return the {@link UserDefinedDebugAdapterDescriptorFactory} for this server
     */
    @Override
    protected @NotNull DebugAdapterDescriptorFactory createFactory() {
        return new UserDefinedDebugAdapterDescriptorFactory();
    }

    /**
     * Returns the list of server mappings, which are generated based on language and file type mappings.
     *
     * @return a list of {@link ServerMapping} instances
     */
    @Override
    protected List<ServerMapping> getServerMappings() {
        if (serverMappings != null) {
            return serverMappings;
        }
        var allMappings = Streams.concat(getLanguageMappings().stream(),
                        getFileTypeMappings().stream())
                .toList();
        serverMappings = toServerMappings(getId(), allMappings);
        return serverMappings;
    }

    /**
     * Sets the display name for this server definition.
     *
     * @param name the new display name (must not be null)
     */
    @Override
    public void setName(@NotNull String name) {
        super.setName(name);
    }

    /**
     * Returns the user environment variables used to start the language server process.
     *
     * @return a map of user-defined environment variables, or an empty map if none are defined
     */
    @NotNull
    public Map<String, String> getUserEnvironmentVariables() {
        return userEnvironmentVariables != null ? userEnvironmentVariables : Collections.emptyMap();
    }

    /**
     * Sets the user environment variables to be used when starting the language server process.
     *
     * @param userEnvironmentVariables the user-defined environment variables
     */
    public void setUserEnvironmentVariables(Map<String, String> userEnvironmentVariables) {
        this.userEnvironmentVariables = userEnvironmentVariables;
    }

    /**
     * Returns whether system environment variables should be included when starting the language server process.
     *
     * @return {@code true} if system environment variables should be included, {@code false} otherwise
     */
    public boolean isIncludeSystemEnvironmentVariables() {
        return includeSystemEnvironmentVariables;
    }

    /**
     * Sets whether system environment variables should be included when starting the language server process.
     *
     * @param includeSystemEnvironmentVariables {@code true} to include system environment variables, {@code false} otherwise
     */
    public void setIncludeSystemEnvironmentVariables(boolean includeSystemEnvironmentVariables) {
        this.includeSystemEnvironmentVariables = includeSystemEnvironmentVariables;
    }

    /**
     * Returns the command line used to start the language server.
     *
     * @return the command line string
     */
    public String getCommandLine() {
        return commandLine;
    }

    /**
     * Sets the command line used to start the language server.
     *
     * @param commandLine the new command line
     */
    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    /**
     * Returns the list of language mappings for the server.
     *
     * @return a list of {@link ServerMappingSettings} for language mappings
     */
    public @NotNull List<ServerMappingSettings> getLanguageMappings() {
        return languageMappings;
    }

    /**
     * Sets the list of language mappings for the server.
     *
     * @param languageMappings the new list of language mappings
     */
    public void setLanguageMappings(@NotNull List<ServerMappingSettings> languageMappings) {
        this.languageMappings = languageMappings;
        this.serverMappings = null;
    }

    /**
     * Returns the list of file type mappings for the server.
     *
     * @return a list of {@link ServerMappingSettings} for file type mappings
     */
    public @NotNull List<ServerMappingSettings> getFileTypeMappings() {
        return fileTypeMappings;
    }

    /**
     * Sets the list of file type mappings for the server.
     *
     * @param fileTypeMappings the new list of file type mappings
     */
    public void setFileTypeMappings(@NotNull List<ServerMappingSettings> fileTypeMappings) {
        this.fileTypeMappings = fileTypeMappings;
        this.serverMappings = null;
    }

    /**
     * Sets the connection timeout for the server.
     *
     * @param connectTimeout the connection timeout in milliseconds
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Returns the connection timeout for the server.
     *
     * @return the connection timeout in milliseconds
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Sets the pattern that the debug server must match to indicate it is ready.
     *
     * @param debugServerReadyPattern the debug server ready pattern
     */
    public void setDebugServerReadyPattern(String debugServerReadyPattern) {
        this.debugServerReadyPattern = debugServerReadyPattern;
    }

    /**
     * Returns the pattern that the debug server must match to indicate it is ready.
     *
     * @return the debug server ready pattern
     */
    public String getDebugServerReadyPattern() {
        return debugServerReadyPattern;
    }

    /**
     * Returns the list of launch configurations for the server.
     *
     * @return a list of {@link LaunchConfiguration} instances
     */
    public List<LaunchConfiguration> getLaunchConfigurations() {
        return launchConfigurations;
    }

    /**
     * Sets the list of launch configurations for the server.
     *
     * @param launchConfigurations the new list of launch configurations
     */
    public void setLaunchConfigurations(List<LaunchConfiguration> launchConfigurations) {
        this.launchConfigurations = launchConfigurations;
    }

    public String getAttachAddress() {
        return attachAddress;
    }

    public void setAttachAddress(String attachAddress) {
        this.attachAddress = attachAddress;
    }

    public String getAttachPort() {
        return attachPort;
    }

    public void setAttachPort(String attachPort) {
        this.attachPort = attachPort;
    }
}

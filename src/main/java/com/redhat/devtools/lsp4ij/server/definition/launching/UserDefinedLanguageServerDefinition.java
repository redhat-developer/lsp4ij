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

import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.ServerInstaller;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
import com.redhat.devtools.lsp4ij.server.SocketStreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.contributors.LanguageServerSettingsContributor;
import com.redhat.devtools.lsp4ij.settings.contributors.ServerConfigurationContributor;
import com.redhat.devtools.lsp4ij.settings.contributors.ServerExperimentalContributor;
import com.redhat.devtools.lsp4ij.settings.contributors.ServerInitializationOptionsContributor;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils.createCommandLine;
import static com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils.resolveCommandLine;

/**
 * {@link com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition} implementation to start a
 * language server with a process command defined by the user.
 */
public class UserDefinedLanguageServerDefinition extends LanguageServerDefinition implements ClientConfigurableLanguageServerDefinition, CommandLineUpdater,
        ServerConfigurationContributor, ServerExperimentalContributor, ServerInitializationOptionsContributor {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDefinedLanguageServerDefinition.class);

    /**
     * The root of the language server in its command and its working directory: with {@code serverInstances}, the
     * root of each instance, and otherwise the project's directory, as {@code $PROJECT_DIR$}. A root in an archive is
     * the project's directory too.
     */
    public static final @NotNull String INSTANCE_ROOT_MACRO = "${instanceRoot}";
    private final String templateId;
    @SerializedName("displayName")
    private String name;
    private String url;
    private String commandLine;
    private String workingDir;
    private Map<String, String> userEnvironmentVariables;
    private boolean includeSystemEnvironmentVariables;

    private final String defaultConfigurationContent;
    private final boolean defaultExpandConfiguration;
    private final String defaultConfigurationSchemaContent;
    private final String defaultInitializationOptionsContent;
    private final String defaultExperimentalContent;

    private String clientConfigurationContent;
    private ClientConfigurationSettings clientConfiguration;
    private @Nullable ServerInstallerDescriptor serverInstallerDescriptor;
    private String installerConfigurationContent;
    private String workspaceFolderStrategyConfiguration;
    private @NotNull UserDefinedTransport transport = UserDefinedTransport.STDIO;

    public UserDefinedLanguageServerDefinition(@NotNull String id,
                                               @Nullable String templateId,
                                               @NotNull String name,
                                               @Nullable String url,
                                               @Nullable String description,
                                               @NotNull String commandLine,
                                               @Nullable String workingDir,
                                               @NotNull Map<String, String> userEnvironmentVariables,
                                               boolean includeSystemEnvironmentVariables,
                                               @Nullable String defaultConfigurationContent,
                                               boolean defaultExpandConfiguration,
                                               @Nullable String defaultConfigurationSchemaContent,
                                               @Nullable String defaultInitializationOptionsContent,
                                               @Nullable String defaultExperimentalContent,
                                               @Nullable String clientConfigurationContent,
                                               @Nullable String installerConfigurationContent,
                                               @Nullable String workspaceFolderStrategyConfiguration) {
        super(id, name, description, true, null, false);
        this.name = name;
        this.url = url;
        this.templateId = templateId;
        this.commandLine = commandLine;
        this.workingDir = workingDir;
        this.userEnvironmentVariables = userEnvironmentVariables;
        this.includeSystemEnvironmentVariables = includeSystemEnvironmentVariables;
        this.defaultConfigurationContent = defaultConfigurationContent;
        this.defaultConfigurationSchemaContent = defaultConfigurationSchemaContent;
        this.defaultExpandConfiguration = defaultExpandConfiguration;
        this.defaultInitializationOptionsContent = defaultInitializationOptionsContent;
        this.defaultExperimentalContent = defaultExperimentalContent;
        this.clientConfigurationContent = clientConfigurationContent;
        this.installerConfigurationContent = installerConfigurationContent;
        this.workspaceFolderStrategyConfiguration = workspaceFolderStrategyConfiguration;
    }

    // Backward-compatible signature for clients calling without client configuration content
    public UserDefinedLanguageServerDefinition(@NotNull String id,
                                               @NotNull String name,
                                               @Nullable String description,
                                               @NotNull String commandLine,
                                               @NotNull Map<String, String> userEnvironmentVariables,
                                               boolean includeSystemEnvironmentVariables,
                                               @Nullable String defaultConfigurationContent,
                                               @Nullable String defaultInitializationOptionsContent) {
        this(id,
                null,
                name,
                null,
                description,
                commandLine,
                null,
                userEnvironmentVariables,
                includeSystemEnvironmentVariables,
                defaultConfigurationContent,
                false,
                null,
                defaultInitializationOptionsContent,
                null,
                null,
                null,
                null);
    }

    @Override
    public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
        // The one language server of the project has the project's directory as root
        return createConnectionProvider(project, project.getBasePath(), null);
    }

    @Override
    public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project,
                                                                      @NotNull WorkspaceFolder instanceRoot) {
        // Without a configured working directory, an instance runs in its root, unless that is inside an archive
        var root = LSPIJUtils.findResourceFor(instanceRoot.getUri());
        String directory = root != null && root.isInLocalFileSystem() ? root.getPath() : null;
        // A root in an archive is no directory to run in or to name: the project's directory stands for it
        return createConnectionProvider(project, directory != null ? directory : project.getBasePath(), directory);
    }

    private @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project,
                                                                       @Nullable String root,
                                                                       @Nullable String defaultWorkingDir) {
        String resolvedWorkingDir = workingDir != null && !workingDir.isBlank() ? resolve(workingDir, project, root) : defaultWorkingDir;
        var transport = this.transport;
        return switch (transport.mode()) {
            case STDIO -> new UserDefinedStreamConnectionProvider(resolve(commandLine, project, root),
                    resolvedWorkingDir,
                    userEnvironmentVariables,
                    includeSystemEnvironmentVariables,
                    this,
                    project);
            case ATTACH -> new UserDefinedSocketStreamConnectionProvider(SocketStreamConnectionProvider.Direction.CONNECT,
                    // Without a host, both loopback addresses
                    transport.host() != null && !transport.host().isBlank() ? transport.host() : null,
                    transport.port(),
                    null,
                    this,
                    project);
            case START_AND_CONNECT, START_AND_ACCEPT -> new UserDefinedSocketStreamConnectionProvider(
                    transport.mode() == UserDefinedTransport.Mode.START_AND_CONNECT
                            ? SocketStreamConnectionProvider.Direction.CONNECT
                            : SocketStreamConnectionProvider.Direction.ACCEPT,
                    null,
                    transport.port(),
                    // The command line takes the port, which LSP4IJ may only choose when it starts the language server
                    port -> createCommandLine(resolve(UserDefinedTransport.withPort(commandLine, port), project, root),
                            resolvedWorkingDir,
                            userEnvironmentVariables,
                            includeSystemEnvironmentVariables),
                    this,
                    project);
        };
    }

    /**
     * Expands the macros of the command or the working directory, then sets the root for {@link #INSTANCE_ROOT_MACRO}:
     * after the macros, so that a root whose path has a {@code $} isn't taken for one.
     *
     * @param text    the command or the working directory.
     * @param project the project.
     * @param root    the root of the language server, or null if it has none.
     * @return the text with its macros resolved.
     */
    static @NotNull String resolve(@NotNull String text, @NotNull Project project, @Nullable String root) {
        String resolved = resolveCommandLine(text, project);
        return root != null ? resolved.replace(INSTANCE_ROOT_MACRO, root) : resolved;
    }

    /**
     * Returns how LSP4IJ talks to the language server: over the standard input and output of its process, or over a
     * socket.
     *
     * @return how LSP4IJ talks to the language server.
     */
    public @NotNull UserDefinedTransport getTransport() {
        return transport;
    }

    /**
     * Sets how LSP4IJ talks to the language server.
     *
     * @param transport how LSP4IJ talks to the language server.
     */
    public void setTransport(@NotNull UserDefinedTransport transport) {
        this.transport = transport;
    }

    @Override
    public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project) {
        return new UserDefinedLanguageClient(this, project);
    }

    @Override
    public @NotNull LSPClientFeatures createClientFeatures() {
        return new UserDefinedClientFeatures();
    }

    /**
     * Returns the template id which has been used to create the language server definition and null otherwise.
     *
     * @return the template id which has been used to create the language server definition and null otherwise.
     */
    @Nullable
    public String getTemplateId() {
        return templateId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String getCommandLine() {
        return commandLine;
    }

    @Override
    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    public Map<String, String> getUserEnvironmentVariables() {
        return userEnvironmentVariables;
    }

    public void setUserEnvironmentVariables(Map<String, String> userEnvironmentVariables) {
        this.userEnvironmentVariables = userEnvironmentVariables;
    }

    public boolean isIncludeSystemEnvironmentVariables() {
        return includeSystemEnvironmentVariables;
    }

    public void setIncludeSystemEnvironmentVariables(boolean includeSystemEnvironmentVariables) {
        this.includeSystemEnvironmentVariables = includeSystemEnvironmentVariables;
    }

    @Override
    public String getDefaultConfigurationContent() {
        return defaultConfigurationContent;
    }

    @Override
    public boolean isDefaultExpandConfiguration() {
        return defaultExpandConfiguration;
    }

    @Override
    public String getDefaultConfigurationSchemaContent() {
        return defaultConfigurationSchemaContent;
    }

    public String getDefaultInitializationOptionsContent() {
        return defaultInitializationOptionsContent;
    }

    @Override
    public String getDefaultExperimentalContent() {
        return defaultExperimentalContent;
    }

    public String getClientConfigurationContent() {
        return clientConfigurationContent;
    }

    public void setClientConfigurationContent(String clientConfigurationContent) {
        this.clientConfigurationContent = clientConfigurationContent;
        this.clientConfiguration = null;
    }

    public String getInstallerConfigurationContent() {
        return installerConfigurationContent;
    }

    public void setInstallerConfigurationContent(String installerConfigurationContent) {
        this.installerConfigurationContent = installerConfigurationContent;
        this.serverInstallerDescriptor = null;
    }

    public String getWorkspaceFolderStrategyConfiguration() {
        return workspaceFolderStrategyConfiguration;
    }

    public void setWorkspaceFolderStrategyConfiguration(String workspaceFolderStrategyConfiguration) {
        this.workspaceFolderStrategyConfiguration = workspaceFolderStrategyConfiguration;
    }

    @Override
    @Nullable
    public ClientConfigurationSettings getLanguageServerClientConfiguration() {
        if ((clientConfiguration == null) && (clientConfigurationContent != null) && !clientConfigurationContent.isBlank()) {
            try {
                clientConfiguration = JSONUtils.getLsp4jGson().fromJson(clientConfigurationContent, ClientConfigurationSettings.class);
            } catch (Exception e) {
                LOGGER.error("Error while parsing JSON client configuration for the language server '{}'", getId(), e);
            }
        }
        return clientConfiguration;
    }

    @Nullable
    public ServerInstallerDescriptor getServerInstallerDescriptor() {
        if ((serverInstallerDescriptor == null) && (installerConfigurationContent != null) && !installerConfigurationContent.isBlank()) {
            try {
                serverInstallerDescriptor = ServerInstallerManager.getInstance().loadInstaller(installerConfigurationContent);
            } catch (Exception e) {
                // Do nothing
            }
        }
        return serverInstallerDescriptor;
    }

    @Override
    public @NotNull String getDisplayName() {
        return name;
    }

    @Override
    public @Nullable ServerInstaller createServerInstaller() {
        return new UserDefinedLanguageServerInstaller(this);
    }

    @Override
    public @Nullable LanguageServerSettingsContributor createLanguageServerSettingsContributor() {
        return new UserDefinedLanguageServerSettingsContributor(this);
    }
}

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
package com.redhat.devtools.lsp4ij.dap.descriptors.userdefined;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.dap.ConnectingServerStrategy;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * User defined {@link DebugAdapterDescriptorFactory}.
 */
public class UserDefinedDebugAdapterDescriptorFactory extends DebugAdapterDescriptorFactory {

    // Server
    private final @NotNull String id;
    private @NotNull String name;
    private Map<String, String> userEnvironmentVariables;
    private boolean includeSystemEnvironmentVariables;
    private String commandLine;
    private String waitForTimeout;
    private String waitForTrace;

    // Mappings
    private @NotNull List<ServerMappingSettings> languageMappings;
    private @NotNull List<ServerMappingSettings> fileTypeMappings;

    // Configuration
    private String launchConfiguration;
    private String launchConfigurationSchema;
    private String attachConfiguration;
    private String attachConfigurationSchema;

    public UserDefinedDebugAdapterDescriptorFactory(@NotNull String id,
                                                    @NotNull String name,
                                                    @NotNull String commandLine,
                                                    @NotNull List<ServerMappingSettings> languageMappings,
                                                    @NotNull List<ServerMappingSettings> fileTypeMappings) {
        this.id = id;
        this.name = name;
        this.commandLine = commandLine;
        this.languageMappings = languageMappings;
        this.fileTypeMappings = fileTypeMappings;
    }

    @Override
    public @NotNull String getId() {
        return id;
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    public void setName(@NotNull String name) {
        this.name = name;
    }

    /**
     * Returns the User environment variables used to start the language server process.
     *
     * @return the User environment variables used to start the language server process.
     */
    @NotNull
    public Map<String, String> getUserEnvironmentVariables() {
        return userEnvironmentVariables != null ? userEnvironmentVariables : Collections.emptyMap();
    }

    /**
     * Set the User environment variables used to start the language server process.
     *
     * @param userEnvironmentVariables the User environment variables.
     */
    public void setUserEnvironmentVariables(Map<String, String> userEnvironmentVariables) {
        this.userEnvironmentVariables = userEnvironmentVariables;
    }

    /**
     * Returns true if System environment variables must be included when language server process starts and false otherwise.
     *
     * @return true if System environment variables must be included when language server process starts and false otherwise.
     */
    public boolean isIncludeSystemEnvironmentVariables() {
        return includeSystemEnvironmentVariables;
    }

    /**
     * Set true if System environment variables must be included when language server process starts and false otherwise.
     *
     * @param includeSystemEnvironmentVariables true if System environment variables must be included when language server process starts and false otherwise.
     */
    public void setIncludeSystemEnvironmentVariables(boolean includeSystemEnvironmentVariables) {
        this.includeSystemEnvironmentVariables = includeSystemEnvironmentVariables;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public @NotNull List<ServerMappingSettings> getLanguageMappings() {
        return languageMappings;
    }

    public void setLanguageMappings(@NotNull List<ServerMappingSettings> languageMappings) {
        this.languageMappings = languageMappings;
    }

    public @NotNull List<ServerMappingSettings> getFileTypeMappings() {
        return fileTypeMappings;
    }

    public void setFileTypeMappings(@NotNull List<ServerMappingSettings> fileTypeMappings) {
        this.fileTypeMappings = fileTypeMappings;
    }

    public void setWaitForTimeout(String waitForTimeout) {
        this.waitForTimeout = waitForTimeout;
    }

    public String getWaitForTimeout() {
        return waitForTimeout;
    }

    public void setWaitForTrace(String waitForTrace) {
        this.waitForTrace = waitForTrace;
    }

    public String getWaitForTrace() {
        return waitForTrace;
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

    @Override
    public boolean canDebug(@NotNull VirtualFile file,
                            @NotNull Project project) {
        // Match mappings?
        for (var mapping : getFileTypeMappings()) {
            // Match file type?
            String fileType = mapping.getFileType();
            if (StringUtils.isNotBlank(fileType)) {
                if (fileType.equals(file.getFileType().getName())) {
                    return true;
                }
            }
            // Match file name patterns?
            if (mapping.getFileNamePatterns() != null) {
                for (var pattern : mapping.getFileNamePatterns()) {
                    var p = FileNameMatcherFactory.getInstance().createMatcher(pattern);
                    if (p.acceptsCharSequence(file.getName())) {
                        return true;
                    }
                }
            }
        }

        for (var mapping : getLanguageMappings()) {
            // Match language?
            String language = mapping.getLanguage();
            if (StringUtils.isNotBlank(language)) {
                Language fileLanguage = LSPIJUtils.getFileLanguage(file, project);
                if (fileLanguage != null && language.equals(fileLanguage.getID())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean prepareConfiguration(@NotNull RunConfiguration configuration,
                                        @NotNull VirtualFile file,
                                        @NotNull Project project) {
        if(super.prepareConfiguration(configuration, file, project)) {
            if (configuration instanceof DAPRunConfiguration dapConfiguration) {
                // Configuration
                dapConfiguration.setLaunchParameters(getLaunchConfiguration());
                dapConfiguration.setAttachParameters(getAttachConfiguration());

                // Mappings
                dapConfiguration.setServerMappings(Stream.concat(getLanguageMappings().stream(),
                        getFileTypeMappings().stream())
                        .toList());

                // Server
                dapConfiguration.setCommand(getCommandLine());
                ConnectingServerStrategy connectingServerStrategy = ConnectingServerStrategy.NONE;
                String waitFor = getWaitForTimeout();
                if (StringUtils.isNotBlank(waitFor)) {
                    connectingServerStrategy = ConnectingServerStrategy.TIMEOUT;
                    dapConfiguration.setWaitForTimeout(Integer.parseInt(waitFor));
                } else {
                    String trackTrace = getWaitForTrace();
                    if (StringUtils.isNotBlank(trackTrace)) {
                        connectingServerStrategy = ConnectingServerStrategy.TRACE;
                        dapConfiguration.setWaitForTrace(trackTrace);
                    }
                }
                dapConfiguration.setConnectingServerStrategy(connectingServerStrategy);
            }
            return true;
        }
        return false;
    }
}

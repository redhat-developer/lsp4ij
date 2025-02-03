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
import com.redhat.devtools.lsp4ij.dap.DebugServerWaitStrategy;
import com.redhat.devtools.lsp4ij.dap.LaunchConfiguration;
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

import static com.redhat.devtools.lsp4ij.dap.LaunchConfiguration.findAttachConfiguration;
import static com.redhat.devtools.lsp4ij.dap.LaunchConfiguration.findLaunchConfiguration;

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
    private int connectTimeout;
    private String debugServerReadyPattern;

    // Mappings
    private @NotNull List<ServerMappingSettings> languageMappings;
    private @NotNull List<ServerMappingSettings> fileTypeMappings;

    // Configuration
    private List<LaunchConfiguration> launchConfigurations;

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

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setDebugServerReadyPattern(String debugServerReadyPattern) {
        this.debugServerReadyPattern = debugServerReadyPattern;
    }

    public String getDebugServerReadyPattern() {
        return debugServerReadyPattern;
    }

    @Override
    public List<LaunchConfiguration> getLaunchConfigurations() {
        return launchConfigurations;
    }

    public void setLaunchConfigurations(List<LaunchConfiguration> launchConfigurations) {
        this.launchConfigurations = launchConfigurations;
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
                var launchConfiguration = findLaunchConfiguration(getLaunchConfigurations());
                dapConfiguration.setLaunchConfiguration(launchConfiguration != null ? launchConfiguration.getContent() : "");
                var attachConfiguration = findAttachConfiguration(getLaunchConfigurations());
                dapConfiguration.setAttachConfiguration(attachConfiguration != null ? attachConfiguration.getContent() : "");

                // Mappings
                dapConfiguration.setServerMappings(Stream.concat(getLanguageMappings().stream(),
                        getFileTypeMappings().stream())
                        .toList());

                // Server
                dapConfiguration.setCommand(getCommandLine());
                DebugServerWaitStrategy debugServerWaitStrategy = DebugServerWaitStrategy.TIMEOUT;
                int connectTimeout = getConnectTimeout();
                if (connectTimeout > 0) {
                    debugServerWaitStrategy = DebugServerWaitStrategy.TIMEOUT;
                    dapConfiguration.setConnectTimeout(connectTimeout);
                } else {
                    String trackTrace = getDebugServerReadyPattern();
                    if (StringUtils.isNotBlank(trackTrace)) {
                        debugServerWaitStrategy = DebugServerWaitStrategy.TRACE;
                        dapConfiguration.setDebugServerReadyPattern(trackTrace);
                    }
                }
                dapConfiguration.setDebugServerWaitStrategy(debugServerWaitStrategy);
            }
            return true;
        }
        return false;
    }

}

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
package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.dap.DebuggingType;
import com.redhat.devtools.lsp4ij.dap.features.DAPClientFeatures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DAPRunConfiguration extends RunConfigurationBase<DAPRunConfigurationOptions> {

    private final @NotNull DAPClientFeatures clientFeatures;

    protected DAPRunConfiguration(@NotNull DAPClientFeatures clientFeatures,
                                  @NotNull Project project,
                                  @NotNull ConfigurationFactory factory,
                                  @NotNull String name) {
        super(project, factory, name);
        this.clientFeatures = clientFeatures;
    }

    @NotNull
    @Override
    protected DAPRunConfigurationOptions getOptions() {
        return (DAPRunConfigurationOptions) super.getOptions();
    }

    public String getCommand() {
        return getOptions().getCommand();
    }

    public void setCommand(String command) {
        getOptions().setCommand(command);
    }


    public String getWaitForTrace() {
        return getOptions().getWaitForTrace();
    }

    public void setWaitForTrace(String waitForTrace) {
        getOptions().setWaitForTrace(waitForTrace);
    }


    public String getWorkingDirectory() {
        return getOptions().getWorkingDirectory();
    }

    public void setWorkingDirectory(String workingDirectory) {
        getOptions().setWorkingDirectory(workingDirectory);
    }

    public String getProgram() {
        return getOptions().getProgram();
    }

    public void setProgram(String program) {
        getOptions().setProgram(program);
    }

    public String getLaunchParameters() {
        return getOptions().getLaunchParameters();
    }

    public void setLaunchParameters(String launchParameters) {
        getOptions().setLaunchParameters(launchParameters);
    }

    public String getAttachParameters() {
        return getOptions().getAttachParameters();
    }

    public void setAttachParameters(String attachParameters) {
        getOptions().setAttachParameters(attachParameters);
    }

    public DebuggingType getDebuggingType() {
        return getOptions().getDebuggingType();
    }

    public void setDebuggingType(DebuggingType debuggingType) {
        getOptions().setDebuggingType(debuggingType);
    }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return clientFeatures.getConfigurationEditor(getProject());
    }

    public @NotNull DAPClientFeatures getClientFeatures() {
        return clientFeatures;
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor,
                                    @NotNull ExecutionEnvironment environment) {
        return new DAPCommandLineState(clientFeatures, getOptions(), environment);
    }

}
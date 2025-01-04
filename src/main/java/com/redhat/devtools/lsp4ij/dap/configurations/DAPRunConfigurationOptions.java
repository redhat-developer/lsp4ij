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

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.openapi.components.StoredProperty;
import com.redhat.devtools.lsp4ij.dap.DebuggingType;

/**
 * Debug Adapter Protocol (DAP) configuration options.
 */
public class DAPRunConfigurationOptions extends RunConfigurationOptions {

    private final StoredProperty<String> command = string("")
            .provideDelegate(this, "command");

    private final StoredProperty<Integer> waitForTimeout = property(0)
            .provideDelegate(this, "waitForTimeout");

    private final StoredProperty<String> waitForTrace = string("")
            .provideDelegate(this, "waitForTrace");

    private final StoredProperty<String> program = string("")
            .provideDelegate(this, "program");

    private final StoredProperty<String> workingDirectory = string("")
            .provideDelegate(this, "workingDirectory");

    private final StoredProperty<String> debuggingType = string(DebuggingType.LAUNCH.name())
            .provideDelegate(this, "debuggingType");

    private final StoredProperty<String> launchParameters = string("")
            .provideDelegate(this, "launchParameters");

    private final StoredProperty<String> attachParameters = string("")
            .provideDelegate(this, "attachParameters");

    // Program settings

    public String getWorkingDirectory() {
        return workingDirectory.getValue(this);
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory.setValue(this, workingDirectory);
    }

    public String getProgram() {
        return program.getValue(this);
    }

    public void setProgram(String program) {
        this.program.setValue(this, program);
    }

    // Debug Adapter Protocol settings

    /**
     * Returns the command to execute to start the Debug Adapter Protocol server.
     *
     * <p>
     * ex : node path/to/js-debug/src/dapDebugServer.js ${port}
     * </p>
     *
     * @return the command to execute to start the Debug Adapter Protocol server.
     */
    public String getCommand() {
        return command.getValue(this);
    }

    /**
     * Set the command to execute to start the Debug Adapter Protocol server.
     *
     * <p>
     * ex : node path/to/js-debug/src/dapDebugServer.js ${port}
     * </p>
     *
     * @param command the command to execute to start the Debug Adapter Protocol server.
     */
    public void setCommand(String command) {
        this.command.setValue(this, command);
    }

    public String getWaitForTrace() {
        return waitForTrace.getValue(this);
    }

    public void setWaitForTrace(String waitForTrace) {
        this.waitForTrace.setValue(this, waitForTrace);
    }

    public Integer getWaitForTimeout() {
        return waitForTimeout.getValue(this);
    }

    public void setWaitForTimeout(Integer waitForTimeout) {
        this.waitForTimeout.setValue(this, waitForTimeout);
    }

    public DebuggingType getDebuggingType() {
        return DebuggingType.get(debuggingType.getValue(this));
    }

    public void setDebuggingType(DebuggingType debuggingType) {
        this.debuggingType.setValue(this, debuggingType.name());
    }

    public String getLaunchParameters() {
        return launchParameters.getValue(this);
    }

    public void setLaunchParameters(String launchParameters) {
        this.launchParameters.setValue(this, launchParameters);
    }

    public String getAttachParameters() {
        return attachParameters.getValue(this);
    }

    public void setAttachParameters(String attachParameters) {
        this.attachParameters.setValue(this, attachParameters);
    }

    /**
     * Returns the DAP launch/attach parameters according the debugging type.
     *
     * @return the DAP launch/attach parameters according the debugging type.
     */
    public String getDapParameters() {
        return getDebuggingType() == DebuggingType.ATTACH ? getAttachParameters() : getLaunchParameters();
    }
}
/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Process stream connection provider used to start a language server with a process.
 */
public abstract class ProcessStreamConnectionProvider implements StreamConnectionProvider {
    @Nullable
    private Process process;

    @Nullable
    private List<String> commands;

    @Nullable
    private String workingDir;

    @Nullable
    private Map<String, String> userEnvironmentVariables;
    private boolean includeSystemEnvironmentVariables;

    public ProcessStreamConnectionProvider() {
        this(null);
    }

    public ProcessStreamConnectionProvider(@Nullable List<String> commands) {
        this(commands, null);
    }

    public ProcessStreamConnectionProvider(@Nullable List<String> commands, @Nullable String workingDir) {
        this(commands, workingDir, null);
    }

    public ProcessStreamConnectionProvider(@Nullable List<String> commands, @Nullable String workingDir, @Nullable Map<String, String> environment) {
        this.commands = commands;
        this.workingDir = workingDir;
        this.userEnvironmentVariables = environment;
        this.includeSystemEnvironmentVariables = true;
    }

    @Override
    public void start() throws CannotStartProcessException {
        if (this.commands == null || this.commands.isEmpty() || this.commands.stream().anyMatch(Objects::isNull)) {
            throw new CannotStartProcessException("Unable to start language server: " + this.toString()); //$NON-NLS-1$
        }
        ProcessBuilder builder = createProcessBuilder();
        try {
            this.process = builder.start();
        } catch (IOException e) {
            throw new CannotStartProcessException(e);
        }
    }

    @Override
    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    @Override
    public void ensureIsAlive() throws CannotStartProcessException {
        // Wait few ms before checking the is alive flag.
        synchronized (this.process) {
            try {
                this.process.wait(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (!isAlive()) {
            throw new CannotStartProcessException("Unable to start language server: " + this.toString()); //$NON-NLS-1$
        }
    }

    protected ProcessBuilder createProcessBuilder() {
        ProcessBuilder builder = new ProcessBuilder(getCommands());
        // Add System environment variables
        if (isIncludeSystemEnvironmentVariables()) {
            builder.environment().putAll(EnvironmentUtil.getEnvironmentMap());
        }
        // Add User environment variables
        if (getUserEnvironmentVariables() != null) {
            builder.environment().putAll(getUserEnvironmentVariables());
        }
        // Working directory
        if (getWorkingDirectory() != null) {
            builder.directory(new File(getWorkingDirectory()));
        }
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        return builder;
    }

    @Override
    public @Nullable
    InputStream getInputStream() {
        Process p = process;
        return p == null ? null : p.getInputStream();
    }

    @Override
    public @Nullable
    InputStream getErrorStream() {
        Process p = process;
        return p == null ? null : p.getErrorStream();
    }

    @Override
    public @Nullable
    OutputStream getOutputStream() {
        Process p = process;
        return p == null ? null : p.getOutputStream();
    }

    public @Nullable
    Long getPid() {
        final Process p = process;
        return p == null ? null : p.pid();
    }

    @Override
    public void stop() {
        Process p = process;
        if (p != null) {
            p.destroy();
            process = null;
        }
    }

    public List<String> getCommands() {
        return commands;
    }

    public void setCommands(List<String> commands) {
        this.commands = commands;
    }

    @Nullable
    public String getWorkingDirectory() {
        return workingDir;
    }

    public void setWorkingDirectory(@Nullable String workingDir) {
        this.workingDir = workingDir;
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

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof ProcessStreamConnectionProvider other)) {
            return false;
        }
        return Objects.equals(this.getCommands(), other.getCommands()) &&
                Objects.equals(this.getWorkingDirectory(), other.getWorkingDirectory()) &&
                Objects.equals(this.getUserEnvironmentVariables(), other.getUserEnvironmentVariables()) &&
                this.isIncludeSystemEnvironmentVariables() == other.isIncludeSystemEnvironmentVariables();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getCommands(), this.getWorkingDirectory(), this.getUserEnvironmentVariables(), this.isIncludeSystemEnvironmentVariables());
    }

    @Override
    public String toString() {
        return "ProcessStreamConnectionProvider [commands=" + this.getCommands() + ", workingDir="
                + this.getWorkingDirectory() + "]";
    }

}

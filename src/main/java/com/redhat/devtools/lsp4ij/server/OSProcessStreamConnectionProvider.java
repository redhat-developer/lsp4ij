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
package com.redhat.devtools.lsp4ij.server;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Process stream connection provider used to start a language server with the
 * IntelliJ {@link OSProcessHandler} and {@link GeneralCommandLine}.
 */
public class OSProcessStreamConnectionProvider implements StreamConnectionProvider, ProcessDataProvider {

    private GeneralCommandLine commandLine;
    private OSProcessHandler processHandler;
    private InputStream inputStream;
    private final List<LanguageServerLogErrorHandler> handlers;
    private final List<Runnable> unexpectedServerStopHandlers;
    private boolean stopped;

    public OSProcessStreamConnectionProvider() {
        this(null);
    }

    public OSProcessStreamConnectionProvider(@Nullable GeneralCommandLine commandLine) {
        this.commandLine = commandLine;
        this.handlers = new ArrayList<>();
        this.unexpectedServerStopHandlers = new ArrayList<>();
    }

    /**
     * Set the command line.
     *
     * @param commandLine the command line.
     */
    public void setCommandLine(GeneralCommandLine commandLine) {
        this.commandLine = commandLine;
    }

    /**
     * Returns the command line.
     *
     * @return the command line.
     */
    public GeneralCommandLine getCommandLine() {
        return commandLine;
    }

    /**
     * Returns the OS process handler.
     *
     * @return the OS process handler.
     */
    protected OSProcessHandler getProcessHandler() {
        return processHandler;
    }


    @Override
    public @Nullable Long getPid() {
        final Process p = processHandler != null ? processHandler.getProcess() : null;
        return p == null ? null : p.pid();
    }

    @Override
    public List<String> getCommands() {
        if (commandLine == null) {
            return Collections.emptyList();
        }
        List<String> commands = new ArrayList<>();
        commands.add(commandLine.getExePath());
        commands.addAll(commandLine.getParametersList().getParameters());
        return commands;
    }

    @Override
    public void addLogErrorHandler(LanguageServerLogErrorHandler handler) {
        handlers.add(handler);
    }

    @Override
    public void addUnexpectedServerStopHandler(Runnable handler) {
        unexpectedServerStopHandlers.add(handler);
    }

    @Override
    public void start() throws CannotStartProcessException {
        if (this.commandLine == null) {
            throw new CannotStartProcessException("Unable to start language server: " + this);
        }
        try {
            processHandler = new OSProcessHandler(commandLine) {
                @Override
                protected BaseOutputReader.@NotNull Options readerOptions() {
                    // To avoid this following error:
                    // If it's a long-running mostly idle daemon process,
                    // consider overriding OSProcessHandler#readerOptions
                    // with 'BaseOutputReader.Options.forMostlySilentProcess()'
                    // to reduce CPU usage.
                    return BaseOutputReader.Options.forMostlySilentProcess();
                }
            };
            LSPProcessListener processListener = new LSPProcessListener(this);
            processHandler.addProcessListener(processListener);
            inputStream = processListener.getInputStream();
            processHandler.startNotify();
        } catch (Exception e) {
            throw new CannotStartProcessException(e);
        }
    }

    @Override
    public boolean isAlive() {
        return processHandler != null &&
                processHandler.isStartNotified() &&
                !processHandler.isProcessTerminated();
    }

    @Override
    public void ensureIsAlive() throws CannotStartProcessException {
        // Wait few ms before checking the is alive flag.
        if (processHandler != null && !isEdtAndReadAction(processHandler)) {
            processHandler.waitFor(200L);
        }
        if (!isAlive()) {
            throw new CannotStartProcessException("Unable to start language server: " + this);
        }
    }

    private static boolean isEdtAndReadAction(@NotNull ProcessHandler processHandler) {
        Application application = ApplicationManager.getApplication();
        if (application == null || !application.isInternal() || application.isHeadlessEnvironment()) {
            return false;
        }
        return application.isDispatchThread() || application.isReadAccessAllowed();
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return processHandler.getProcessInput();
    }

    @Override
    public void stop() {
        if (stopped) {
            return;
        }
        this.stopped = true;
        if (processHandler != null && !processHandler.isProcessTerminated()) {
            ExecutionManagerImpl.stopProcess(processHandler);
        }
    }

    /**
     * Returns true if the provider has been stopped with the {@link #stop()} method and false otherwise.
     *
     * @return true if the provider has been stopped with the {@link #stop()} method and false otherwise.
     */
    boolean isStopped() {
        return stopped;
    }

    List<LanguageServerLogErrorHandler> getHandlers() {
        return handlers;
    }

    List<Runnable> getUnexpectedServerStopHandlers() {
        return unexpectedServerStopHandlers;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getCommandLine());
    }

    @Override
    public String toString() {
        return "OSProcessStreamConnectionProvider [commandLine=" + this.getCommandLine() + "]";
    }

}

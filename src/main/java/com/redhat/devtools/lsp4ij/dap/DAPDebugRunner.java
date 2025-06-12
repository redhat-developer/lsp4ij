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
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.AsyncProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPCommandLineState;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.ConsoleProvider;
import com.redhat.devtools.lsp4ij.installation.ServerInstallationStatus;
import com.redhat.devtools.lsp4ij.installation.ServerInstaller;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

/**
 * Debug runner that integrates with Debug Adapter Protocol (DAP).
 * Handles server installation if necessary and launches the debug process.
 */
public class DAPDebugRunner extends AsyncProgramRunner<RunnerSettings> {

    // ID used to associate with the default IntelliJ debug executor
    public static final String DEBUG_EXECUTOR_ID = DefaultDebugExecutor.EXECUTOR_ID;

    // Unique ID for this runner
    private static final String RUNNER_ID = "DAPDebugRunner";

    @Override
    public @NotNull String getRunnerId() {
        return RUNNER_ID;
    }

    /**
     * Determines whether this runner can handle the given run configuration.
     */
    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return profile instanceof DAPRunConfiguration dap && dap.canRun(executorId);
    }

    /**
     * Executes the run configuration. If the debugger needs to be installed, it shows
     * a temporary console and performs installation first, then launches the debug session.
     */
    @Override
    protected @NotNull Promise<@Nullable RunContentDescriptor> execute(@NotNull ExecutionEnvironment env,
                                                                       @NotNull RunProfileState state) {
        FileDocumentManager.getInstance().saveAllDocuments(); // Ensure all documents are saved

        final DAPCommandLineState dapState = (DAPCommandLineState) state;
        var serverDefinition = dapState.getServerDescriptor().getServerDefinition();
        final ServerInstaller installer = serverDefinition != null ? serverDefinition.getServerInstaller() : null;

        // If already installed or no installer required, run directly
        if (installer == null || installer.getStatus() == ServerInstallationStatus.INSTALLED) {
            try {
                return Promises.resolvedPromise(doExecute(env, dapState));
            } catch (Exception e) {
                return Promises.rejectedPromise(e);
            }
        }

        // Otherwise, install the debugger first
        AsyncPromise<RunContentDescriptor> promise = new AsyncPromise<>();

        // Create a temporary console to show installation progress
        var consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(env.getProject());
        var console = consoleBuilder.getConsole();
        console.print("⏳ Downloading debugger...\n", ConsoleViewContentType.NORMAL_OUTPUT);

        RunContentDescriptor tempDescriptor = new RunContentDescriptor(
                console, null, console.getComponent(), "DAP Debugger (initializing)"
        );
        RunContentManager.getInstance(env.getProject())
                .showRunContent(env.getExecutor(), tempDescriptor);

        var consoleProvider = new ConsoleProvider(console, env.getProject());
        installer.registerConsoleProvider(consoleProvider);

        // Trigger installation
        installer
                .checkInstallation()
                .handle((ignored, err) -> {
                    if (err != null) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            console.print("❌ Debugger installation failed: " + err.getMessage(),
                                    ConsoleViewContentType.ERROR_OUTPUT);
                            promise.setError(new ExecutionException("Debugger installation failed", err));
                        });
                        return null;
                    }

                    // Launch the debug session after installation
                    ApplicationManager.getApplication().invokeLater(() -> {
                        try {
                            if (serverDefinition instanceof CommandLineUpdater commandLineUpdater) {
                                dapState.setCommandLine(commandLineUpdater.getCommandLine());
                            }

                            RunContentDescriptor finalDescriptor = doExecute(env, dapState);

                            // Remove temporary initialization console
                            RunContentManager.getInstance(env.getProject())
                                    .removeRunContent(env.getExecutor(), tempDescriptor);

                            promise.setResult(finalDescriptor);
                        } catch (Throwable e) {
                            promise.setError(e);
                        }
                    });
                    return null;
                });

        return promise;
    }

    /**
     * Launches the actual debug session using the IntelliJ XDebugger infrastructure.
     */
    private @NotNull RunContentDescriptor doExecute(@NotNull ExecutionEnvironment env, DAPCommandLineState dapState)
            throws ExecutionException {
        return XDebuggerManager.getInstance(env.getProject())
                .startSession(env, new XDebugProcessStarter() {
                    @Override
                    public @NotNull XDebugProcess start(@NotNull XDebugSession session) throws ExecutionException {
                        ExecutionResult result = dapState.execute(env.getExecutor(), DAPDebugRunner.this);
                        boolean debugMode = DEBUG_EXECUTOR_ID.equals(env.getExecutor().getId());
                        return new DAPDebugProcess(dapState, session, result, debugMode);
                    }
                }).getRunContentDescriptor();
    }
}

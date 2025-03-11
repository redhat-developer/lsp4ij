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
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPCommandLineState;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debug Adapter Protocol (DAP) program runner.
 */
public class DAPDebugRunner extends GenericProgramRunner {

    private static final @NotNull
    @NonNls String RUNNER_ID = "DAPDebugRunner";

    public static final String DEBUG_EXECUTOR_ID = "Debug";

    @Override
    public @NotNull @NonNls String getRunnerId() {
        return RUNNER_ID;
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return profile instanceof DAPRunConfiguration dapProfile && dapProfile.canRun(executorId);
    }

    @Override
    protected @Nullable RunContentDescriptor doExecute(@NotNull RunProfileState state,
                                                       @NotNull ExecutionEnvironment environment) throws ExecutionException {
        FileDocumentManager.getInstance().saveAllDocuments();
        return createContentDescriptor(state, environment);
    }

    private RunContentDescriptor createContentDescriptor(@NotNull RunProfileState state,
                                                         @NotNull ExecutionEnvironment environment) throws ExecutionException {
        return XDebuggerManager.getInstance(environment.getProject())
                .startSession(environment, new XDebugProcessStarter() {
                    @Override
                    public @NotNull XDebugProcess start(final @NotNull XDebugSession session) throws ExecutionException {
                        final DAPCommandLineState dapState = (DAPCommandLineState) state;
                        final ExecutionResult executionResult = state.execute(environment.getExecutor(), DAPDebugRunner.this);
                        boolean debugMode = DEBUG_EXECUTOR_ID.equals(environment.getExecutor().getId());
                        return new DAPDebugProcess(dapState, session, executionResult, debugMode);
                    }
                }).getRunContentDescriptor();
    }
}

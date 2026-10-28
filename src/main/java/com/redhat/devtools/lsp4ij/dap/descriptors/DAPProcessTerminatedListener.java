/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.descriptors;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.ide.IdeCoreBundle;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import org.jetbrains.annotations.NotNull;

/**
 * Shows "Process finished with exit code N" when the process of a DAP debug session ends, as
 * {@link ProcessTerminatedListener} does. The process is the debug adapter's, so N is the exit code of the debugged
 * program when the debug adapter reported it with the 'exited' event ({@link DAPDebugProcess#PROGRAM_EXIT_CODE}), and
 * the exit code of the debug adapter otherwise.
 */
public final class DAPProcessTerminatedListener implements ProcessListener {

    private DAPProcessTerminatedListener() {
    }

    /**
     * Shows the exit code of the debugged program when the given process of a debug adapter ends.
     *
     * @param processHandler the process handler of the debug adapter.
     */
    public static void attach(@NotNull ProcessHandler processHandler) {
        processHandler.addProcessListener(new DAPProcessTerminatedListener());
    }

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
        ProcessHandler processHandler = event.getProcessHandler();
        processHandler.removeProcessListener(this);
        Integer programExitCode = processHandler.getUserData(DAPDebugProcess.PROGRAM_EXIT_CODE);
        int exitCode = programExitCode != null ? programExitCode : event.getExitCode();
        String message = IdeCoreBundle.message("finished.with.exit.code.text.message",
                ProcessTerminatedListener.stringifyExitCode(exitCode));
        processHandler.notifyTextAvailable("\n" + message + "\n", ProcessOutputTypes.SYSTEM);
    }
}

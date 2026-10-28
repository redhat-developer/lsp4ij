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
import com.intellij.openapi.util.Key;
import com.intellij.testFramework.LightPlatformTestCase;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;

/**
 * The process of a DAP debug session is the debug adapter's, which often ends with its own exit code, such as 143 when
 * it is stopped. When the debug adapter reported the exit code of the debugged program with the 'exited' event, the
 * console shows that one instead.
 */
public class DAPProcessTerminatedListenerTest extends LightPlatformTestCase {

    public void testTheProgramsExitCodeIsShown() {
        var adapter = new AdapterProcess();
        adapter.putUserData(DAPDebugProcess.PROGRAM_EXIT_CODE, 3);

        String output = terminate(adapter);

        assertTrue(output, output.contains("exit code 3"));
        assertFalse(output, output.contains("143"));
    }

    public void testTheAdaptersExitCodeIsShownWithoutAnExitedEvent() {
        String output = terminate(new AdapterProcess());

        assertTrue(output, output.contains("exit code 143"));
    }

    public void testAChildSessionDoesNotReplaceTheExitCodeOfTheSessionThatStartedIt() {
        var adapter = new AdapterProcess();
        DAPDebugProcess.keepProgramExitCode(adapter, 7, false);
        DAPDebugProcess.keepProgramExitCode(adapter, 0, true);

        String output = terminate(adapter);

        assertTrue(output, output.contains("exit code 7"));
    }

    public void testTheSessionThatStartedAChildReplacesItsExitCode() {
        var adapter = new AdapterProcess();
        DAPDebugProcess.keepProgramExitCode(adapter, 0, true);
        DAPDebugProcess.keepProgramExitCode(adapter, 7, false);

        String output = terminate(adapter);

        assertTrue(output, output.contains("exit code 7"));
    }

    public void testAChildSessionsExitCodeIsShownWithoutOneOfTheSessionThatStartedIt() {
        var adapter = new AdapterProcess();
        DAPDebugProcess.keepProgramExitCode(adapter, 3, true);

        String output = terminate(adapter);

        assertTrue(output, output.contains("exit code 3"));
        assertFalse(output, output.contains("143"));
    }

    private static String terminate(AdapterProcess adapter) {
        DAPProcessTerminatedListener.attach(adapter);
        var output = new StringBuilder();
        adapter.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                output.append(event.getText());
            }
        });
        adapter.startNotify();
        adapter.destroyProcess();
        assertTrue(adapter.isProcessTerminated());
        return output.toString();
    }

    /**
     * A debug adapter's process, which ends with exit code 143 when it is stopped.
     */
    private static class AdapterProcess extends ProcessHandler {
        @Override
        protected void destroyProcessImpl() {
            notifyProcessTerminated(143);
        }

        @Override
        protected void detachProcessImpl() {
            notifyProcessDetached();
        }

        @Override
        public boolean detachIsDefault() {
            return false;
        }

        @Override
        public @Nullable OutputStream getProcessInput() {
            return null;
        }
    }
}

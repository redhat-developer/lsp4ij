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
package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.debug.ProcessEventArguments;
import org.eclipse.lsp4j.debug.ProcessEventArgumentsStartMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link DAPClient#getProcessMessage(ProcessEventArguments, String)} tests: the console line of a DAP 'process' event
 * names the process, and gives what the event says of it, leaving out what the event leaves out.
 */
public class DAPClientProcessMessageTest extends BasePlatformTestCase {

    public void testTheLineHasThePidAndTheStartMethod() {
        assertEquals("Debugging process '/path/app' (PID 1234, launched)",
                message(process("/path/app", 1234, true, ProcessEventArgumentsStartMethod.LAUNCH)));
    }

    public void testThePidIsNotGrouped() {
        assertEquals("Debugging process 'app' (PID 1234567)", message(process("app", 1234567, null, null)));
    }

    public void testTheLineLeavesOutWhatTheEventLeavesOut() {
        assertEquals("Debugging process '/path/app' (launched)",
                message(process("/path/app", null, null, ProcessEventArgumentsStartMethod.LAUNCH)));
        assertEquals("Debugging process '/path/app' (PID 1234)", message(process("/path/app", 1234, null, null)));
        assertEquals("Debugging process '/path/app'", message(process("/path/app", null, null, null)));
    }

    public void testALocalProcessIsNotSaidToBeLocal() {
        assertEquals("Debugging process 'app'", message(process("app", null, true, null)));
    }

    public void testAProcessWhichIsNotLocalIsSaidToBeRemote() {
        assertEquals("Debugging process 'app' (PID 1234, remote, attached)",
                message(process("app", 1234, false, ProcessEventArgumentsStartMethod.ATTACH)));
    }

    public void testEachStartMethod() {
        assertEquals("Debugging process 'app' (launched)", message(process("app", null, null, ProcessEventArgumentsStartMethod.LAUNCH)));
        assertEquals("Debugging process 'app' (attached)", message(process("app", null, null, ProcessEventArgumentsStartMethod.ATTACH)));
        assertEquals("Debugging process 'app' (launched suspended, then attached)",
                message(process("app", null, null, ProcessEventArgumentsStartMethod.ATTACH_FOR_SUSPENDED_LAUNCH)));
    }

    public void testAProcessWithoutAName() {
        // DAP requires the name, but a debug adapter may leave it empty
        assertEquals("Debugging a process (PID 1234)", message(process("", 1234, null, null)));
        assertEquals("Debugging a process (PID 1234)", message(process(" ", 1234, null, null)));
    }

    public void testTheLineOfAChildSessionNamesTheSession() {
        assertEquals("Debugging process 'python3' (PID 4321, attached) in the session 'Subprocess 4321'",
                DAPClient.getProcessMessage(process("python3", 4321, true, ProcessEventArgumentsStartMethod.ATTACH), "Subprocess 4321"));
    }

    private static String message(ProcessEventArguments args) {
        return DAPClient.getProcessMessage(args, null);
    }

    private static ProcessEventArguments process(@NotNull String name,
                                                 @Nullable Integer pid,
                                                 @Nullable Boolean local,
                                                 @Nullable ProcessEventArgumentsStartMethod startMethod) {
        var args = new ProcessEventArguments();
        args.setName(name);
        args.setSystemProcessId(pid);
        args.setIsLocalProcess(local);
        args.setStartMethod(startMethod);
        return args;
    }
}

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.dap.console.DAPConsoleView;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;

/**
 * Verifies what the debug console prints past its filter of DAP traces, which hides the DAP messages in the process
 * output of a debug adapter that talks over stdio: the result of an evaluation typed in the console, and the process
 * output of a debug adapter that talks over a socket.
 */
public class DAPDebugProcessConsolePrintTest extends BasePlatformTestCase {

    private static final String JSON_RESULT = "{\"name\": \"value\"}\n";

    public void testAnEvaluationResultWhichIsAJsonObjectIsPrinted() throws IOException {
        var console = stdioConsole();

        DAPDebugProcess.printOnConsole(console, JSON_RESULT, ConsoleViewContentType.NORMAL_OUTPUT, true);
        console.flushDeferredText();

        assertEquals(JSON_RESULT, console.getEditor().getDocument().getText());
    }

    public void testTheDebugAdaptersOutputWhichIsAJsonObjectIsFilteredAsADapTrace() throws IOException {
        // What the filter is for: a DAP trace of a stdio adapter, which the result above must not be taken for
        var console = stdioConsole();

        DAPDebugProcess.printOnConsole(console, JSON_RESULT, ConsoleViewContentType.NORMAL_OUTPUT, false);
        console.flushDeferredText();

        assertEquals("", console.getEditor().getDocument().getText());
    }

    public void testTheProcessOutputOfASocketDebugAdapterIsPrintedAsItIs() throws IOException {
        // 'dlv dap' talks over a socket, and the program it runs writes to Delve's own output, a line at a time:
        // fmt.Println("a"), fmt.Println(), fmt.Println("b") and fmt.Println(student{1, 2})
        var console = console();
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            DAPDebugProcess.getTransportStreams(new DefaultExecutionResult(console, new NopProcessHandler()),
                    server.getInetAddress().getHostAddress(), server.getLocalPort()).close();
        }

        for (String line : List.of("a\n", "\n", "b\n", "{1 2}\n")) {
            // How the console prints the process output
            console.print(line, ConsoleViewContentType.NORMAL_OUTPUT);
        }
        console.flushDeferredText();

        assertEquals("a\n\nb\n{1 2}\n", console.getEditor().getDocument().getText());
    }

    private DAPConsoleView stdioConsole() throws IOException {
        var console = console();
        DAPDebugProcess.getTransportStreams(new DefaultExecutionResult(console, new NopProcessHandler()), null, null);
        return console;
    }

    private DAPConsoleView console() {
        var console = new DAPConsoleView(getProject(), GlobalSearchScope.allScope(getProject()), false, false);
        Disposer.register(getTestRootDisposable(), console);
        // The console's editor exists once its component does
        console.getComponent();
        return console;
    }
}

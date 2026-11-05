/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.dap.console.DAPConsoleView;

/**
 * Verifies that the result of an evaluation typed in the debug console is printed past the console's filter of DAP
 * traces, which hides normal output that is a JSON object.
 */
public class DAPDebugProcessConsolePrintTest extends BasePlatformTestCase {

    private static final String JSON_RESULT = "{\"name\": \"value\"}\n";

    public void testAnEvaluationResultWhichIsAJsonObjectIsPrinted() {
        var console = console();

        DAPDebugProcess.printOnConsole(console, JSON_RESULT, ConsoleViewContentType.NORMAL_OUTPUT, true);
        console.flushDeferredText();

        assertEquals(JSON_RESULT, console.getEditor().getDocument().getText());
    }

    public void testTheDebugAdaptersOutputWhichIsAJsonObjectIsFilteredAsADapTrace() {
        // What the filter is for: a DAP trace of a stdio adapter, which the result above must not be taken for
        var console = console();

        DAPDebugProcess.printOnConsole(console, JSON_RESULT, ConsoleViewContentType.NORMAL_OUTPUT, false);
        console.flushDeferredText();

        assertEquals("", console.getEditor().getDocument().getText());
    }

    private DAPConsoleView console() {
        var console = new DAPConsoleView(getProject(), GlobalSearchScope.allScope(getProject()), false, false);
        Disposer.register(getTestRootDisposable(), console);
        // The console's editor exists once its component does
        console.getComponent();
        return console;
    }
}

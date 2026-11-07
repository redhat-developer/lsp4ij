/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.console;

import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.concurrent.CompletableFuture;

/**
 * Verifies that a line typed in a DAP console reaches the console's input handler, and not the process's input.
 */
public class DAPConsoleViewInputTest extends BasePlatformTestCase {

    public void testALineTypedInTheConsoleGoesToItsInputHandler() throws Exception {
        var console = new DAPConsoleView(getProject(), GlobalSearchScope.allScope(getProject()), false, false);
        Disposer.register(getTestRootDisposable(), console);
        // The console's editor exists once its component does
        console.getComponent();
        var process = new NopProcessHandler();
        console.attachToProcess(process);
        process.startNotify();
        var typed = new CompletableFuture<String>();
        console.setInputHandler(typed::complete);

        var editor = console.getEditor();
        var context = DataManager.getInstance().getDataContext(editor.getContentComponent());
        for (char c : "1 + 2".toCharArray()) {
            TypedAction.getInstance().actionPerformed(editor, c, context);
        }
        // Enter, as the console's Enter handler does it: a line end of user input, which sends the typed line
        console.print("\n", ConsoleViewContentType.USER_INPUT);
        console.flushDeferredText();

        // The console hands the typed line over in an alarm of its own, which runs as this thread dispatches events.
        // The bound only ends a hang.
        PlatformTestUtil.waitWithEventsDispatching("the typed line must reach the input handler", typed::isDone, 3600);
        assertEquals("1 + 2", typed.get());
        process.destroyProcess();
    }
}

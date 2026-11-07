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
package com.redhat.devtools.lsp4ij.dap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link DAPDebugProcess#getConsoleText(String, boolean, boolean)} tests: the output of a DAP 'output' event is
 * printed as the debug adapter sent it, and a message of LSP4IJ's own on a line of its own.
 */
class DAPDebugProcessConsoleTextTest {

    private static final String DISCONNECTED = "Disconnected successfully from the debug server.";

    @Test
    void output_promptWithoutLineEnd_isPrintedAsSent() {
        // a program that prints a prompt and waits for input: the input goes on the prompt's line
        assertEquals("Name? ", DAPDebugProcess.getConsoleText("Name? ", false, true));
    }

    @Test
    void output_lineWrittenInPieces_isPrintedAsSent() {
        assertEquals("1, ", DAPDebugProcess.getConsoleText("1, ", false, true));
        assertEquals("2, ", DAPDebugProcess.getConsoleText("2, ", false, false));
        assertEquals("3\n", DAPDebugProcess.getConsoleText("3\n", false, false));
    }

    @Test
    void output_lineEndsAndEmptyLines_areKept() {
        assertEquals("\n", DAPDebugProcess.getConsoleText("\n", false, true));
        assertEquals("a\n\nb", DAPDebugProcess.getConsoleText("a\n\nb", false, true));
    }

    @Test
    void message_goesOnALineOfItsOwn() {
        assertEquals(DISCONNECTED + "\n", DAPDebugProcess.getConsoleText(DISCONNECTED, true, true));
        assertEquals("done\n", DAPDebugProcess.getConsoleText("done\n", true, true));
    }

    @Test
    void message_afterOutputThatDidNotEndItsLine_startsANewLine() {
        assertEquals("\n" + DISCONNECTED + "\n", DAPDebugProcess.getConsoleText(DISCONNECTED, true, false));
    }

    @Test
    void empty_printsNothing() {
        assertNull(DAPDebugProcess.getConsoleText("", false, true));
        assertNull(DAPDebugProcess.getConsoleText(null, false, true));
        assertNull(DAPDebugProcess.getConsoleText("", true, false));
        assertNull(DAPDebugProcess.getConsoleText(null, true, false));
    }
}

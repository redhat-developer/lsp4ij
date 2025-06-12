/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.console;

import com.intellij.execution.ui.ConsoleViewContentType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DAPConsoleView#getApplicableText(String, ConsoleViewContentType)} tests.
 */
class DAPConsoleViewTest {

    @Test
    void testSystemOutput_isReturnedAsIs() {
        String text = "This is system output";
        String result = DAPConsoleView.getApplicableText(text, ConsoleViewContentType.SYSTEM_OUTPUT);
        assertEquals(text, result);
    }

    @Test
    void testNormalOutput_contentLengthFilteredOut() {
        String text = "Content-Length: 123";
        String result = DAPConsoleView.getApplicableText(text, ConsoleViewContentType.NORMAL_OUTPUT);
        assertNull(result);
    }

    @Test
    void testNormalOutput_singleLineFilteredOut() {
        String text = "\n";
        String result = DAPConsoleView.getApplicableText(text, ConsoleViewContentType.NORMAL_OUTPUT);
        assertNull(result);
    }

    @Test
    void testNormalOutput_validJsonFollowedByContentLength_shouldBeFilteredOut() {
        String json = "{ \"foo\": { \"bar\": 1 } }Content-Length: 123";
        String result = DAPConsoleView.getApplicableText(json, ConsoleViewContentType.NORMAL_OUTPUT);
        assertNull(result);
    }

    @Test
    void testNormalOutput_validJsonFollowedByPrintable_shouldReturnPrintablePart() {
        String json = "{ \"foo\": { \"bar\": 1 } }Remaining Output";
        String result = DAPConsoleView.getApplicableText(json, ConsoleViewContentType.NORMAL_OUTPUT);
        assertEquals("Remaining Output", result);
    }

    @Test
    void testNormalOutput_partialJson_shouldReturnWhole() {
        String text = "{ \"foo\": { \"bar\": 1 ";
        String result = DAPConsoleView.getApplicableText(text, ConsoleViewContentType.NORMAL_OUTPUT);
        assertEquals(text, result);
    }

    @Test
    void testNormalOutput_otherText_shouldBeNull() {
        String text = "Just some log";
        String result = DAPConsoleView.getApplicableText(text, ConsoleViewContentType.NORMAL_OUTPUT);
        assertNull(result);
    }

    @Test
    void testNormalOutput_jsonOnly_shouldReturnNullIfFollowedByNewline() {
        String text = "{ \"a\": 1 }Content-Length: 45";
        String result = DAPConsoleView.getApplicableText(text, ConsoleViewContentType.NORMAL_OUTPUT);
        assertNull(result);
    }
}
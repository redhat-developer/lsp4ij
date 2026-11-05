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

import com.intellij.execution.impl.ConsoleState;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
        assertEquals("Just some log", result);
    }

    @Test
    void testNormalOutput_jsonOnly_shouldReturnNullIfFollowedByNewline() {
        String text = "{ \"a\": 1 }Content-Length: 45";
        String result = DAPConsoleView.getApplicableText(text, ConsoleViewContentType.NORMAL_OUTPUT);
        assertNull(result);
    }

    @Test
    void typedLines_goToTheInputHandler_withoutTheirLineEnds() throws Exception {
        var lines = new ArrayList<String>();
        var state = new DAPConsoleView.InputState(new RunningState(), () -> lines::add);

        state.sendUserInput("1 + 2\n");
        state.sendUserInput("first\r\nsecond\n");

        assertEquals(List.of("1 + 2", "first", "second"), lines);
    }

    @Test
    void aTypedLine_waitsForItsEnd() throws Exception {
        var lines = new ArrayList<String>();
        var state = new DAPConsoleView.InputState(new RunningState(), () -> lines::add);

        state.sendUserInput("par");
        assertEquals(List.of(), lines);
        state.sendUserInput("tial\n");

        assertEquals(List.of("partial"), lines);
    }

    @Test
    void anEmptyTypedLine_isALine() throws Exception {
        // A program which waits for Enter takes the empty line as its input
        var lines = new ArrayList<String>();
        var state = new DAPConsoleView.InputState(new RunningState(), () -> lines::add);

        state.sendUserInput("\n");

        assertEquals(List.of(""), lines);
    }

    @Test
    void withoutAnInputHandler_theRunningStateTakesTheInput() throws Exception {
        var running = new RunningState();
        var state = new DAPConsoleView.InputState(running, () -> null);

        state.sendUserInput("text\n");

        assertEquals(List.of("text\n"), running.input);
    }

    /**
     * A running console state which records the input it gets.
     */
    private static class RunningState extends ConsoleState {

        private final List<String> input = new ArrayList<>();

        @Override
        public @NotNull ConsoleState attachTo(@NotNull ConsoleViewImpl console, @NotNull ProcessHandler processHandler) {
            return this;
        }

        @Override
        public @NotNull ConsoleState dispose() {
            return this;
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public void sendUserInput(@NotNull String input) {
            this.input.add(input);
        }
    }
}
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
package com.redhat.devtools.lsp4ij.dap.console;

import com.intellij.execution.impl.ConsoleState;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.ConsoleViewRunningState;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.redhat.devtools.lsp4ij.console.actions.AutoFoldingAction;
import com.redhat.devtools.lsp4ij.console.actions.LSPClearConsoleAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Extends {@link ConsoleViewImpl} to support custom DAP folding by using [Trace
 */
public class DAPConsoleView extends ConsoleViewImpl {

    private volatile @Nullable Consumer<String> inputHandler;
    private volatile boolean stdioTransport;

    public DAPConsoleView(@NotNull Project project,
                          @NotNull GlobalSearchScope searchScope,
                          boolean viewer,
                          boolean usePredefinedMessageFilter) {
        super(project, searchScope, viewer, new DAPConsoleState(), usePredefinedMessageFilter);
    }

    /**
     * Sets what takes each line typed in the console, without its line end, such as the debug session, which
     * evaluates it. Without one, the console takes no input, since a debug adapter's process input can be the debug
     * protocol's channel.
     *
     * @param inputHandler what takes each typed line, or null for no input.
     */
    public void setInputHandler(@Nullable Consumer<String> inputHandler) {
        this.inputHandler = inputHandler;
    }

    /**
     * Sets that the debug adapter talks over its process's stdio, so that the process output carries the DAP
     * messages, which the console hides. Over a socket, the process output is the adapter's own, or the debugged
     * program's, such as the output of the program that Delve runs, and the console prints it as it is: a blank line
     * or a line which starts with '{' is then no DAP trace.
     */
    public void setStdioTransport() {
        this.stdioTransport = true;
    }

    private static class DAPConsoleState extends ConsoleState.NotStartedStated {

        @Override
        public @NotNull ConsoleState attachTo(@NotNull ConsoleViewImpl console, @NotNull ProcessHandler processHandler) {
            // The process's output is shown; its input, which can be the debug protocol's channel, takes nothing
            var running = new ConsoleViewRunningState(console, processHandler, this, true, false);
            return console instanceof DAPConsoleView dapConsole ?
                    new InputState(running, () -> dapConsole.inputHandler) : running;
        }
    }

    /**
     * The state of a console attached to a process, which hands each line typed in the console to the console's input
     * handler, if it has one, and is the given running state otherwise.
     */
    static class InputState extends ConsoleState {

        private final ConsoleState running;
        private final Supplier<@Nullable Consumer<String>> inputHandler;
        private final StringBuilder typed = new StringBuilder();

        InputState(@NotNull ConsoleState running, @NotNull Supplier<@Nullable Consumer<String>> inputHandler) {
            this.running = running;
            this.inputHandler = inputHandler;
        }

        @Override
        public synchronized void sendUserInput(@NotNull String input) throws IOException {
            var handler = inputHandler.get();
            if (handler == null) {
                running.sendUserInput(input);
                return;
            }
            // A line not ended yet waits for its end
            typed.append(input);
            for (int end = typed.indexOf("\n"); end >= 0; end = typed.indexOf("\n")) {
                int lineEnd = end > 0 && typed.charAt(end - 1) == '\r' ? end - 1 : end;
                String line = typed.substring(0, lineEnd);
                typed.delete(0, end + 1);
                handler.accept(line);
            }
        }

        @Override
        public @NotNull ConsoleState attachTo(@NotNull ConsoleViewImpl console, @NotNull ProcessHandler processHandler) {
            return dispose().attachTo(console, processHandler);
        }

        @Override
        public @NotNull ConsoleState dispose() {
            return running.dispose();
        }

        @Override
        public boolean isFinished() {
            return running.isFinished();
        }

        @Override
        public boolean isRunning() {
            return running.isRunning();
        }

        @Override
        public boolean isCommandLine(@NotNull String line) {
            return running.isCommandLine(line);
        }
    }

    /**
     * Prints text which is not the debug adapter's process output, such as the result of an evaluation typed in the
     * console, which the filter of DAP traces in {@link #print(String, ConsoleViewContentType)} must not hide: a
     * result can be a JSON object.
     *
     * @param text        the text.
     * @param contentType the console content type.
     */
    public void printUnfiltered(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
        super.print(text, contentType);
    }

    @Override
    public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
        String applicableText = stdioTransport ? getApplicableText(text, contentType) : text;
        if (applicableText != null) {
            super.print(applicableText, contentType);
        }
    }

    @TestOnly
    public static @Nullable String getApplicableText(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
        if (contentType != ConsoleViewContentType.NORMAL_OUTPUT) {
            // 1) SYSTEM_OUTPUT:
            //    -> node C:/Users/XXXX/.lsp4ij/dap/vscode-js-debug/js-debug/src/dapDebugServer.js 55125 127.0.0.1
            //    -> [Trace - 14:15:26] Received notification 'output'

            // 2) LOG_DEBUG_OUTPUT -> js-debug/launch

            // 3) LOG_INFO_OUTPUT -> C:\Program Files\nodejs\node.exe --experimental-network-inspection .\foo.js

            // 4) LOG_ERROR_OUTPUT -> "Uncaught SyntaxError foo.js:1...

            // returns the original text.
            return text;
        }
        // NORMAL_OUTPUT
        // ex: Debug server listening at 127.0.0.1:55125 (must be displayed)
        // At this
        if (isContentLengthDapTrace(text)) {
            // DAP traces from stdio (Content-Length)
            return null;
        }
        if (text.startsWith("{")) {
            // DAP traces from stdio (Json)
            int offset = findLastCompleteJsonBlockEnd(text);
            if (offset == -1) {
                return text;
            }
            offset++;
            String s =  text.length() > offset ? text.substring(offset) : null;
            if (s != null && isContentLengthDapTrace(s)) {
                return null;
            }
            return s;
        }
        return text;
    }

    private static boolean isContentLengthDapTrace(@NotNull String text) {
        return text.startsWith("Content-Length:") || text.equals("\n");
    }

    private static int findLastCompleteJsonBlockEnd(@NotNull String text) {
        int depth = 0;
        int lastValidEnd = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    lastValidEnd = i;
                } else if (depth < 0) {
                    depth = 0;
                }
            }
        }
        return lastValidEnd;
    }

    @Override
    public AnAction @NotNull [] createConsoleActions() {
        // Don't call super.createConsoleActions() to avoid having some action like previous occurrence that we don't need.
        List<AnAction> consoleActions = new ArrayList<>();
        var editor = getEditor();
        if (editor != null) {
            consoleActions.add(new AutoFoldingAction(editor));
            consoleActions.add(new ScrollToTheEndToolbarAction(editor));
        }
        consoleActions.add(ActionManager.getInstance().getAction("Print"));
        consoleActions.add(new LSPClearConsoleAction(this));
        return consoleActions.toArray(AnAction.EMPTY_ARRAY);
    }

    @Override
    protected void updateFoldings(int startLine, int endLine) {
        super.updateFoldings(startLine, endLine);
        if (!canApplyFolding()) {
            return;
        }

        var editor = getEditor();
        editor.getFoldingModel().runBatchFoldingOperation(() -> {
            var document = editor.getDocument();
            int foldingStartOffset = -1;
            String foldingPaceholder = null;
            int lineNumber = 0;
            boolean expanded = AutoFoldingAction.shouldLSPTracesBeExpanded(editor);
            int nbEmptyLine = 0;
            for (int line = startLine; line <= endLine; line++) {
                var lineText = EditorHyperlinkSupport.getLineText(document, line, false);
                if (lineText.startsWith("[Trace")) {
                    foldingStartOffset = document.getLineStartOffset(line);
                    foldingPaceholder = lineText;
                    lineNumber = 0;
                } else {
                    if (foldingStartOffset != -1) {
                        // Folding...
                        if (lineText.isEmpty()) {
                            nbEmptyLine++;
                            if (nbEmptyLine == 2) {
                                var foldingEndOffset = document.getLineStartOffset(line);
                                // Fold the previous Trace
                                var region = editor.getFoldingModel().addFoldRegion(foldingStartOffset, foldingEndOffset, foldingPaceholder);
                                if (region != null) {
                                    region.setExpanded(expanded);
                                }
                                nbEmptyLine = 0;
                                foldingStartOffset = -1;
                                foldingPaceholder = null;
                            }
                        } else {
                            lineNumber++;
                        }
                    }
                }
            }
            if (foldingStartOffset != -1 && lineNumber > 0) {
                // Fold the previous end Trace
                var foldingEndOffset = document.getLineStartOffset(endLine) - 1;
                var region = editor.getFoldingModel().addFoldRegion(foldingStartOffset, foldingEndOffset, foldingPaceholder);
                if (region != null) {
                    region.setExpanded(expanded);
                }
            }
        });
    }

    /**
     * Returns true if language server settings is configured with "verbose" level trace for the language server and false otherwise.
     *
     * @return true if language server settings is configured with "verbose" level trace for the language server and false otherwise.
     */
    private boolean canApplyFolding() {
        /*UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = UserDefinedLanguageServerSettings.getInstance(getProject()).getLanguageServerSettings(serverDefinition.getId());
        if (settings == null) {
            return false;
        }
        return settings.getServerTrace() == ServerTrace.verbose;*/
        return true;
    }

}

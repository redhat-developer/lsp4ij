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
package com.redhat.devtools.lsp4ij.installation;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides an abstraction around IntelliJ's ConsoleView,
 * allowing custom printing and live-updating progress messages.
 */
public class ConsoleProvider {

    // The console instance used to display output
    private final @NotNull ConsoleView console;

    // The associated IntelliJ project
    private final @NotNull Project project;

    /**
     * Constructs a ConsoleProvider with the given console and project.
     */
    public ConsoleProvider(@NotNull ConsoleView console,
                           @NotNull Project project) {
        this.console = console;
        this.project = project;
    }

    /**
     * Returns the associated ConsoleView instance.
     */
    public @NotNull ConsoleView getConsole() {
        return console;
    }

    /**
     * Returns the associated Project.
     */
    public @NotNull Project getProject() {
        return project;
    }

    /**
     * Clears all content in the console.
     */
    public void clear() {
        console.clear();
    }

    /**
     * Prints a message to the console, followed by a newline.
     *
     * @param message     the text to print
     * @param contentType the content type (normal, error, etc.)
     */
    public void print(@Nullable String message,
                      @NotNull ConsoleViewContentType contentType) {
        console.print(message + "\n", contentType);
    }

    /**
     * Replaces the last printed line in the console with the specified message.
     * Useful for showing progress updates without flooding the console.
     *
     * If the console does not support direct document access, falls back to standard print.
     *
     * @param message the message to replace the last line with
     */
    public void printProgress(@Nullable String message) {
        if (message == null) {
            return;
        }

        // Check if we can access the underlying editor (ConsoleViewImpl)
        if (console instanceof ConsoleViewImpl consoleView) {
            Editor editor = consoleView.getEditor();
            if (editor != null) {
                Document document = editor.getDocument();
                // Perform document update in a write-safe context
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    try {
                        int lineCount = document.getLineCount();
                        if (lineCount > 0) {
                            int startOffset = document.getLineStartOffset(lineCount - 1);
                            int endOffset = document.getLineEndOffset(lineCount - 1);
                            document.replaceString(startOffset, endOffset, message);
                        } else {
                            document.insertString(0, message);
                        }
                    } catch (Exception e) {
                        // On failure, just print the message normally
                        print(message, ConsoleViewContentType.NORMAL_OUTPUT);
                    }
                });
            }
        } else {
            // If not a ConsoleViewImpl, fallback to normal print
            print(message, ConsoleViewContentType.NORMAL_OUTPUT);
        }
    }
}

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
package com.redhat.devtools.lsp4ij.installation.definition;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.download.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents the execution context during a Language Server installation or verification process.
 * This context holds the associated {@link Project}, the type of {@link InstallerAction} to perform,
 * an optional {@link CommandLineUpdater} for modifying configuration, and runtime status/reporting capabilities.
 *
 * <p>This class provides utilities for:</p>
 * <ul>
 *   <li>Logging messages to the IntelliJ console and progress indicator</li>
 *   <li>Reporting errors and installation status</li>
 *   <li>Storing key-value properties during execution</li>
 *   <li>Updating command lines if required</li>
 * </ul>
 *
 * @see Reporter
 * @see InstallerAction
 * @see CommandLineUpdater
 */
public class InstallerContext implements Reporter {

    /**
     * Represents a structured status message with an associated severity level.
     *
     * @param message The message to log or report.
     * @param level   The log level, such as {@link java.util.logging.Level#INFO} or {@link java.util.logging.Level#SEVERE}.
     */
    public static record InstallationStatus(String message, Level level) {}

    private final @NotNull Project project;
    private final @NotNull InstallerAction action;
    private final @Nullable CommandLineUpdater commandLineUpdater;
    private final @NotNull Map<String, String> properties;
    private boolean flushOnEachPrint;
    private @Nullable ConsoleView console;
    private @Nullable ProgressIndicator progressIndicator;
    private final @NotNull List<InstallationStatus> status;

    /**
     * Constructs a new {@code InstallerContext}.
     *
     * @param project             the IntelliJ {@link Project} in which the installation is taking place.
     * @param action              the {@link InstallerAction} to be performed (e.g., CHECK, RUN).
     * @param commandLineUpdater  optional component responsible for updating the launch configuration.
     */
    public InstallerContext(@NotNull Project project,
                            @NotNull InstallerAction action,
                            @Nullable CommandLineUpdater commandLineUpdater) {
        this.project = project;
        this.action = action;
        this.commandLineUpdater = commandLineUpdater;
        this.properties = new HashMap<>();
        this.status = new ArrayList<>();
        setFlushOnEachPrint(false);
    }

    /**
     * Sets whether the console output should be flushed after each print.
     *
     * @param flushOnEachPrint {@code true} to flush after each message; {@code false} otherwise.
     */
    public void setFlushOnEachPrint(boolean flushOnEachPrint) {
        this.flushOnEachPrint = flushOnEachPrint;
    }

    public @NotNull Project getProject() {
        return project;
    }

    public @NotNull InstallerAction getAction() {
        return action;
    }

    /**
     * Assigns a {@link ConsoleView} for output display.
     *
     * @param console the console to use.
     * @return the current {@code InstallerContext} for chaining.
     */
    public @NotNull InstallerContext setConsole(@Nullable ConsoleView console) {
        this.console = console;
        return this;
    }

    public @Nullable ProgressIndicator getProgressIndicator() {
        return progressIndicator;
    }

    /**
     * Assigns a {@link ProgressIndicator} for visual feedback.
     *
     * @param progressIndicator the progress indicator to use.
     * @return the current {@code InstallerContext} for chaining.
     */
    public @NotNull InstallerContext setProgressIndicator(@NotNull ProgressIndicator progressIndicator) {
        this.progressIndicator = progressIndicator;
        return this;
    }

    public @Nullable CommandLineUpdater getCommandLineUpdater() {
        return commandLineUpdater;
    }

    /**
     * Stores a key-value property in this context and logs it to the console.
     *
     * @param key   the property name.
     * @param value the property value.
     */
    public void putProperty(@NotNull String key, @Nullable String value) {
        properties.put(key, value);
        print("${" + key + "}=" + value);
    }

    /**
     * Retrieves a property value by key.
     *
     * @param key the property name.
     * @return the corresponding value, or {@code null} if not present.
     */
    public @Nullable String getProperty(@NotNull String key) {
        return properties.get(key);
    }

    /**
     * Returns all property keys currently stored in this context.
     *
     * @return a set of property names.
     */
    public Set<String> getPropertyKeys() {
        return properties.keySet();
    }

    /**
     * Clears the console output if available.
     */
    public void clear() {
        if (console != null) {
            console.clear();
        }
    }

    /**
     * Prints a message to the console with {@link ConsoleViewContentType#NORMAL_OUTPUT}.
     *
     * @param message the message to print.
     */
    public void print(@Nullable String message) {
        print(message, ConsoleViewContentType.NORMAL_OUTPUT);
    }

    public void printError(@Nullable String message, @NotNull Exception e) {
        printError(message, e, false);
    }
    /**
     * Prints an error message and stack trace to the console.
     *
     * @param message the contextual error message.
     * @param e       the exception to display.
     */
    public void printError(@Nullable String message, @NotNull Exception e, boolean add) {
        Writer s = new StringWriter();
        e.printStackTrace(new PrintWriter(s));
        printError((message != null ? message : "") + s.toString(), add);
    }

    public void printError(@Nullable String message) {
        printError(message, false);
    }

    /**
     * Prints an error message to the console with {@link ConsoleViewContentType#ERROR_OUTPUT}.
     *
     * @param message the error message.
     */
    public void printError(@Nullable String message, boolean add) {
        print(message, ConsoleViewContentType.ERROR_OUTPUT);
        if (add && message != null) {
            addErrorMessage(message);
        }
    }

    /**
     * Prints a message to the console with a specified content type.
     *
     * @param message     the message to print.
     * @param contentType the output content type.
     */
    public void print(@Nullable String message,
                      @NotNull ConsoleViewContentType contentType) {
        if (message == null) {
            return;
        }
        if (console != null) {
            console.print(message + "\n", contentType);
            if (flushOnEachPrint && console instanceof ConsoleViewImpl consoleView) {
                ApplicationManager.getApplication().invokeLater(consoleView::flushDeferredText);
            }
        }
        if (progressIndicator != null) {
            progressIndicator.setText2(message);
        }
    }

    /**
     * Replaces the last printed line in the console with the specified message (used for progress updates).
     *
     * @param message the message to replace the last line with.
     */
    public void printProgress(String message) {
        if (message == null) {
            return;
        }
        if (console != null) {
            Editor editor = ((ConsoleViewImpl) console).getEditor();
            if (editor != null) {
                Document document = editor.getDocument();
                int lineCount = document.getLineCount();
                if (lineCount > 0) {
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        try {
                            int startOffset = document.getLineStartOffset(lineCount - 1);
                            int endOffset = document.getLineEndOffset(lineCount - 1);
                            document.replaceString(startOffset, endOffset, message);
                        } catch (Exception e) {
                            // Ignore error
                        }
                    });
                }
            }
        }
    }

    @Override
    public void setText(@NotNull String text) {
        print(text);
    }

    @Override
    public void setText(@NotNull String text, @NotNull Exception e) {
        printError(text, e);
    }

    @Override
    public void checkCanceled() {
        // Not implemented
    }

    /**
     * Appends an informational message to the internal status list.
     *
     * @param message the message to record.
     */
    public void addInfoMessage(@NotNull String message) {
        status.add(new InstallationStatus(message, Level.INFO));
    }

    /**
     * Appends an error message to the internal status list.
     *
     * @param message the error message to record.
     */
    public void addErrorMessage(@NotNull String message) {
        status.add(new InstallationStatus(message, Level.SEVERE));
    }

    /**
     * Returns a list of status messages accumulated during execution.
     *
     * @return a list of {@link InstallationStatus} records.
     */
    public @NotNull List<InstallationStatus> getStatus() {
        return status;
    }

    /**
     * Represents the type of action to perform during installation.
     */
    public static enum InstallerAction {
        /** Only checks for prerequisites or conditions. */
        CHECK,

        /** Only performs the actual installation or execution. */
        RUN,

        /** Performs both a check and the installation. */
        CHECK_AND_RUN
    }
}

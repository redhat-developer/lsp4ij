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

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.ExceptionUtil;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.ConsoleProvider;
import com.redhat.devtools.lsp4ij.installation.download.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Represents the execution context during a Language Server installation or verification process.
 * This context holds the associated {@link Project}, the type of {@link InstallerAction} to perform,
 * an optional {@link CommandLineUpdater} for modifying configuration, and runtime status/reporting capabilities.
 *
 * <p>This class provides utilities for:</p>
 * <ul>
 *   <li>Logging messages to the IntelliJ console and progress indicator, and to the IDE's log, whatever consoles are
 *   registered</li>
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

    private static final Logger LOGGER = LoggerFactory.getLogger(InstallerContext.class);

    private final @Nullable Project project;
    private final @NotNull InstallerAction action;
    private @Nullable CommandLineUpdater commandLineUpdater;
    private final @NotNull Map<String, Object> properties;
    private @Nullable Supplier<List<ConsoleProvider>> consoleProviders;

    private @Nullable ProgressIndicator progressIndicator;
    private final @NotNull List<InstallationStatus> status;

    private @Nullable Set<Runnable> onPreInstall;
    private @Nullable Set<Runnable> onPostInstall;

    private boolean showNotification;
    private boolean checkAgainAfterWaiting;
    // The name of the installer, which the log puts before each line
    private @Nullable String installerName;

    /**
     * Constructs a new {@code InstallerContext}.
     *
     * @param project             the IntelliJ {@link Project} in which the installation is taking place.
     * @param action              the {@link InstallerAction} to be performed (e.g., CHECK, RUN).
     */
    public InstallerContext(@Nullable Project project,
                            @NotNull InstallerAction action) {
        this.project = project;
        this.action = action;
        this.properties = new HashMap<>();
        this.status = new ArrayList<>();
        setShowNotification(true);
    }

    public @Nullable Project getProject() {
        return project;
    }

    public @NotNull InstallerAction getAction() {
        return action;
    }

    /**
     * Sets the name of the installer which runs with this context, which the IDE's log puts before each line.
     *
     * @param installerName the name of the installer.
     */
    void setInstallerName(@Nullable String installerName) {
        this.installerName = installerName;
    }

    public void setConsoleProviders(@Nullable Supplier<List<ConsoleProvider>> consoleProviders) {
        this.consoleProviders = consoleProviders;
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

    public void setCommandLineUpdater(@Nullable CommandLineUpdater commandLineUpdater) {
        this.commandLineUpdater = commandLineUpdater;
    }

    public void setOnPreInstall(@Nullable Set<Runnable> onPreInstall) {
        this.onPreInstall = onPreInstall;
    }

    public @NotNull Set<Runnable> getOnPreInstall() {
        return onPreInstall != null ? onPreInstall : Collections.emptySet();
    }

    public void setOnPostInstall(@Nullable Set<Runnable> onPostInstall) {
        this.onPostInstall = onPostInstall;
    }

    public @NotNull Set<Runnable> getOnPostInstall() {
        return onPostInstall != null ? onPostInstall : Collections.emptySet();
    }

    /**
     * Stores a key-value property in this context and logs it to the console.
     *
     * @param key   the property name.
     * @param value the property value.
     */
    public void putProperty(@NotNull String key, @Nullable Object value) {
        properties.put(key, value);
        print("${" + key + "}=" + value);
    }

    /**
     * Removes a property from this context, if it is there, and logs it to the console.
     *
     * @param key the property name.
     */
    public void removeProperty(@NotNull String key) {
        if (properties.remove(key) != null) {
            print("${" + key + "} removed");
        }
    }

    /**
     * Retrieves a property value by key.
     *
     * @param key the property name.
     * @return the corresponding value, or {@code null} if not present.
     */
    public @Nullable Object getProperty(@NotNull String key) {
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
        if (consoleProviders != null) {
            consoleProviders
                    .get()
                            .forEach(ConsoleProvider::clear);
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
     * Prints a message to the console with a specified content type, and writes it to the IDE's log at the level of
     * its content type: an error as a warning, a step at INFO, and the rest at DEBUG.
     *
     * @param message     the message to print.
     * @param contentType the output content type.
     */
    public void print(@Nullable String message,
                      @NotNull ConsoleViewContentType contentType) {
        print(message, contentType, getLogLevel(contentType));
    }

    /**
     * Prints a message to the consoles with the given content type, and writes it to the IDE's log at the given level,
     * whatever consoles are registered, as when none is. Each line that an installer prints goes through this method.
     * <p>
     * {@link Level#WARNING} and above are logged as warnings, {@link Level#INFO} at INFO, the levels below at DEBUG,
     * which {@code #com.redhat.devtools.lsp4ij.installation} in Debug Log Settings turns on, and {@link Level#OFF} not
     * at all. The log gets the message without its values which can be secrets, as a token.
     *
     * @param message     the message to print.
     * @param contentType the output content type.
     * @param logLevel    the level at which the IDE's log gets the message.
     */
    public void print(@Nullable String message,
                      @NotNull ConsoleViewContentType contentType,
                      @NotNull Level logLevel) {
        if (message == null) {
            return;
        }
        if (consoleProviders != null) {
            consoleProviders
                    .get()
                    .forEach(provider -> provider.print(message, contentType));
        }
        if (progressIndicator != null) {
            progressIndicator.setText2(message);
        }
        log(logLevel, message);
    }

    /**
     * Writes the given message to the IDE's log at the given level, after the name of the installer, without its
     * values which can be secrets, as {@link #print(String, ConsoleViewContentType, Level)} says, but not to the
     * consoles, as a line for the log only, which says how a task ended.
     *
     * @param level   the level at which the IDE's log gets the message.
     * @param message the message.
     */
    public void log(@NotNull Level level, @NotNull String message) {
        if (level.equals(Level.OFF)) {
            return;
        }
        // SEVERE too is logged as a warning: the IDE reports an error of the log as an error of LSP4IJ
        boolean warning = level.intValue() >= Level.WARNING.intValue();
        boolean info = !warning && level.intValue() >= Level.INFO.intValue();
        if (!warning && !info && !LOGGER.isDebugEnabled()) {
            return;
        }
        String line = message.strip();
        if (line.isEmpty()) {
            return;
        }
        String logged = (installerName != null ? "Installer '" + installerName + "': " : "Installer: ")
                + SecretRedactor.redact(line, EnvironmentUtil.getEnvironmentMap());
        if (warning) {
            LOGGER.warn("{}", logged);
        } else if (info) {
            LOGGER.info("{}", logged);
        } else {
            LOGGER.debug("{}", logged);
        }
    }

    /**
     * Returns the level at which the IDE's log gets a message of the given content type: a warning for an error, INFO
     * for a step, as {@code - Step: <name>}, and DEBUG for the rest.
     */
    private static @NotNull Level getLogLevel(@NotNull ConsoleViewContentType contentType) {
        if (contentType == ConsoleViewContentType.ERROR_OUTPUT
                || contentType == ConsoleViewContentType.LOG_ERROR_OUTPUT
                || contentType == ConsoleViewContentType.LOG_WARNING_OUTPUT) {
            return Level.WARNING;
        }
        return contentType == ConsoleViewContentType.LOG_INFO_OUTPUT ? Level.INFO : Level.FINE;
    }

    /**
     * Replaces the last printed line in the console with the specified message (used for progress updates).
     *
     * @param message the message to replace the last line with.
     */
    public void printProgress(@Nullable String message) {
        if (message == null) {
            return;
        }
        if (consoleProviders != null) {
            consoleProviders
                    .get()
                    .forEach(provider -> provider.printProgress(message));
        }
    }

    @Override
    public void setText(@NotNull String text) {
        print(text);
    }

    /**
     * Prints the given error of an asset fetcher, as {@link #printError(String, Exception)} does. The IDE's log gets it
     * at DEBUG only, since the fetcher logs its errors itself, with what it fetched.
     */
    @Override
    public void setText(@NotNull String text, @NotNull Exception e) {
        print(text + ExceptionUtil.getThrowableText(e), ConsoleViewContentType.ERROR_OUTPUT, Level.FINE);
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


    public @NotNull String resolveValues(@NotNull String unresolved){
        String resolved = unresolved;
        var keys = getPropertyKeys();
        for(var key : keys) {
            Object value = getProperty(key);
            if (value != null) {
                resolved = resolved.replace("${" + key + "}", value.toString());
            }
        }
        return resolved;
    }

    public void setShowNotification(boolean showNotification) {
        this.showNotification = showNotification;
    }

    public boolean isShowNotification() {
        return showNotification;
    }

    /**
     * Returns true if an exec task which waited for another one to leave its working directory runs the check of the
     * installer again, and doesn't run its command if the check then passes, as when the other one installed the
     * server; and false otherwise, as when the installation is forced.
     *
     * @return true if an exec task which waited runs the check of the installer again, and false otherwise.
     */
    public boolean isCheckAgainAfterWaiting() {
        return checkAgainAfterWaiting;
    }

    /**
     * Sets whether an exec task which waited for another one to leave its working directory runs the check of the
     * installer again, and doesn't run its command if the check then passes.
     *
     * @param checkAgainAfterWaiting true if an exec task which waited runs the check of the installer again.
     */
    public void setCheckAgainAfterWaiting(boolean checkAgainAfterWaiting) {
        this.checkAgainAfterWaiting = checkAgainAfterWaiting;
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

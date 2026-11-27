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

import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.redhat.devtools.lsp4ij.installation.ConsoleProvider;
import com.redhat.devtools.lsp4ij.settings.ui.InstallerPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the installer of a debug adapter printed at the start of a debug session, in the console
 * {@code DAP Debugger (initializing)}, which the debug runner removes once the session starts: the {@code Installer} tab
 * of the session shows it, so that the check and the installation which ran stay in sight (#2296).
 */
final class DAPInstallerOutput {

    // The output of the installation which ran at the start of the debug session of an execution environment
    private static final Key<DAPInstallerOutput> KEY = Key.create("lsp4ij.dap.installerOutput");

    /**
     * A text that the installer printed, with its content type.
     */
    private record Printed(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    }

    // Guarded by itself: an installer prints many lines, as npm install does, which a copy on each write would make
    // quadratic
    private final List<Printed> printed = new ArrayList<>();

    /**
     * Keeps what the installer prints into the given console from now on for the debug session of the given execution
     * environment, the check and the installation, and returns the console provider which the installer prints with.
     *
     * @param console     the console of the start of the debug session.
     * @param project     the project of the debug session.
     * @param environment the execution environment of the debug session.
     * @return the console provider which prints into the console, and keeps what it prints.
     */
    static @NotNull ConsoleProvider record(@NotNull ConsoleView console,
                                           @NotNull Project project,
                                           @NotNull UserDataHolder environment) {
        var output = new DAPInstallerOutput();
        environment.putUserData(KEY, output);
        // What it keeps isn't cleared with the console, which the installation clears after the check: the Installer
        // tab shows both
        return new ConsoleProvider(console, project) {
            @Override
            public void print(@Nullable String message, @NotNull ConsoleViewContentType contentType) {
                super.print(message, contentType);
                if (message != null) {
                    // As the console got it
                    synchronized (output.printed) {
                        output.printed.add(new Printed(message + "\n", contentType));
                    }
                }
            }
        };
    }

    /**
     * Returns what the installer printed at the start of the debug session of the given execution environment, or null
     * if no installation ran then, as when the debug adapter was installed already.
     *
     * @param environment the execution environment of the debug session.
     * @return what the installer printed at the start of the debug session, or null.
     */
    static @Nullable DAPInstallerOutput of(@NotNull UserDataHolder environment) {
        return environment.getUserData(KEY);
    }

    /**
     * Prints what the installer printed into the console of the given {@code Installer} tab.
     *
     * @param installerPanel the panel of the {@code Installer} tab.
     */
    void printTo(@NotNull InstallerPanel installerPanel) {
        List<Printed> snapshot;
        synchronized (printed) {
            snapshot = List.copyOf(printed);
        }
        // Outside the lock: the console of the tab can take its time
        snapshot.forEach(text -> installerPanel.print(text.text(), text.contentType()));
    }
}

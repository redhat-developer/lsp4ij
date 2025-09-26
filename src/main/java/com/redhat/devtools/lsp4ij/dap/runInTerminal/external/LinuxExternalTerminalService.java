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
package com.redhat.devtools.lsp4ij.dap.runInTerminal.external;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.redhat.devtools.lsp4ij.internal.ResponseErrorExceptionWrapper;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * This class code is the vscode TypeScript translate from
 * <a href="https://github.com/microsoft/vscode/blob/62093721f21b6b178ea92d9f6c9da74c6afdeaf5/src/vs/platform/externalTerminal/node/externalTerminalService.ts#L234">LinuxExternalTerminalService</a>
 */
public class LinuxExternalTerminalService extends ExternalTerminalService {

    // List of common Linux terminals in the same order VS Code tries them
    private static final String[] LINUX_TERMINALS = new String[]{
            "gnome-terminal",
            "konsole",
            "xfce4-terminal",
            "xterm",
            "lxterminal",
            "mate-terminal",
            "terminator",
            "tilix",
            "deepin-terminal",
            "kitty",
            "alacritty",
            "x-terminal-emulator"
    };

    @Override
    public boolean isApplicable() {
        return SystemInfo.isLinux;
    }

    /**
     * Executes the {@code runInTerminal} request in an external Windows terminal.
     *
     * @param args    the DAP {@link RunInTerminalRequestArguments} containing command, environment and working directory.
     * @param project the IntelliJ {@link Project} in which this terminal is being launched
     * @return a {@link CompletableFuture} that completes with the spawned process.
     */
    @Override
    protected CompletableFuture<Void> doRunInTerminal(@NotNull RunInTerminalRequestArguments args,
                                                      @NotNull Project project) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.supplyAsync(() -> {
            // Configure the process builder with working directory and environment variables.
            ProcessBuilder pb = createProcessBuilder(args, LinuxExternalTerminalService::createCommands);
            return startProcess(pb, 0);
        }).handle((process, error) -> {
            if (process == null || error != null) {
                if (error instanceof ResponseErrorException) {
                    result.completeExceptionally(error);
                } else {
                    result.completeExceptionally(new ResponseErrorExceptionWrapper(error));
                }
            } else {
                process.onExit().thenRun(() -> {
                    if (process.exitValue() == 0) {
                        result.complete(null);
                    } else {
                        result.completeExceptionally(new ResponseErrorExceptionWrapper("Linux terminal failed with exit code " + process.exitValue()));
                    }
                });
            }
            return null;
        });
        return result;
    }

    private static @NotNull List<String> createCommands(@NotNull RunInTerminalRequestArguments args) {
        String terminal = findAvailableTerminal();
        if (terminal == null) {
            throw new ResponseErrorExceptionWrapper("No supported Linux terminal found in PATH");
        }

        List<String> termArgs = new ArrayList<>();
        if (terminal.contains("gnome-terminal")) {
            termArgs.add("-x");
        } else {
            termArgs.add("-e");
        }

        termArgs.add("bash");
        termArgs.add("-c");

        String bashCommand = quote(args.getArgs()) + "; echo; read -p \""
                + "Press any key to continue..." + "\" -n1;";
        // wrapping argument in two sets of ' because node is so "friendly" that it removes one set...
        termArgs.add("''" + bashCommand + "''");
        termArgs.add(0, terminal);
        return termArgs;
    }

    /**
     * Finds the first available terminal from the list in the PATH.
     */
    private static String findAvailableTerminal() {
        for (String term : LINUX_TERMINALS) {
            if (isExecutableInPath(term)) {
                return term;
            }
        }
        return null;
    }

    /**
     * Checks if the given executable is available in the PATH using 'which'.
     */
    private static boolean isExecutableInPath(String exec) {
        try {
            Process p = new ProcessBuilder("which", exec).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Quote args if necessary and combine into a space separated string.
     */
    private static String quote(String[] args) {
        StringBuilder r = new StringBuilder();
        for (String a : args) {
            if (a.contains(" ")) {
                r.append('"').append(a).append('"');
            } else {
                r.append(a);
            }
            r.append(' ');
        }
        return r.toString().trim();
    }

}

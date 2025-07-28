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
package com.redhat.devtools.lsp4ij.dap.client.runInterminal.external;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
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

    @Override
    protected CompletableFuture<Integer> doRunInTerminal(@NotNull RunInTerminalRequestArguments args,
                                                         @NotNull Project project) {

        // TODO : reimplement this class!!!

        String terminal = findAvailableTerminal();
        if (terminal == null) {
            CompletableFuture<Integer> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("No supported Linux terminal found in PATH"));
            return failed;
        }

        // Build the shell command like VS Code:
        // bash -c "cd /cwd && <command args> ; exec bash"
        String joinedArgs = String.join(" ", escapeArgs(args.getArgs()));
        String fullCommand = "cd " + escapeArg(args.getCwd()) + " && " + joinedArgs + " ; exec bash";

        List<String> cmd = new ArrayList<>();
        cmd.add(terminal);
        cmd.add("-e");
        cmd.add("bash");
        cmd.add("-c");
        cmd.add(fullCommand);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(args.getCwd()));

        // Filter out null values in the environment variables
        Map<String, String> env = args.getEnv() != null ? args.getEnv() : new HashMap<>();
        env.entrySet().removeIf(e -> e.getValue() == null);
        pb.environment().putAll(env);

        return runProcessAsyncWithExitCode(pb);
    }

    /**
     * Run the process asynchronously and complete the future with the exit code.
     */
    private CompletableFuture<Integer> runProcessAsyncWithExitCode(ProcessBuilder pb) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        try {
            Process p = pb.start();
            p.onExit().thenAccept(pr -> future.complete(pr.exitValue()));
        } catch (IOException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Finds the first available terminal from the list in the PATH.
     */
    private String findAvailableTerminal() {
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
    private boolean isExecutableInPath(String exec) {
        try {
            Process p = new ProcessBuilder("which", exec).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Escape arguments for safe usage inside bash -c.
     */
    private List<String> escapeArgs(String[] args) {
        List<String> escaped = new ArrayList<>();
        for (String arg : args) {
            escaped.add(escapeArg(arg));
        }
        return escaped;
    }

    /**
     * Escape a single argument by wrapping it in single quotes and handling embedded quotes.
     */
    private String escapeArg(String arg) {
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }
}

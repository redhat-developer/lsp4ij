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
 * <a href="https://github.com/microsoft/vscode/blob/62093721f21b6b178ea92d9f6c9da74c6afdeaf5/src/vs/platform/externalTerminal/node/externalTerminalService.ts#L144">MacExternalTerminalService</a>
 */
public class MacExternalTerminalService extends ExternalTerminalService {

    @Override
    public boolean isApplicable() {
        return SystemInfo.isMac;
    }

    @Override
    protected CompletableFuture<Integer> doRunInTerminal(@NotNull RunInTerminalRequestArguments args,
                                                         @NotNull Project project) {

        // TODO : reimplement this class!!!

        // Build AppleScript command like VS Code does
        String joinedArgs = String.join(" ", escapeArgs(args.getArgs()));
        String fullCommand = "cd " + escapeArg(args.getCwd()) + " && " + joinedArgs;

        // AppleScript to tell Terminal to open a new window and run the command
        String appleScript = "tell application \"Terminal\"\n" +
                "do script \"" + fullCommand + "\"\n" +
                "activate\n" +
                "end tell";

        List<String> cmd = Arrays.asList("osascript", "-e", appleScript);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(args.getCwd()));

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
     * Escape arguments for safe usage inside AppleScript/Terminal.
     */
    private List<String> escapeArgs(String[] args) {
        List<String> escaped = new ArrayList<>();
        for (String arg : args) {
            escaped.add(escapeArg(arg));
        }
        return escaped;
    }

    /**
     * Escape a single argument for safe bash usage.
     */
    private String escapeArg(String arg) {
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }
}

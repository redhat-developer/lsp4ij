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
package com.redhat.devtools.lsp4ij.dap.client.runInterminal;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.Alarm;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArgumentsKind;
import org.eclipse.lsp4j.debug.RunInTerminalResponse;
import org.eclipse.sisu.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manages Debug Adapter Protocol (DAP) {@code RunInTerminal} requests,
 * supporting integrated terminal execution within IntelliJ IDEA.
 */
public class RunInTerminalManager {

    private final @NotNull Project project;

    public RunInTerminalManager(@NotNull Project project) {
        this.project = project;
    }

    public static RunInTerminalManager getInstance(@NotNull Project project) {
        return project.getService(RunInTerminalManager.class);
    }

    /**
     * Executes a command in either an integrated or external terminal based on the request kind.
     *
     * @param args DAP {@link RunInTerminalRequestArguments}
     * @return a {@link CompletableFuture} resolving to the {@link RunInTerminalResponse}
     */
    public CompletableFuture<RunInTerminalResponse> runInTerminal(RunInTerminalRequestArguments args) {
        if (shouldUseIntegratedTerminal(args.getKind())) {
            return runInIntegratedTerminal(args);
        }
        return runInExternalTerminal(args);
    }

    /**
     * Runs a command in IntelliJ's integrated terminal.
     */
    private CompletableFuture<RunInTerminalResponse> runInIntegratedTerminal(RunInTerminalRequestArguments args) {
        CompletableFuture<RunInTerminalResponse> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                TerminalToolWindowManager tm = TerminalToolWindowManager.getInstance(project);

                String title = args.getTitle();
                String workingDirectory = args.getCwd();
                ShellTerminalWidget shellWidget = tm.createLocalShellWidget(workingDirectory, title);

                String command = prepareCommandWithEnv(args);

                shellWidget.executeCommand(command);

                Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
                Runnable checkProcess = new Runnable() {
                    @Override
                    public void run() {
                        var tty = shellWidget.getProcessTtyConnector();
                        if (tty != null) {
                            RunInTerminalResponse resp = new RunInTerminalResponse();
                            resp.setProcessId((int) tty.getProcess().pid());
                            future.complete(resp);
                        } else if (!future.isDone()) {
                            alarm.addRequest(this, 100); // recheck in 100ms
                        }
                    }
                };
                alarm.addRequest(checkProcess, 100);

            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Prepares a full command line with environment variable injection,
     * adapting to the current OS and shell syntax.
     * <p>
     * - PowerShell: ${env:VAR}='value'; command
     * - cmd.exe: set VAR=value && set VAR2=value && command
     * - Unix shells: VAR='value' VAR2='value' command
     */
    private String prepareCommandWithEnv(RunInTerminalRequestArguments args) {
        String command = formatCommandArguments(args.getArgs());

        if (args.getEnv() == null || args.getEnv().isEmpty()) {
            return command;
        }

        boolean isWindows = SystemInfo.isWindows;
        boolean isPowerShell = isWindows && isPowerShellEnvironment();
        StringBuilder envCmd = new StringBuilder();

        if (isPowerShell) {
            args.getEnv().forEach((k, v) -> {
                envCmd.append("${env:").append(k).append("}='")
                        .append(v.replace("'", "''")).append("'; ");
            });
            envCmd.append(command);
        } else if (isWindows) {
            args.getEnv().forEach((k, v) -> {
                envCmd.append("set ").append(k).append("=").append(v).append(" && ");
            });
            envCmd.append(command);
        } else {
            args.getEnv().forEach((k, v) -> {
                envCmd.append(k).append("='")
                        .append(v.replace("'", "'\"'\"'")).append("' ");
            });
            envCmd.append(command);
        }

        return envCmd.toString();
    }

    /**
     * Formats a command line by quoting and escaping arguments for the current OS/shell.
     * <p>
     * - PowerShell: adds call operator '&' and quotes paths with spaces
     * - cmd.exe: escapes spaces with '^'
     * - Unix: wraps arguments with spaces in single quotes
     */
    private String formatCommandArguments(String[] args) {
        boolean isWindows = SystemInfo.isWindows;
        boolean isPowerShell = isWindows && isPowerShellEnvironment();

        if (args.length == 0) {
            return "";
        }

        if (isPowerShell) {
            String first = args[0];
            if (first.contains(" ")) {
                first = "& \"" + first + "\"";
            } else {
                first = "& " + first;
            }

            String rest = Arrays.stream(args, 1, args.length)
                    .map(arg -> arg.contains(" ") ? "\"" + arg + "\"" : arg)
                    .collect(Collectors.joining(" "));

            return first + (rest.isEmpty() ? "" : " " + rest);
        } else if (isWindows) {
            return Arrays.stream(args)
                    .map(arg -> arg.contains(" ") ? arg.replace(" ", "^ ") : arg)
                    .collect(Collectors.joining(" "));
        } else {
            return Arrays.stream(args)
                    .map(arg -> arg.contains(" ") ? "'" + arg.replace("'", "'\"'\"'") + "'" : arg)
                    .collect(Collectors.joining(" "));
        }
    }

    /**
     * Detects whether the current environment is running PowerShell on Windows.
     */
    private boolean isPowerShellEnvironment() {
        String psModule = System.getenv("PSModulePath");
        return psModule != null && psModule.contains("PowerShell");
    }

    /**
     * Placeholder for running in an external terminal.
     */
    private CompletableFuture<RunInTerminalResponse> runInExternalTerminal(RunInTerminalRequestArguments args) {
        CompletableFuture<RunInTerminalResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException("runInExternalTerminal not supported"));
        return future;
    }

    /**
     * Checks if the request should use the integrated terminal.
     */
    private boolean shouldUseIntegratedTerminal(@Nullable RunInTerminalRequestArgumentsKind kind) {
        return kind == RunInTerminalRequestArgumentsKind.INTEGRATED && isTerminalPluginInstalled();
    }

    /**
     * Verifies if the IntelliJ terminal plugin is installed.
     */
    private static boolean isTerminalPluginInstalled() {
        PluginId pluginId = PluginId.getId("org.jetbrains.plugins.terminal");
        return PluginManagerCore.getPlugin(pluginId) != null;
    }
}

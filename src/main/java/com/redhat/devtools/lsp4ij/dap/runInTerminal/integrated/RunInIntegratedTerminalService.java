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
package com.redhat.devtools.lsp4ij.dap.runInTerminal.integrated;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.runInTerminal.RunInTerminalService;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.eclipse.lsp4j.debug.RunInTerminalResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Integrated terminal implementation of {@link RunInTerminalService} for Debug Adapter Protocol (DAP).
 * <p>
 * This service allows running commands inside IntelliJ integrated terminals, supports injecting
 * environment variables, and formats commands depending on the OS and shell.
 * </p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *     <li>Creates a local shell widget in the integrated terminal window.</li>
 *     <li>Supports PowerShell, cmd.exe, and Unix-style shells.</li>
 *     <li>Injects environment variables with proper shell syntax.</li>
 *     <li>Returns the process ID of the spawned terminal via {@link RunInTerminalResponse}.</li>
 *     <li>Ensures that terminals with the same title are not shared between clients.</li>
 * </ul>
 *
 * <p>
 * This service requires the IntelliJ Terminal plugin to be installed. {@link #isApplicable()} returns
 * {@code false} if the plugin is missing.
 * </p>
 */
public class RunInIntegratedTerminalService implements RunInTerminalService {

    /**
     * Map of terminals currently assigned to clients, keyed by terminal title.
     */
    private static final Map<String, ClientTerminal> clientTerminals = new ConcurrentHashMap<>();

    /**
     * Acquires a terminal for the given client.
     * <p>
     * If a terminal with the requested title is already assigned to this client, it is reused.
     * Otherwise, it searches for an existing unassigned terminal with the same title in IntelliJ.
     * If none is found, a new terminal is created.
     *
     * @param workingDirectory the working directory for the terminal, or null for default
     * @param title            the terminal title, may be null
     * @param client           the DAP client requesting the terminal
     * @return a {@link ShellTerminalWidget} ready for command execution
     */
    private static @NotNull ShellTerminalWidget acquireTerminal(@Nullable String workingDirectory,
                                                                @Nullable String title,
                                                                @NotNull DAPClient client) {
        var project = client.getProject();
        TerminalToolWindowManager tm = TerminalToolWindowManager.getInstance(project);

        // 1) Terminal with same title used by another client
        ClientTerminal assigned = StringUtils.isNotBlank(title) ? clientTerminals.get(title) : null;
        if (assigned != null && !assigned.client.equals(client)) {
            return createNewTerminal(workingDirectory, title, client);
        }

        // 2) Look for existing unassigned terminal in IntelliJ
        for (TerminalWidget terminalWidget : tm.getTerminalWidgets()) {
            if (title != null && title.equals(terminalWidget.getTerminalTitle().getDefaultTitle())) {
                boolean used = clientTerminals.values().stream()
                        .anyMatch(ct -> ct.terminalWidget == terminalWidget);
                if (!used) {
                    JBTerminalWidget jbTerminalWidget = JBTerminalWidget.asJediTermWidget(terminalWidget);
                    if (jbTerminalWidget instanceof ShellTerminalWidget shellTerminalWidget) {
                        clientTerminals.put(title, new ClientTerminal(title, shellTerminalWidget, client));
                        return shellTerminalWidget;
                    }
                }
            }
        }

        // 3) Create a new terminal if nothing found
        return createNewTerminal(workingDirectory, title, client);
    }

    /**
     * Helper method to create a new terminal and register it for the client.
     */
    private static @NotNull ShellTerminalWidget createNewTerminal(@Nullable String workingDirectory,
                                                                  @Nullable String title,
                                                                  @NotNull DAPClient client) {
        var project = client.getProject();
        TerminalToolWindowManager tm = TerminalToolWindowManager.getInstance(project);
        ShellTerminalWidget shellTerminalWidget = tm.createLocalShellWidget(workingDirectory, title);
        if (StringUtils.isNotBlank(title)) {
            // Cache the terminal widget to reuse if title is filled
            clientTerminals.put(title, new ClientTerminal(title, shellTerminalWidget, client));
        }
        return shellTerminalWidget;
    }


    /**
     * Checks if the IntelliJ Terminal plugin is installed.
     *
     * @return true if the terminal plugin is installed, false otherwise
     */
    @Override
    public boolean isApplicable() {
        PluginId pluginId = PluginId.getId("org.jetbrains.plugins.terminal");
        return PluginManagerCore.getPlugin(pluginId) != null;
    }

    /**
     * Executes a command in an integrated terminal for the given client.
     *
     * @param args   the request arguments containing command, working directory, environment, and title
     * @param client the DAP client executing the command
     * @return a {@link CompletableFuture} resolving to {@link RunInTerminalResponse} with the process ID
     */
    @Override
    public CompletableFuture<RunInTerminalResponse> runInTerminal(@NotNull RunInTerminalRequestArguments args,
                                                                  @NotNull DAPClient client) {
        CompletableFuture<RunInTerminalResponse> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                var project = client.getProject();
                String title = args.getTitle();
                String workingDirectory = StringUtils.isBlank(args.getCwd()) ? null : args.getCwd();

                ShellTerminalWidget shellWidget = acquireTerminal(workingDirectory, title, client);

                String command = prepareCommandWithEnv(args);
                shellWidget.executeCommand(command);

                // Poll until the terminal process PID becomes available
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
                            alarm.addRequest(this, 100);
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
     * Prepares the full command line with environment variable injection according to the current OS and shell.
     *
     * @param args the request arguments containing command and environment variables
     * @return the formatted command string
     */
    private String prepareCommandWithEnv(@NotNull RunInTerminalRequestArguments args) {
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
     * Formats command-line arguments for the active OS and shell.
     *
     * @param args array of command arguments
     * @return properly quoted and escaped command string
     */
    private String formatCommandArguments(String[] args) {
        boolean isWindows = SystemInfo.isWindows;
        boolean isPowerShell = isWindows && isPowerShellEnvironment();

        if (args.length == 0) return "";

        if (isPowerShell) {
            String first = args[0];
            first = first.contains(" ") ? "& \"" + first + "\"" : "& " + first;

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
     *
     * @return true if PowerShell is detected, false otherwise
     */
    private boolean isPowerShellEnvironment() {
        String psModule = System.getenv("PSModulePath");
        return psModule != null && psModule.contains("PowerShell");
    }

    @Override
    public void releaseClientTerminals(@NotNull DAPClient client) {
        clientTerminals.values().stream()
                .filter(ct -> ct.client.equals(client))
                .toList()
                .forEach(ct -> clientTerminals.remove(ct.title, ct));
    }

    /**
     * Represents a terminal currently assigned to a DAP client.
     */
    private record ClientTerminal(String title, ShellTerminalWidget terminalWidget, DAPClient client) {
    }
}


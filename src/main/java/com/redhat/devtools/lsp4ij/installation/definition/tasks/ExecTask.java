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
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Key;
import com.intellij.util.EnvironmentUtil;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * <pre>
 * " exec": {
 *     "name": "Check typescript-language-server",*
 *     "command": {
 *       "windows": "where typescript-language-server",
 *       "default": "which typescript-language-server"
 *     },
 *     "onFail": { ...
 *     }
 * }
 * </pre>
 */
public class ExecTask extends InstallerTask {

    private static final int DEFAULT_TIMEOUT = 2000;

    private final @NotNull List<String> command;
    private final @Nullable Integer timeout;
    private final @Nullable String workingDir;
    private final boolean ignoreStderr;

    public ExecTask(@Nullable String id,
                    @Nullable String name,
                    @Nullable InstallerTask onFail,
                    @Nullable InstallerTask onSuccess,
                    @NotNull List<String> command,
                    @Nullable String workingDir,
                    @Nullable Integer timeout,
                    boolean ignoreStderr,
                    @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        super(id, name, onFail, onSuccess, serverInstallerDeclaration);
        this.command = command;
        this.workingDir = workingDir;
        this.timeout = timeout;
        this.ignoreStderr = ignoreStderr;

    }

    private static List<String> resolveServerCommand(@NotNull List<String> command,
                                                     @NotNull InstallerContext context) {
        if (command.size() == 1 && command.get(0).equals("${server.command}")) {
            // Specific case: resolve "${server.command}"
            // create an array string of the resolved command
            String resolved = context.resolveValues(command.get(0));
            return CommandUtils.createCommands(resolved);
        }
        return command;
    }

    @Override
    public boolean run(@NotNull InstallerContext context) {
        CapturingProcessHandler handler = null;
        try {
            @Nullable var project = context.getProject();
            List<String> command = resolveServerCommand(this.command, context);
            List<String> resolvedCommand = command
                    .stream()
                    .map(args -> CommandUtils.resolveCommandLine(args, project))
                    .toList();
            context.print("> " + String.join(" ", resolvedCommand));

            GeneralCommandLine cmdLine = new GeneralCommandLine(resolvedCommand)
                    .withEnvironment(EnvironmentUtil.getEnvironmentMap());
            Path workingDir = getWorkingDir(this.workingDir, context);
            if (workingDir != null) {
                if (!Files.exists(workingDir)) {
                    Files.createDirectories(workingDir);
                }
                cmdLine = cmdLine
                        .withWorkDirectory(workingDir.toFile());
            }
            cmdLine.setCharset(java.nio.charset.StandardCharsets.UTF_8);

            handler = new CapturingProcessHandler(cmdLine);
            handler.addProcessListener(new ProcessAdapter() {
                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                    if (outputType == ProcessOutputType.STDERR) {
                        context.printError(event.getText());
                    } else {
                        context.print(event.getText());
                    }
                }
            });

            ProcessOutput result = timeout != null ? handler.runProcess(timeout) : handler.runProcess();
            if (result.isCancelled()) {
                throw new ProcessCanceledException();
            }
            if (result.isTimeout()) {
                return true;
            }
            return result.getExitCode() == 0 && (ignoreStderr || result.getStderr().isEmpty());
        } catch (Exception e) {
            context.printError("", e);
            return false;
        } finally {
            if (handler != null) {
                handler.destroyProcess();
            }
        }
    }

    private Path getWorkingDir(@Nullable String workingDir,
                               @NotNull InstallerContext context) {
        @Nullable var project = context.getProject();
        if (workingDir != null) {
            String resolved = CommandUtils.resolveCommandLine(context.resolveValues(workingDir), project);
            if (resolved != null) {
                return Paths.get(resolved);
            }
        }
        if (project != null) {
            var projectDir = ProjectUtil.guessProjectDir(project);
            if (projectDir != null) {
                return projectDir.toNioPath();
            }
        }
        return null;
    }
}

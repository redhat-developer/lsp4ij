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
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.util.EnvironmentUtil;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * <pre>
 " exec": {
 *     "name": "Check typescript-language-server",*
 *     "command": {
 *       "windows": "where typescript-language-server",
 *       "default": "which typescript-language-server"
 *     },
 *     "onFail": { ...
 *     }
 * }
 * </pre>
 *
 */
public class ExecTask extends InstallerTask {

    private final @NotNull List<String> command;

    public ExecTask(@Nullable String id,
                    @Nullable String name,
                    @Nullable InstallerTask onFail,
                    @Nullable InstallerTask onSuccess,
                    @NotNull List<String> command,
                    @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        super(id, name, onFail, onSuccess, serverInstallerDeclaration);
        this.command = command;
    }

    @Override
    public boolean run(@NotNull InstallerContext context) {
        try {
            context.print("> " + String.join(" ", command));

            GeneralCommandLine cmdLine = new GeneralCommandLine(command)
                    .withEnvironment(EnvironmentUtil.getEnvironmentMap());
            cmdLine.setCharset(java.nio.charset.StandardCharsets.UTF_8);

            CapturingProcessHandler handler = new CapturingProcessHandler(cmdLine);
            ProcessOutput output = handler.runProcess();

            if (output.getExitCode() != 0) {
                context.printError(output.getStderr());
                return false;
            }

            context.print(output.getStdout());
            return true;
        } catch (Exception e) {
            context.printError("", e);
            return false;
        }
    }
}

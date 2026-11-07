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
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A command line whose executable, when it is a name, is looked up as its process starts, in the PATH of the
 * environment that the process gets in its working directory, so that the tool which the directory provides, as with
 * mise or direnv, is the one that starts.
 * <p>
 * Since 2025.1, the platform lets plugins customize the environment of a process for its working directory
 * ({@code CommandLineEnvCustomizer}) when the process starts, but it looks up the executable before that, in the PATH
 * given to the command line only. This command line looks it up again in the environment that the platform has
 * customized for the process, so the customizers run once for each start, and the executable is the one of the
 * environment that the process gets.
 * <p>
 * A command line without a working directory, or whose executable is a path, starts as the platform starts it.
 */
@ApiStatus.Internal
public class WorkingDirectoryCommandLine extends GeneralCommandLine {

    public WorkingDirectoryCommandLine(@NotNull List<String> command) {
        super(command);
    }

    @Override
    protected @NotNull Process createProcess(@NotNull ProcessBuilder processBuilder) throws IOException {
        String name = CommandUtils.getExecutable(this);
        if (getWorkDirectory() != null && name != null) {
            // The environment of the process, which the platform has customized for its working directory
            File executable = CommandUtils.findExecutable(name, processBuilder.environment());
            if (executable != null) {
                setExePath(executable.getPath());
                processBuilder.command(prepareCommandLine(executable.getPath(), getParametersList().getList(),
                        Platform.current()));
            }
        }
        return super.createProcess(processBuilder);
    }
}

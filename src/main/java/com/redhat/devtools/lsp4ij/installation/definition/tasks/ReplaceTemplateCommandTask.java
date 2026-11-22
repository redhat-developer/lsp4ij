/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Installer task that replaces the command of the template with the given command, as {@code configureServer} does,
 * but only while the server has exactly the command of the template, compared as written: so the check of an
 * installer, which also runs from <i>Check installation</i> in the settings, gives a server created from the template
 * the command of the file that it found, {@code ${check.file}}, and leaves a command that the user or an earlier
 * installation gave the server as it is. When the server has another command, the task leaves it and succeeds. On an
 * OS without a {@code templateCommand}, it never changes the command.
 * <p>
 * It is a task of its own, rather than an option of {@code configureServer}, so that an earlier version of LSP4IJ,
 * which skips a task type it doesn't know, doesn't give the server the command with {@code ${check.file}} unresolved.
 *
 * <pre>
 * "replaceTemplateCommand": {
 *   "name": "Configure clangd server command",
 *   "templateCommand": "clangd",
 *   "command": "\"${check.file}\""
 * }
 * </pre>
 */
public class ReplaceTemplateCommandTask extends ConfigureServerTask {

    private final @Nullable String templateCommand;

    public ReplaceTemplateCommandTask(@Nullable String id,
                                      @Nullable String name,
                                      @Nullable InstallerTask onFail,
                                      @Nullable InstallerTask onSuccess,
                                      @Nullable String templateCommand,
                                      @NotNull String command,
                                      @NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        super(id, name, onFail, onSuccess, command, serverInstallerDescriptor);
        this.templateCommand = templateCommand;
    }

    @Override
    public boolean run(@NotNull InstallerContext context) {
        var commandLineUpdater = context.getCommandLineUpdater();
        String currentCommand = commandLineUpdater != null ? commandLineUpdater.getCommandLine() : null;
        if (templateCommand == null || !templateCommand.equals(currentCommand)) {
            context.print("Server command kept, since it is not the command of the template '" + templateCommand
                    + "': " + currentCommand);
            return true;
        }
        return super.run(context);
    }
}

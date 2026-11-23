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

import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <pre>
 " "configureServer": {
 *   "name": "Configure jdt-ls server command",
 *   "command": "\"${output.dir}/${output.file.name}\" -configuration \"USER_HOME$/.cache/jdtls\" -data \"$PROJECT_DIR$/jdtls-data\"",
 *   "update": true
 * }
 * </pre>
 * <p>
 * A command with {@code ${check.file}} that no {@code fileExists} task found a file for is an error, and leaves the
 * command of the server as it is.
 */
public class ConfigureServerTask extends InstallerTask {

    private static final String CHECK_FILE = "${" + FileExistsTask.CHECK_FILE_PROPERTY + "}";

    private final @NotNull String command;

    public ConfigureServerTask(@Nullable String id,
                               @Nullable String name,
                               @Nullable InstallerTask onFail,
                               @Nullable InstallerTask onSuccess,
                               @NotNull String command,
                               @NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        super(id, name, onFail, onSuccess, serverInstallerDescriptor);
        this.command = command;
    }

    @Override
    public boolean run(@NotNull InstallerContext context) {
        try {
            String resolvedCommand = context.resolveValues(command);
            if (resolvedCommand.contains(CHECK_FILE)) {
                context.printError("The server command '" + command + "' has " + CHECK_FILE
                        + ", but no fileExists task found a file for it", true);
                return false;
            }
            context.print("Start server command=" + resolvedCommand);
            var commandLineUpdater = context.getCommandLineUpdater();
            if (commandLineUpdater != null) {
                commandLineUpdater.setCommandLine(resolvedCommand);
            }
            context.addInfoMessage("Server command has been updated with '" + resolvedCommand + "'");
            return true;
        } catch (Exception e) {
            context.printError("", e);
            return false;
        }
    }

}

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
 *
 */
public class ConfigureServerTask extends InstallerTask {

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
            String resolvedCommand = command;
            var keys = context.getPropertyKeys();
            for(var key : keys) {
                String value = context.getProperty(key);
                if (value != null) {
                    resolvedCommand = resolvedCommand.replace("${" + key + "}", value);
                }
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

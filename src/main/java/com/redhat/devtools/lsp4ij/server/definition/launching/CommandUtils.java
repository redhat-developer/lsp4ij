/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition.launching;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Command utilities.
 */
public class CommandUtils {

    /**
     * Returns the commands to execute with {@link Process} from the given commandline.
     *
     * @param commandLine the command line.
     *
     * @return the commands to execute with {@link Process} from the given commandline.
     */
    @NotNull
    public static List<String> createCommands(@NotNull String commandLine) {
        List<String> commands = new ArrayList<>();
        StringBuilder commandPart = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            switch(c) {
                case '"':
                    inString = !inString;
                    break;
                case ' ':
                    if (inString) {
                        commandPart.append(c);
                    } else {
                        addArg(commandPart, commands);
                        commandPart.setLength(0);
                    }
                    break;
                default:
                    commandPart.append(c);
                    break;
            }
        }
        if (commandPart.length() > 0) {
            addArg(commandPart, commands);
            commandPart.setLength(0);
        }
        return commands;
    }

    private static void addArg(StringBuilder commandPart, List<String> commands) {
        String arg = commandPart.toString().trim();
        if (!arg.isEmpty()) {
            commands.add(arg);
        }
    }

}

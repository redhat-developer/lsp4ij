/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link ProcessStreamConnectionProvider} implementation to start a the language server with a
 * process command defined by the user.
 */
public class UserDefinedStreamConnectionProvider extends ProcessStreamConnectionProvider {

    private final @NotNull UserDefinedLanguageServerDefinition serverDefinition;

    public UserDefinedStreamConnectionProvider(@NotNull String commandLine,
                                               @NotNull Map<String, String> userEnvironmentVariables,
                                               boolean includeSystemEnvironmentVariables,
                                               @NotNull UserDefinedLanguageServerDefinition serverDefinition) {
        super.setCommands(createCommands(commandLine));
        super.setUserEnvironmentVariables(userEnvironmentVariables);
        super.setIncludeSystemEnvironmentVariables(includeSystemEnvironmentVariables);
        this.serverDefinition = serverDefinition;
    }

    private List<String> createCommands(String commandLine) {
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
                        commands.add(commandPart.toString());
                        commandPart.setLength(0);
                    }
                    break;
                default:
                    commandPart.append(c);
                    break;
            }
        }
        if (commandPart.length() > 0) {
            commands.add(commandPart.toString());
            commandPart.setLength(0);
        }
        return commands;
    }

    @Override
    public Object getInitializationOptions(URI rootUri) {
        return serverDefinition.getLanguageServerInitializationOptions();
    }
}

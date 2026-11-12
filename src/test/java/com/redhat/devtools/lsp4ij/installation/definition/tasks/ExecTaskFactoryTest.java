/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that an installer splits a command given as a string as LSP4IJ splits the command which starts a language
 * server.
 */
public class ExecTaskFactoryTest {

    @Test
    void anArgumentInDoubleQuotesKeepsItsSpaces() {
        assertEquals(List.of("gem", "install", "--install-dir", "/Users/me/My Gems", "ruby-lsp"),
                command("{ \"command\": \"gem install --install-dir \\\"/Users/me/My Gems\\\" ruby-lsp\" }"));
    }

    @Test
    void severalSpacesMakeNoEmptyArgument() {
        assertEquals(List.of("npm", "install", "typescript"), command("{ \"command\": \"npm  install typescript\" }"));
    }

    @Test
    void theCommandOfTheOperatingSystemIsSplitToo() {
        assertEquals(List.of("gem", "install", "debug"),
                command("{ \"command\": { \"windows\": \"gem install debug\", \"default\": \"gem install debug\" } }"));
    }

    @Test
    void aCommandGivenAsAnArrayIsTakenAsItIs() {
        assertEquals(List.of("npm", "install", "-g", "a b"),
                command("{ \"command\": { \"default\": [\"npm\", \"install\", \"-g\", \"a b\"] } }"));
    }

    private static @NotNull List<String> command(@NotNull String json) {
        return ExecTaskFactory.getCommand(JsonParser.parseString(json).getAsJsonObject());
    }
}

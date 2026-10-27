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

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for {@link CommandUtils}.
 */
public class CommandUtilsTest {

    @Test
    public void testCommand() {
        assertCommand("foo bar",
                "foo",
                "bar"
        );
    }

    @Test
    public void testCommandWithSpaces() {
        assertCommand("foo",
                "foo");
        assertCommand("foo bar",
                "foo",
                "bar");
        assertCommand("foo   bar",
                "foo",
                "bar");
        assertCommand("   foo   bar   ",
                "foo",
                "bar");
    }

    @Test
    public void testGolpsCommand() {
        assertCommand("sh -c gopls -mode=stdio",
                "sh",
                "-c",
                "gopls",
                "-mode=stdio"
        );
    }

    @Test
    public void testGolpsCommandWithSpaces() {
        assertCommand("    sh    -c    gopls    -mode=stdio",
                "sh",
                "-c",
                "gopls",
                "-mode=stdio"
        );
    }

    @Test
    public void testCommandWithQuote() {
        assertCommand("    \"C:\\Users\\USERNAME\\A FOLDER WITH SPACE\\node_modules/.bin/typescript-language-server.cmd\"    --stdio   ",
                "C:\\Users\\USERNAME\\A FOLDER WITH SPACE\\node_modules/.bin/typescript-language-server.cmd",
                "--stdio"
        );
    }

    @Test
    public void testWindowsEnvironmentMergeIsCaseInsensitive() {
        Map<String, String> parentEnvironment =
                Map.of("Path", "parent-path");

        Map<String, String> configuredEnvironment =
                Map.of("PATH", "configured-path");

        Map<String, String> result =
                CommandUtils.mergeEnvironmentVariables(
                        parentEnvironment,
                        configuredEnvironment,
                        true
                );

        Assert.assertEquals(1, result.size());
        Assert.assertEquals("configured-path", result.get("Path"));
        Assert.assertEquals("configured-path", result.get("PATH"));
    }

    @Test
    public void testNonWindowsEnvironmentMergeIsCaseSensitive() {
        Map<String, String> parentEnvironment =
                Map.of("Path", "parent-path");

        Map<String, String> configuredEnvironment =
                Map.of("PATH", "configured-path");

        Map<String, String> result =
                CommandUtils.mergeEnvironmentVariables(
                        parentEnvironment,
                        configuredEnvironment,
                        false
                );

        Assert.assertEquals(2, result.size());
        Assert.assertEquals("parent-path", result.get("Path"));
        Assert.assertEquals("configured-path", result.get("PATH"));
    }

    private static void assertCommand(String command, String... expected) {
        List<String> actual = CommandUtils.createCommands(command);
        Assert.assertArrayEquals(expected, actual.toArray());
    }
}

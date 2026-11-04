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

import com.intellij.execution.configurations.GeneralCommandLine;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link CommandUtils}.
 */
public class CommandUtilsTest {

    @After
    public void tearDown() {
        CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
    }

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
        // Parent defines variables with various casings
        Map<String, String> parentEnvironment = new HashMap<>();
        parentEnvironment.put("Path", "C:\\Windows\\System32");
        parentEnvironment.put("COMMON_VAR", "parent-value");
        parentEnvironment.put("MIXED_Case", "parent-mixed");

        // Configured (user) variables override parent variables using different casings
        Map<String, String> configuredEnvironment = new HashMap<>();
        configuredEnvironment.put("PATH", "C:\\custom\\bin;C:\\Windows\\System32"); // different case, should override Path
        configuredEnvironment.put("common_var", "user-override");                   // different case, should override COMMON_VAR
        configuredEnvironment.put("USER_ONLY", "user-val");

        Map<String, String> result = CommandUtils.mergeEnvironmentVariables(
                parentEnvironment,
                configuredEnvironment,
                true
        );

        // 1. Verify size: exactly 4 unique variables (Path/PATH, COMMON_VAR/common_var, MIXED_Case, USER_ONLY)
        Assert.assertEquals(4, result.size());

        // 2. Case-insensitive lookup: all case variations return the winning value
        Assert.assertEquals("C:\\custom\\bin;C:\\Windows\\System32", result.get("Path"));
        Assert.assertEquals("C:\\custom\\bin;C:\\Windows\\System32", result.get("PATH"));
        Assert.assertEquals("C:\\custom\\bin;C:\\Windows\\System32", result.get("path"));
        Assert.assertEquals("C:\\custom\\bin;C:\\Windows\\System32", result.get("pAtH"));

        Assert.assertEquals("user-override", result.get("COMMON_VAR"));
        Assert.assertEquals("user-override", result.get("common_var"));
        Assert.assertEquals("user-override", result.get("Common_Var"));

        Assert.assertEquals("parent-mixed", result.get("mixed_case"));
        Assert.assertEquals("parent-mixed", result.get("MIXED_CASE"));

        Assert.assertEquals("user-val", result.get("USER_ONLY"));
        Assert.assertEquals("user-val", result.get("user_only"));

        // 3. Verify correct precedence: user/configured variables must win over parent variables
        Assert.assertEquals("C:\\custom\\bin;C:\\Windows\\System32", result.get("Path"));
        Assert.assertEquals("user-override", result.get("COMMON_VAR"));

        // 4. Verify no duplicate variable names across cases in key set
        Set<String> lowerCaseKeys = new HashSet<>();
        for (String key : result.keySet()) {
            boolean isNew = lowerCaseKeys.add(key.toLowerCase(Locale.ROOT));
            Assert.assertTrue("Duplicate key detected with different casing: " + key, isNew);
        }

        // 5. Verify that when transferred to GeneralCommandLine environment, no duplicates occur
        GeneralCommandLine cmd = new GeneralCommandLine("echo", "test");
        cmd.getEnvironment().putAll(result);
        Assert.assertEquals(4, cmd.getEnvironment().size());
    }

    @Test
    public void testNonWindowsEnvironmentMergeIsCaseSensitive() {
        Map<String, String> parentEnvironment = Map.of("Path", "parent-path");
        Map<String, String> configuredEnvironment = Map.of("PATH", "configured-path");

        Map<String, String> result = CommandUtils.mergeEnvironmentVariables(
                parentEnvironment,
                configuredEnvironment,
                false
        );

        Assert.assertEquals(2, result.size());
        Assert.assertEquals("parent-path", result.get("Path"));
        Assert.assertEquals("configured-path", result.get("PATH"));
    }

    @Test
    public void testCreateCommandLineWithWorkingDirectory() {
        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", "C:/test/dir", Collections.emptyMap(), false);
        Assert.assertNotNull(cmd.getWorkDirectory());
        Assert.assertEquals(new File("C:/test/dir").getAbsolutePath(), cmd.getWorkDirectory().getAbsolutePath());
    }

    @Test
    public void testCreateCommandLineWithNullOrBlankWorkingDirectory() {
        GeneralCommandLine cmdNull = CommandUtils.createCommandLine("node server.js", null, Collections.emptyMap(), false);
        Assert.assertNull(cmdNull.getWorkDirectory());

        GeneralCommandLine cmdBlank = CommandUtils.createCommandLine("node server.js", "   ", Collections.emptyMap(), false);
        Assert.assertNull(cmdBlank.getWorkDirectory());
    }

    public static class TestCustomizer {
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            env.put("MISE_LOADED", "true");
        }
    }

    public static class FailingCustomizer {
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            throw new RuntimeException("Simulated customizer error");
        }
    }

    public static class CountingCustomizer {
        int callCount = 0;
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            callCount++;
            env.put("CALL_COUNT", String.valueOf(callCount));
        }
    }

    public static class FailingMutatingCustomizer {
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            env.put("CORRUPT_KEY", "corrupted");
            throw new RuntimeException("Crash after partial mutation");
        }
    }

    @Test
    public void testApplyCustomizersModifiesEnvironmentAndSetsParentTypeNone() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    TestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test")
                    .withEnvironment("EXISTING_VAR", "initial")
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
            cmd.setWorkDirectory("C:/test/dir");

            CommandUtils.applyCustomizers(cmd, new Object[]{new TestCustomizer()});

            Assert.assertEquals("true", cmd.getEnvironment().get("MISE_LOADED"));
            Assert.assertEquals("initial", cmd.getEnvironment().get("EXISTING_VAR"));
            Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.NONE, cmd.getParentEnvironmentType());
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testApplyCustomizersHandlesExceptionGracefully() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    FailingCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test")
                    .withEnvironment("EXISTING_VAR", "initial")
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
            cmd.setWorkDirectory("C:/test/dir");

            // Should not throw exception
            CommandUtils.applyCustomizers(cmd, new Object[]{new FailingCustomizer()});

            // ParentEnvironmentType should remain CONSOLE because all customizers failed
            Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.CONSOLE, cmd.getParentEnvironmentType());
            Assert.assertNull(cmd.getEnvironment().get("MISE_LOADED"));
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testMultipleCustomizersOneSucceedsOneThrowsPreservesSuccessfulCustomization() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    TestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test")
                    .withEnvironment("EXISTING_VAR", "initial")
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
            cmd.setWorkDirectory("C:/test/dir");

            // First customizer succeeds, second throws
            CommandUtils.applyCustomizers(cmd, new Object[]{new TestCustomizer(), new FailingCustomizer()});

            // The exception should not propagate, succeeding customizer's env should be applied,
            // and parent environment type should become NONE
            Assert.assertEquals("true", cmd.getEnvironment().get("MISE_LOADED"));
            Assert.assertEquals("initial", cmd.getEnvironment().get("EXISTING_VAR"));
            Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.NONE, cmd.getParentEnvironmentType());
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testMultipleCustomizersFailingFirstStillRunsSubsequentCustomizers() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    TestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test")
                    .withEnvironment("EXISTING_VAR", "initial")
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
            cmd.setWorkDirectory("C:/test/dir");

            // First customizer throws, second succeeds
            CommandUtils.applyCustomizers(cmd, new Object[]{new FailingCustomizer(), new TestCustomizer()});

            // The failure of the first should not prevent the second from running
            Assert.assertEquals("true", cmd.getEnvironment().get("MISE_LOADED"));
            Assert.assertEquals("initial", cmd.getEnvironment().get("EXISTING_VAR"));
            Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.NONE, cmd.getParentEnvironmentType());
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testCustomizeEnvironmentIdempotency() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    CountingCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test")
                    .withEnvironment("EXISTING_VAR", "initial");
            cmd.setWorkDirectory("C:/test/dir");

            CountingCustomizer countingCustomizer = new CountingCustomizer();
            Object[] customizers = new Object[]{countingCustomizer};

            // First invocation through the real customization helper
            CommandUtils.applyCustomizers(cmd, customizers);

            Assert.assertEquals(1, countingCustomizer.callCount);
            Assert.assertEquals("1", cmd.getEnvironment().get("CALL_COUNT"));
            Assert.assertEquals(Boolean.TRUE, cmd.getUserData(CommandUtils.ENV_CUSTOMIZED_KEY));

            // Second invocation on the same GeneralCommandLine without manually modifying the guard
            CommandUtils.applyCustomizers(cmd, customizers);

            // Verifies that the customizer was NOT executed again and ran exactly once
            Assert.assertEquals(1, countingCustomizer.callCount);
            Assert.assertEquals("1", cmd.getEnvironment().get("CALL_COUNT"));
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testPartialModificationsFromFailingCustomizerAreDiscarded() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    TestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test")
                    .withEnvironment("EXISTING_VAR", "initial")
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
            cmd.setWorkDirectory("C:/test/dir");

            // TestCustomizer succeeds, FailingMutatingCustomizer modifies working copy and throws
            CommandUtils.applyCustomizers(cmd, new Object[]{new TestCustomizer(), new FailingMutatingCustomizer()});

            // The successful customizer's modification is retained
            Assert.assertEquals("true", cmd.getEnvironment().get("MISE_LOADED"));
            Assert.assertEquals("initial", cmd.getEnvironment().get("EXISTING_VAR"));
            // The failing customizer's partial mutation is discarded
            Assert.assertNull(cmd.getEnvironment().get("CORRUPT_KEY"));
            Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.NONE, cmd.getParentEnvironmentType());
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    private static void assertCommand(String command, String... expected) {
        List<String> actual = CommandUtils.createCommands(command);
        Assert.assertArrayEquals(expected, actual.toArray());
    }
}

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
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static class BaseTestCustomizer {
        public boolean executed = false;

        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
        }
    }

    public static class TestCustomizer extends BaseTestCustomizer {
        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            env.put("MISE_LOADED", "true");
        }
    }

    public static class FailingCustomizer extends BaseTestCustomizer {
        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            throw new RuntimeException("Simulated customizer error");
        }
    }

    public static class CountingCustomizer extends BaseTestCustomizer {
        int callCount = 0;

        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            callCount++;
            env.put("CALL_COUNT", String.valueOf(callCount));
        }
    }

    public static class FailingMutatingCustomizer extends BaseTestCustomizer {
        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            env.put("CORRUPT_KEY", "corrupted");
            throw new RuntimeException("Crash after partial mutation");
        }
    }

    public static class InvalidEnvCustomizer extends BaseTestCustomizer {
        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            env.put("VALID_VAR", "valid");
            env.put("INVALID=VAR", "invalid_name");
            env.put("NULL_VAL_VAR", null);
        }
    }

    public static class PathCustomizer extends BaseTestCustomizer {
        private final String customPath;

        public PathCustomizer(String customPath) {
            this.customPath = customPath;
        }

        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            env.put("PATH", customPath);
        }
    }

    public static class DirectoryAwareCustomizer extends BaseTestCustomizer {
        int callCount = 0;

        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            callCount++;
            File workDir = cmd.getWorkDirectory();
            if (workDir != null) {
                env.put("DIR_NAME", workDir.getName());
            }
        }
    }

    public static class LinkageErrorCustomizer extends BaseTestCustomizer {
        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            throw new LinkageError("Simulated LinkageError from incompatible plugin");
        }
    }

    public static class FatalErrorCustomizer extends BaseTestCustomizer {
        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            throw new OutOfMemoryError("Simulated JVM OOM");
        }
    }

    public static class ThreadDeathCustomizer extends BaseTestCustomizer {
        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            throw new ThreadDeath();
        }
    }

    public static class ProcessCanceledCustomizer extends BaseTestCustomizer {
        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            throw new ProcessCanceledException();
        }
    }

    public static class CustomControlFlowException extends RuntimeException implements ControlFlowException {
    }

    public static class ControlFlowCustomizer extends BaseTestCustomizer {
        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            throw new CustomControlFlowException();
        }
    }

    public static class RetryableCancellationCustomizer extends BaseTestCustomizer {
        int callCount = 0;
        boolean shouldCancel = true;

        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            callCount++;
            if (shouldCancel) {
                shouldCancel = false;
                throw new ProcessCanceledException();
            }
            env.put("RETRY_SUCCEEDED", "true");
        }
    }

    public static class OverridingCustomizer extends BaseTestCustomizer {
        @Override
        public void customizeEnv(GeneralCommandLine cmd, Map<String, String> env) {
            executed = true;
            env.put("PATH", "customizer-path");
            env.put("COMMON_VAR", "customizer-common");
            env.put("CUSTOMIZER_ONLY", "customizer-val");
        }
    }

    @Test
    public void testApplyCustomizersModifiesEnvironmentAndSetsParentTypeNone() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test")
                    .withEnvironment("EXISTING_VAR", "initial")
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
            cmd.setWorkDirectory("C:/test/dir");

            TestCustomizer testCustomizer = new TestCustomizer();
            CommandUtils.applyCustomizers(cmd, new Object[]{testCustomizer});

            Assert.assertTrue(testCustomizer.executed);
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
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test")
                    .withEnvironment("EXISTING_VAR", "initial")
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
            cmd.setWorkDirectory("C:/test/dir");

            FailingCustomizer failingCustomizer = new FailingCustomizer();
            // Should not throw exception
            CommandUtils.applyCustomizers(cmd, new Object[]{failingCustomizer});

            Assert.assertTrue(failingCustomizer.executed);
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
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test")
                    .withEnvironment("EXISTING_VAR", "initial")
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
            cmd.setWorkDirectory("C:/test/dir");

            TestCustomizer testCustomizer = new TestCustomizer();
            FailingCustomizer failingCustomizer = new FailingCustomizer();

            // First customizer succeeds, second throws
            CommandUtils.applyCustomizers(cmd, new Object[]{testCustomizer, failingCustomizer});

            Assert.assertTrue(testCustomizer.executed);
            Assert.assertTrue(failingCustomizer.executed);
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
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test")
                    .withEnvironment("EXISTING_VAR", "initial")
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
            cmd.setWorkDirectory("C:/test/dir");

            FailingCustomizer failingCustomizer = new FailingCustomizer();
            TestCustomizer testCustomizer = new TestCustomizer();

            // First customizer throws, second succeeds
            CommandUtils.applyCustomizers(cmd, new Object[]{failingCustomizer, testCustomizer});

            Assert.assertTrue(failingCustomizer.executed);
            Assert.assertTrue(testCustomizer.executed);
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
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test")
                    .withEnvironment("EXISTING_VAR", "initial");
            cmd.setWorkDirectory("C:/test/dir");

            CountingCustomizer countingCustomizer = new CountingCustomizer();
            Object[] customizers = new Object[]{countingCustomizer};

            // First invocation through the real customization helper
            CommandUtils.applyCustomizers(cmd, customizers);

            Assert.assertTrue(countingCustomizer.executed);
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
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test")
                    .withEnvironment("EXISTING_VAR", "initial")
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
            cmd.setWorkDirectory("C:/test/dir");

            TestCustomizer testCustomizer = new TestCustomizer();
            FailingMutatingCustomizer failingCustomizer = new FailingMutatingCustomizer();

            // TestCustomizer succeeds, FailingMutatingCustomizer modifies working copy and throws
            CommandUtils.applyCustomizers(cmd, new Object[]{testCustomizer, failingCustomizer});

            Assert.assertTrue(testCustomizer.executed);
            Assert.assertTrue(failingCustomizer.executed);
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

    @Test
    public void testIncludeParentEnvironmentTruePreservesParentVars() {
        Map<String, String> userEnv = Map.of("USER_VAR", "user_val");
        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", "C:/test/dir", userEnv, true);
        Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.CONSOLE, cmd.getParentEnvironmentType());
        Assert.assertEquals("user_val", cmd.getEnvironment().get("USER_VAR"));
        Assert.assertFalse(cmd.getEnvironment().isEmpty());
    }

    @Test
    public void testUserEnvironmentVariablesOverrideSystemEnvironmentVariables() {
        String pathKey = SystemInfo.isWindows ? "path" : "PATH";
        Map<String, String> userEnv = Map.of(pathKey, "custom-path-override");
        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", null, userEnv, true);
        Assert.assertEquals("custom-path-override", cmd.getEnvironment().get(pathKey));
        if (SystemInfo.isWindows) {
            Assert.assertEquals("custom-path-override", cmd.getEnvironment().get("PATH"));
            Assert.assertEquals("custom-path-override", cmd.getEnvironment().get("Path"));
        }
    }

    @Test
    public void testIncludeParentEnvironmentFalseExcludesParentVars() {
        Map<String, String> userEnv = Map.of("USER_VAR", "user_val");
        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", "C:/test/dir", userEnv, false);
        Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.NONE, cmd.getParentEnvironmentType());
        Assert.assertEquals(1, cmd.getEnvironment().size());
        Assert.assertEquals("user_val", cmd.getEnvironment().get("USER_VAR"));
    }

    @Test
    public void testIncludeParentEnvironmentFalseWithCustomizerDoesNotLeakParentEnv() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            Map<String, String> userEnv = Map.of("USER_VAR", "user_val");
            GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", null, userEnv, false);
            cmd.setWorkDirectory("C:/test/dir");

            TestCustomizer customizer = new TestCustomizer();
            CommandUtils.applyCustomizers(cmd, new Object[]{customizer});

            Assert.assertTrue(customizer.executed);
            Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.NONE, cmd.getParentEnvironmentType());
            Assert.assertEquals("user_val", cmd.getEnvironment().get("USER_VAR"));
            Assert.assertEquals("true", cmd.getEnvironment().get("MISE_LOADED"));
            Assert.assertEquals(2, cmd.getEnvironment().size());
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testApplyCustomizersFiltersInvalidEnvironmentVariableNames() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test");
            cmd.setWorkDirectory("C:/test/dir");

            InvalidEnvCustomizer customizer = new InvalidEnvCustomizer();
            CommandUtils.applyCustomizers(cmd, new Object[]{customizer});

            Assert.assertTrue(customizer.executed);
            Assert.assertEquals("valid", cmd.getEnvironment().get("VALID_VAR"));
            Assert.assertFalse(cmd.getEnvironment().containsKey("INVALID=VAR"));
            Assert.assertFalse(cmd.getEnvironment().containsKey("NULL_VAL_VAR"));
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testCustomizedPathIsAvailableForExecutableResolution() throws Exception {
        String toolName = SystemInfo.isWindows ? "my-custom-tool.bat" : "my-custom-tool";
        Path tempDir = Files.createTempDirectory("path-test");
        try {
            Path fakeExe = tempDir.resolve(toolName);
            Files.writeString(fakeExe, SystemInfo.isWindows ? "@echo off\r\necho ok" : "#!/bin/sh\necho ok");
            fakeExe.toFile().setExecutable(true);

            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine(toolName);
            cmd.setWorkDirectory("C:/test/dir");

            PathCustomizer customizer = new PathCustomizer(tempDir.toAbsolutePath().toString());
            CommandUtils.applyCustomizers(cmd, new Object[]{customizer});

            Assert.assertTrue(customizer.executed);
            Assert.assertEquals(tempDir.toAbsolutePath().toString(), cmd.getEnvironment().get("PATH"));

            File resolved = PathEnvironmentVariableUtil.findInPath(
                    toolName,
                    cmd.getEnvironment().get("PATH"),
                    null
            );
            Assert.assertNotNull("Executable should be resolved from customized PATH", resolved);
            Assert.assertEquals(fakeExe.toFile().getCanonicalPath(), resolved.getCanonicalPath());
        } finally {
            FileUtil.delete(tempDir.toFile());
        }
    }

    @Test
    public void testCustomizerIsNotInvokedAgainDuringProcessCreation() throws Exception {
        File tempDir = FileUtil.createTempDirectory("process-test", null);
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine(SystemInfo.isWindows ? "cmd.exe" : "echo");
            if (SystemInfo.isWindows) {
                cmd.addParameters("/c", "echo", "test");
            } else {
                cmd.addParameter("test");
            }
            cmd.setWorkDirectory(tempDir.getAbsolutePath());

            CountingCustomizer countingCustomizer = new CountingCustomizer();
            CommandUtils.applyCustomizers(cmd, new Object[]{countingCustomizer});

            Assert.assertEquals(1, countingCustomizer.callCount);

            // Execute the process through IntelliJ's GeneralCommandLine.createProcess()
            Process process = cmd.createProcess();
            try {
                process.waitFor();
            } finally {
                process.destroyForcibly();
            }

            // Confirms that process startup does NOT cause a second customizer invocation
            Assert.assertEquals("Customizer must not be invoked again during process creation",
                    1, countingCustomizer.callCount);
        } finally {
            FileUtil.delete(tempDir);
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testUserEnvironmentVariablesOverrideCustomizerVariables() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            String pathKey = SystemInfo.isWindows ? "path" : "PATH";
            Map<String, String> userEnv = new HashMap<>();
            userEnv.put(pathKey, "user-path-override");
            userEnv.put("COMMON_VAR", "user-common-override");

            GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", "C:/test/dir", userEnv, true);
            OverridingCustomizer customizer = new OverridingCustomizer();
            CommandUtils.applyCustomizers(cmd, new Object[]{customizer});

            Assert.assertTrue(customizer.executed);
            // 1. User variables override customizer variables with same name (including PATH)
            Assert.assertEquals("user-path-override", cmd.getEnvironment().get(pathKey));
            if (SystemInfo.isWindows) {
                Assert.assertEquals("user-path-override", cmd.getEnvironment().get("PATH"));
                Assert.assertEquals("user-path-override", cmd.getEnvironment().get("Path"));
            }
            Assert.assertEquals("user-common-override", cmd.getEnvironment().get("COMMON_VAR"));

            // 2. Customizer variables override parent system variables (when not overridden by user)
            Assert.assertEquals("customizer-val", cmd.getEnvironment().get("CUSTOMIZER_ONLY"));

            // 3. Parent environment type becomes NONE once customizers are applied
            Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.NONE, cmd.getParentEnvironmentType());
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testCustomizeEnvironmentReappliesIfWorkingDirectoryChanges() throws Exception {
        File dir1 = FileUtil.createTempDirectory("dir1", null);
        File dir2 = FileUtil.createTempDirectory("dir2", null);
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", dir1.getAbsolutePath(),
                    Collections.emptyMap(), false);
            DirectoryAwareCustomizer customizer = new DirectoryAwareCustomizer();

            // First application for dir1
            CommandUtils.applyCustomizers(cmd, new Object[]{customizer});
            Assert.assertEquals(1, customizer.callCount);
            Assert.assertEquals(dir1.getName(), cmd.getEnvironment().get("DIR_NAME"));

            // Second application on the same instance with SAME working directory does not re-run
            CommandUtils.applyCustomizers(cmd, new Object[]{customizer});
            Assert.assertEquals(1, customizer.callCount);

            // Reusing the same GeneralCommandLine instance with a DIFFERENT working directory
            cmd.setWorkDirectory(dir2.getAbsolutePath());
            CommandUtils.applyCustomizers(cmd, new Object[]{customizer});

            // Must re-apply customizers for the new working directory
            Assert.assertEquals(2, customizer.callCount);
            Assert.assertEquals(dir2.getName(), cmd.getEnvironment().get("DIR_NAME"));
        } finally {
            FileUtil.delete(dir1);
            FileUtil.delete(dir2);
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testStartupDoesNotFailWhenReflectionUnavailable() {
        CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);

        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", "C:/test/dir",
                Map.of("USER_VAR", "user_val"), false);

        // Even when CommandLineEnvCustomizer is unavailable, command line must be valid
        Assert.assertEquals("user_val", cmd.getEnvironment().get("USER_VAR"));
        Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.NONE, cmd.getParentEnvironmentType());
        Assert.assertNotNull(cmd.getWorkDirectory());
    }

    @Test
    public void testFindCustomizeEnvMethodHandlesClassNotFoundAndNoSuchMethod() {
        // Simulates an IDE version where CommandLineEnvCustomizer class is absent (ClassNotFoundException)
        Method missingClassMethod = CommandUtils.CommandLineEnvCustomizerReflection
                .findCustomizeEnvMethod("com.intellij.nonexistent.CommandLineEnvCustomizer");
        Assert.assertNull("Must return null when customizer class is missing", missingClassMethod);

        // Simulates a class that exists but lacks customizeEnv method (NoSuchMethodException)
        Method missingMethod = CommandUtils.CommandLineEnvCustomizerReflection
                .findCustomizeEnvMethod(String.class.getName());
        Assert.assertNull("Must return null when customizeEnv method is missing", missingMethod);
    }

    @Test
    public void testCustomizeEnvironmentWhenExtensionPointUnregistered() throws Exception {
        try {
            // Provide the method so isAvailable() is true
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", "C:/test/dir",
                    Map.of("USER_VAR", "user_val"), false);

            // customizeEnvironmentForWorkingDirectory queries getExtensionPointIfRegistered.
            // When the extension point is not registered in the platform, it returns gracefully without failure.
            CommandUtils.customizeEnvironmentForWorkingDirectory(cmd);

            Assert.assertEquals("user_val", cmd.getEnvironment().get("USER_VAR"));
            Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.NONE, cmd.getParentEnvironmentType());
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testFatalJvmErrorsAreNotSwallowed() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd1 = new GeneralCommandLine("echo", "test");
            cmd1.setWorkDirectory("C:/test/dir");
            FatalErrorCustomizer oomCustomizer = new FatalErrorCustomizer();
            Assert.assertThrows(OutOfMemoryError.class, () ->
                    CommandUtils.applyCustomizers(cmd1, new Object[]{oomCustomizer}));
            Assert.assertTrue(oomCustomizer.executed);

            GeneralCommandLine cmd2 = new GeneralCommandLine("echo", "test");
            cmd2.setWorkDirectory("C:/test/dir");
            ThreadDeathCustomizer threadDeathCustomizer = new ThreadDeathCustomizer();
            Assert.assertThrows(ThreadDeath.class, () ->
                    CommandUtils.applyCustomizers(cmd2, new Object[]{threadDeathCustomizer}));
            Assert.assertTrue(threadDeathCustomizer.executed);
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testApplyCustomizersHandlesLinkageErrorGracefully() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test")
                    .withEnvironment("EXISTING_VAR", "initial")
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
            cmd.setWorkDirectory("C:/test/dir");

            LinkageErrorCustomizer linkageErrorCustomizer = new LinkageErrorCustomizer();
            // Should not throw LinkageError
            CommandUtils.applyCustomizers(cmd, new Object[]{linkageErrorCustomizer});

            Assert.assertTrue(linkageErrorCustomizer.executed);
            Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.CONSOLE, cmd.getParentEnvironmentType());
            Assert.assertEquals("initial", cmd.getEnvironment().get("EXISTING_VAR"));
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testProcessCanceledExceptionIsNotSwallowed() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test");
            cmd.setWorkDirectory("C:/test/dir");

            ProcessCanceledCustomizer customizer = new ProcessCanceledCustomizer();
            Assert.assertThrows(ProcessCanceledException.class, () ->
                    CommandUtils.applyCustomizers(cmd, new Object[]{customizer}));
            Assert.assertTrue(customizer.executed);
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testControlFlowExceptionIsNotSwallowed() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test");
            cmd.setWorkDirectory("C:/test/dir");

            ControlFlowCustomizer customizer = new ControlFlowCustomizer();
            Assert.assertThrows(CustomControlFlowException.class, () ->
                    CommandUtils.applyCustomizers(cmd, new Object[]{customizer}));
            Assert.assertTrue(customizer.executed);
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    @Test
    public void testCheckFatalOrControlFlowDirectly() {
        ProcessCanceledException pce = new ProcessCanceledException();
        Assert.assertThrows(ProcessCanceledException.class, () ->
                CommandUtils.checkFatalOrControlFlow(pce));

        CustomControlFlowException cfe = new CustomControlFlowException();
        Assert.assertThrows(CustomControlFlowException.class, () ->
                CommandUtils.checkFatalOrControlFlow(cfe));

        OutOfMemoryError oom = new OutOfMemoryError("oom");
        Assert.assertThrows(OutOfMemoryError.class, () ->
                CommandUtils.checkFatalOrControlFlow(oom));

        ThreadDeath td = new ThreadDeath();
        Assert.assertThrows(ThreadDeath.class, () ->
                CommandUtils.checkFatalOrControlFlow(td));

        // Ordinary exceptions must NOT be rethrown by checkFatalOrControlFlow
        CommandUtils.checkFatalOrControlFlow(new RuntimeException("ordinary"));
        CommandUtils.checkFatalOrControlFlow(new LinkageError("ordinary"));
        CommandUtils.checkFatalOrControlFlow(null);
    }

    @Test
    public void testCancellationDoesNotLeaveStaleCustomizedStateAndAllowsRetry() throws Exception {
        try {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(
                    BaseTestCustomizer.class.getMethod("customizeEnv", GeneralCommandLine.class, Map.class));

            GeneralCommandLine cmd = new GeneralCommandLine("echo", "test");
            cmd.setWorkDirectory("C:/test/dir");

            RetryableCancellationCustomizer customizer = new RetryableCancellationCustomizer();

            // First invocation throws ProcessCanceledException
            Assert.assertThrows(ProcessCanceledException.class, () ->
                    CommandUtils.applyCustomizers(cmd, new Object[]{customizer}));
            Assert.assertEquals(1, customizer.callCount);
            // Verify that the command line was NOT marked as customized
            Assert.assertNull(cmd.getUserData(CommandUtils.ENV_CUSTOMIZED_KEY));
            Assert.assertNull(cmd.getUserData(CommandUtils.CUSTOMIZED_WORKING_DIR_KEY));
            Assert.assertNull(cmd.getEnvironment().get("RETRY_SUCCEEDED"));

            // Second invocation with the same GeneralCommandLine instance and same working directory succeeds
            CommandUtils.applyCustomizers(cmd, new Object[]{customizer});
            Assert.assertEquals(2, customizer.callCount);
            Assert.assertEquals("true", cmd.getEnvironment().get("RETRY_SUCCEEDED"));
            Assert.assertEquals(Boolean.TRUE, cmd.getUserData(CommandUtils.ENV_CUSTOMIZED_KEY));
            Assert.assertEquals(cmd.getWorkDirectory().getAbsolutePath(), cmd.getUserData(CommandUtils.CUSTOMIZED_WORKING_DIR_KEY));

            // Third invocation confirms idempotency after successful completion
            CommandUtils.applyCustomizers(cmd, new Object[]{customizer});
            Assert.assertEquals(2, customizer.callCount);
        } finally {
            CommandUtils.CommandLineEnvCustomizerReflection.setTestCustomizeEnv(null);
        }
    }

    private static void assertCommand(String command, String... expected) {
        List<String> actual = CommandUtils.createCommands(command);
        Assert.assertArrayEquals(expected, actual.toArray());
    }
}

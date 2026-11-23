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
package com.redhat.devtools.lsp4ij.installation.definition;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.TestApplicationManager;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.*;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FileExistsTask} execution with real temporary files.
 */
public class FileExistsTaskTest {

    @BeforeClass
    public static void initPlatform() {
        TestApplicationManager.getInstance();
    }

    // --- Basic file existence ---

    @Test
    public void testFileExists() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        Path file = Files.createFile(tempDir.resolve("server-binary"));
        try {
            FileExistsTask task = createTask(file.toString());
            InstallerContext context = createContext();
            assertTrue(task.run(context));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testFileDoesNotExist() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        try {
            FileExistsTask task = createTask(tempDir.resolve("nonexistent").toString());
            InstallerContext context = createContext();
            assertFalse(task.run(context));
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testDirectoryExistsButFileDoesNot() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        Path subDir = Files.createDirectory(tempDir.resolve("subdir"));
        try {
            FileExistsTask task = createTask(subDir.resolve("missing-file").toString());
            InstallerContext context = createContext();
            assertFalse(task.run(context));
        } finally {
            Files.deleteIfExists(subDir);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testDirectoryItselfDoesNotMatchAsFile() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        try {
            FileExistsTask task = createTask(tempDir.toString());
            InstallerContext context = createContext();
            assertFalse(task.run(context));
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    // --- Glob patterns (single segment) ---

    @Test
    public void testGlobMatchesSingleFile() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        Path file = Files.createFile(tempDir.resolve("camel-lsp-server-1.31.0.jar"));
        try {
            FileExistsTask task = createTask(tempDir + "/camel-lsp-server-*.jar");
            InstallerContext context = createContext();
            assertTrue(task.run(context));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testGlobMatchesOneOfMultiple() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        Path file1 = Files.createFile(tempDir.resolve("server-1.0.jar"));
        Path file2 = Files.createFile(tempDir.resolve("server-2.0.jar"));
        Path file3 = Files.createFile(tempDir.resolve("other.txt"));
        try {
            FileExistsTask task = createTask(tempDir + "/server-*.jar");
            InstallerContext context = createContext();
            assertTrue(task.run(context));
        } finally {
            Files.deleteIfExists(file1);
            Files.deleteIfExists(file2);
            Files.deleteIfExists(file3);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testGlobNoMatch() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        Path file = Files.createFile(tempDir.resolve("server-1.0.jar"));
        try {
            FileExistsTask task = createTask(tempDir + "/server-*.zip");
            InstallerContext context = createContext();
            assertFalse(task.run(context));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testGlobWithQuestionMark() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        Path file = Files.createFile(tempDir.resolve("server-a.jar"));
        try {
            FileExistsTask task = createTask(tempDir + "/server-?.jar");
            InstallerContext context = createContext();
            assertTrue(task.run(context));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testGlobInNonExistentDirectory() {
        FileExistsTask task = createTask("/nonexistent/directory/server-*.jar");
        InstallerContext context = createContext();
        assertFalse(task.run(context));
    }

    // --- Multi-segment glob ---

    @Test
    public void testMultiSegmentGlobMatches() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        Path versionDir = Files.createDirectory(tempDir.resolve("clangd_18.1.3"));
        Path binDir = Files.createDirectory(versionDir.resolve("bin"));
        Path file = Files.createFile(binDir.resolve("clangd"));
        try {
            FileExistsTask task = createTask(tempDir + "/clangd*/bin/clangd");
            InstallerContext context = createContext();
            assertTrue(task.run(context));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(binDir);
            Files.deleteIfExists(versionDir);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testMultiSegmentGlobNoMatch() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        Path versionDir = Files.createDirectory(tempDir.resolve("rust_1.0"));
        Path binDir = Files.createDirectory(versionDir.resolve("bin"));
        Path file = Files.createFile(binDir.resolve("rust-analyzer"));
        try {
            FileExistsTask task = createTask(tempDir + "/clangd*/bin/clangd");
            InstallerContext context = createContext();
            assertFalse(task.run(context));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(binDir);
            Files.deleteIfExists(versionDir);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testMultiSegmentGlobMatchesAmongMultipleDirs() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        Path dir1 = Files.createDirectory(tempDir.resolve("clangd_17.0"));
        Path dir1bin = Files.createDirectory(dir1.resolve("bin"));
        Path dir2 = Files.createDirectory(tempDir.resolve("clangd_18.1"));
        Path dir2bin = Files.createDirectory(dir2.resolve("bin"));
        Path file = Files.createFile(dir2bin.resolve("clangd"));
        try {
            FileExistsTask task = createTask(tempDir + "/clangd*/bin/clangd");
            InstallerContext context = createContext();
            assertTrue(task.run(context));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir2bin);
            Files.deleteIfExists(dir2);
            Files.deleteIfExists(dir1bin);
            Files.deleteIfExists(dir1);
            Files.deleteIfExists(tempDir);
        }
    }

    // --- The file found, ${check.file} ---

    @Test
    public void testCheckFileIsTheFileWithoutGlob() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        try {
            Files.createFile(tempDir.resolve("server"));
            InstallerContext context = createContext();
            context.putProperty("serverDist", tempDir.toString());
            assertTrue(createTask("${serverDist}/server").run(context));
            assertEquals(tempDir + "/server", context.getProperty(FileExistsTask.CHECK_FILE_PROPERTY));
        } finally {
            FileUtil.delete(tempDir);
        }
    }

    @Test
    public void testCheckFileIsTheLastInNaturalOrderOfTheFilesThatTheGlobMatches() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        try {
            // 19.1.10 is after 19.1.2 in natural order, and before it in the order of the characters
            for (String version : List.of("clangd_9.0.0", "clangd_19.1.10", "clangd_19.1.2", "clangd_19.1")) {
                Files.createDirectories(tempDir.resolve(version).resolve("bin"));
                Files.createFile(tempDir.resolve(version).resolve("bin").resolve("clangd"));
            }
            // A folder without the file, after the others, isn't the one found
            Files.createDirectories(tempDir.resolve("clangd_20.1.0").resolve("bin"));
            InstallerContext context = createContext();
            context.putProperty("serverDist", tempDir.toString().replace('\\', '/'));
            assertTrue(createTask("${serverDist}/clangd*/bin/clangd").run(context));
            assertEquals(tempDir.toString().replace('\\', '/') + "/clangd_19.1.10/bin/clangd",
                    context.getProperty(FileExistsTask.CHECK_FILE_PROPERTY));
        } finally {
            FileUtil.delete(tempDir);
        }
    }

    @Test
    public void testCheckFileOfAGlobInTheFileName() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        try {
            // A jar named after its version, as the Camel Language Server
            for (String jar : List.of("camel-lsp-server-1.9.0.jar", "camel-lsp-server-1.31.0.jar", "other-2.0.jar")) {
                Files.createFile(tempDir.resolve(jar));
            }
            InstallerContext context = createContext();
            assertTrue(createTask(tempDir + "/camel-lsp-server-*.jar").run(context));
            assertEquals(tempDir + "/camel-lsp-server-1.31.0.jar", context.getProperty(FileExistsTask.CHECK_FILE_PROPERTY));
        } finally {
            FileUtil.delete(tempDir);
        }
    }

    @Test
    public void testCheckFileKeepsTheMacrosOfThePath() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        // A path macro, as $USER_HOME$, which a command keeps until the server starts, as the download does
        String macro = "LSP4IJ_TEST_SERVER_DIST";
        PathMacros.getInstance().setMacro(macro, tempDir.toString());
        try {
            Files.createDirectories(tempDir.resolve("clangd_19.1.2").resolve("bin"));
            Files.createFile(tempDir.resolve("clangd_19.1.2").resolve("bin").resolve("clangd"));
            Files.createFile(tempDir.resolve("server"));
            InstallerContext context = createContext();
            assertTrue(createTask("$" + macro + "$/clangd*/bin/clangd").run(context));
            assertEquals("$" + macro + "$/clangd_19.1.2/bin/clangd", context.getProperty(FileExistsTask.CHECK_FILE_PROPERTY));
            assertTrue(createTask("$" + macro + "$/server").run(context));
            assertEquals("$" + macro + "$/server", context.getProperty(FileExistsTask.CHECK_FILE_PROPERTY));
        } finally {
            PathMacros.getInstance().setMacro(macro, null);
            FileUtil.delete(tempDir);
        }
    }

    @Test
    public void testNoCheckFileWhenTheFileIsNotFound() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        try {
            Files.createFile(tempDir.resolve("server"));
            InstallerContext context = createContext();
            assertFalse(createTask(tempDir + "/missing").run(context));
            assertNull(context.getProperty(FileExistsTask.CHECK_FILE_PROPERTY));
            assertFalse(createTask(tempDir + "/missing-*").run(context));
            assertNull(context.getProperty(FileExistsTask.CHECK_FILE_PROPERTY));

            // Nor the file that an earlier check found
            assertTrue(createTask(tempDir + "/server").run(context));
            assertNotNull(context.getProperty(FileExistsTask.CHECK_FILE_PROPERTY));
            assertFalse(createTask(tempDir + "/missing").run(context));
            assertFalse(context.getPropertyKeys().contains(FileExistsTask.CHECK_FILE_PROPERTY));
        } finally {
            FileUtil.delete(tempDir);
        }
    }

    // --- Variable resolution ---

    @Test
    public void testVariableResolution() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        Path file = Files.createFile(tempDir.resolve("server"));
        try {
            FileExistsTask task = createTask("${serverDist}/server");
            InstallerContext context = createContext();
            context.putProperty("serverDist", tempDir.toString());
            assertTrue(task.run(context));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testVariableResolutionWithGlob() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        Path file = Files.createFile(tempDir.resolve("server-1.0.jar"));
        try {
            FileExistsTask task = createTask("${serverDist}/server-*.jar");
            InstallerContext context = createContext();
            context.putProperty("serverDist", tempDir.toString());
            assertTrue(task.run(context));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testUnresolvedVariableReturnsFalse() {
        FileExistsTask task = createTask("${serverDist}/server");
        InstallerContext context = createContext();
        assertFalse(task.run(context));
    }

    // --- Full installer flow (registry → task) ---

    @Test
    public void testFullFlowFileExistsCheckSucceeds() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        Path file = Files.createFile(tempDir.resolve("dapDebugServer.js"));
        try {
            InstallerTaskRegistry registry = new InstallerTaskRegistry();
            registry.registerFactory("fileExists", new FileExistsTaskFactory());
            registry.registerFactory("download", new DownloadTaskFactory());
            registry.registerFactory("configureServer", new ConfigureServerTaskFactory());

            String json = """
                    {
                      "name": "test-server",
                      "properties": {
                        "serverDist": "%s"
                      },
                      "check": {
                        "fileExists": {
                          "name": "Check if installed",
                          "file": "${serverDist}/dapDebugServer.js"
                        }
                      },
                      "run": {
                        "download": {
                          "name": "Download server",
                          "url": "https://example.com/server.tar.gz"
                        }
                      }
                    }
                    """.formatted(tempDir.toString().replace("\\", "\\\\"));

            ServerInstallerDescriptor descriptor = registry.loadInstaller(json);

            InstallerContext context = createContext();
            for (var entry : descriptor.getProperties().entrySet()) {
                context.putProperty(entry.getKey(), entry.getValue());
            }

            assertTrue(descriptor.getCheck().execute(context));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testFullFlowFileExistsCheckFails() throws IOException {
        Path tempDir = Files.createTempDirectory("lsp4ij-test");
        try {
            InstallerTaskRegistry registry = new InstallerTaskRegistry();
            registry.registerFactory("fileExists", new FileExistsTaskFactory());

            String json = """
                    {
                      "name": "test-server",
                      "properties": {
                        "serverDist": "%s"
                      },
                      "check": {
                        "fileExists": {
                          "name": "Check if installed",
                          "file": "${serverDist}/dapDebugServer.js"
                        }
                      }
                    }
                    """.formatted(tempDir.toString().replace("\\", "\\\\"));

            ServerInstallerDescriptor descriptor = registry.loadInstaller(json);

            InstallerContext context = createContext();
            for (var entry : descriptor.getProperties().entrySet()) {
                context.putProperty(entry.getKey(), entry.getValue());
            }

            assertFalse(descriptor.getCheck().execute(context));
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    // --- Helper methods ---

    private static FileExistsTask createTask(String filePath) {
        InstallerTaskRegistry registry = new InstallerTaskRegistry();
        ServerInstallerDescriptor descriptor = new ServerInstallerDescriptor("test", false, registry);
        return new FileExistsTask(null, "test", null, null, filePath, descriptor);
    }

    private static InstallerContext createContext() {
        return new InstallerContext(null, InstallerContext.InstallerAction.CHECK);
    }
}

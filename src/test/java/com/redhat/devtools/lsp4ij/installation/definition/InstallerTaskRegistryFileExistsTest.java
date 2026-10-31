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

import com.redhat.devtools.lsp4ij.installation.definition.tasks.*;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InstallerTaskRegistry} parsing of the {@code fileExists} task type.
 */
public class InstallerTaskRegistryFileExistsTest {

    private InstallerTaskRegistry createRegistry() {
        InstallerTaskRegistry registry = new InstallerTaskRegistry();
        registry.registerFactory("fileExists", new FileExistsTaskFactory());
        registry.registerFactory("exec", new ExecTaskFactory());
        registry.registerFactory("download", new DownloadTaskFactory());
        registry.registerFactory("configureServer", new ConfigureServerTaskFactory());
        registry.registerFactory("showMessage", new ShowMessageTaskFactory());
        return registry;
    }

    // --- JSON parsing tests ---

    @Test
    public void testFileExistsCheckParsed() {
        InstallerTaskRegistry registry = createRegistry();
        ServerInstallerDescriptor descriptor = registry.loadInstaller("""
                {
                  "name": "test-server",
                  "check": {
                    "fileExists": {
                      "name": "Check if installed",
                      "file": "/path/to/server"
                    }
                  }
                }
                """);
        assertNotNull(descriptor.getCheck());
        assertInstanceOf(FileExistsTask.class, descriptor.getCheck());
        assertEquals("Check if installed", descriptor.getCheck().getName());
    }

    @Test
    public void testFileExistsCheckWithNoRunSection() {
        InstallerTaskRegistry registry = createRegistry();
        ServerInstallerDescriptor descriptor = registry.loadInstaller("""
                {
                  "name": "test-server",
                  "check": {
                    "fileExists": {
                      "file": "/path/to/server"
                    }
                  }
                }
                """);
        assertNotNull(descriptor.getCheck());
        assertInstanceOf(FileExistsTask.class, descriptor.getCheck());
        assertNull(descriptor.getRun());
    }

    @Test
    public void testFileExistsCheckWithDownloadRun() {
        InstallerTaskRegistry registry = createRegistry();
        ServerInstallerDescriptor descriptor = registry.loadInstaller("""
                {
                  "name": "test-server",
                  "check": {
                    "fileExists": {
                      "name": "Check if installed",
                      "file": "/path/to/server"
                    }
                  },
                  "run": {
                    "download": {
                      "name": "Download server",
                      "url": "https://example.com/server.zip",
                      "output": {
                        "dir": "/path/to"
                      }
                    }
                  }
                }
                """);
        assertNotNull(descriptor.getCheck());
        assertInstanceOf(FileExistsTask.class, descriptor.getCheck());
        assertNotNull(descriptor.getRun());
        assertInstanceOf(DownloadTask.class, descriptor.getRun());
    }

    @Test
    public void testFileExistsWithProperties() {
        InstallerTaskRegistry registry = createRegistry();
        ServerInstallerDescriptor descriptor = registry.loadInstaller("""
                {
                  "name": "test-server",
                  "properties": {
                    "serverDist": "$USER_HOME$/.lsp4ij/lsp/test"
                  },
                  "check": {
                    "fileExists": {
                      "file": "${serverDist}/server-binary"
                    }
                  }
                }
                """);
        assertNotNull(descriptor.getCheck());
        assertInstanceOf(FileExistsTask.class, descriptor.getCheck());
        assertEquals("$USER_HOME$/.lsp4ij/lsp/test", descriptor.getProperties().get("serverDist"));
    }

    @Test
    public void testFileExistsWithOnSuccessChain() {
        InstallerTaskRegistry registry = createRegistry();
        ServerInstallerDescriptor descriptor = registry.loadInstaller("""
                {
                  "name": "test-server",
                  "check": {
                    "fileExists": {
                      "name": "Check file",
                      "file": "/path/to/server",
                      "onSuccess": {
                        "exec": {
                          "name": "Verify version",
                          "command": "/path/to/server --version"
                        }
                      }
                    }
                  }
                }
                """);
        InstallerTask check = descriptor.getCheck();
        assertNotNull(check);
        assertInstanceOf(FileExistsTask.class, check);
        assertNotNull(check.getOnSuccess());
        assertInstanceOf(ExecTask.class, check.getOnSuccess());
    }

    @Test
    public void testFileExistsWithOnFailChain() {
        InstallerTaskRegistry registry = createRegistry();
        ServerInstallerDescriptor descriptor = registry.loadInstaller("""
                {
                  "name": "test-server",
                  "check": {
                    "fileExists": {
                      "name": "Check file",
                      "file": "/path/to/server",
                      "onFail": {
                        "showMessage": {
                          "message": "Server not found",
                          "actions": [
                            { "label": "OK" }
                          ]
                        }
                      }
                    }
                  }
                }
                """);
        InstallerTask check = descriptor.getCheck();
        assertNotNull(check);
        assertInstanceOf(FileExistsTask.class, check);
        assertNotNull(check.getOnFail());
        assertInstanceOf(ShowMessageTask.class, check.getOnFail());
    }

    @Test
    public void testFileExistsWithId() {
        InstallerTaskRegistry registry = createRegistry();
        ServerInstallerDescriptor descriptor = registry.loadInstaller("""
                {
                  "name": "test-server",
                  "check": {
                    "fileExists": {
                      "id": "check-binary",
                      "name": "Check binary",
                      "file": "/path/to/server"
                    }
                  }
                }
                """);
        InstallerTask check = descriptor.getCheck();
        assertNotNull(check);
        assertEquals("check-binary", check.getId());
        assertEquals("Check binary", check.getName());
    }

    @Test
    public void testExecCheckStillWorks() {
        InstallerTaskRegistry registry = createRegistry();
        ServerInstallerDescriptor descriptor = registry.loadInstaller("""
                {
                  "name": "test-server",
                  "check": {
                    "exec": {
                      "name": "Trying current command",
                      "command": "echo hello"
                    }
                  }
                }
                """);
        assertNotNull(descriptor.getCheck());
        assertInstanceOf(ExecTask.class, descriptor.getCheck());
    }

    @Test
    public void testExecuteOnStartServer() {
        InstallerTaskRegistry registry = createRegistry();
        ServerInstallerDescriptor descriptor = registry.loadInstaller("""
                {
                  "name": "test-server",
                  "executeOnStartServer": false,
                  "check": {
                    "fileExists": {
                      "file": "/path/to/server"
                    }
                  }
                }
                """);
        assertFalse(descriptor.isExecuteOnStartServer());
    }

    @Test
    public void testMultipleProperties() {
        InstallerTaskRegistry registry = createRegistry();
        ServerInstallerDescriptor descriptor = registry.loadInstaller("""
                {
                  "name": "test-server",
                  "properties": {
                    "serverDist": "/opt/server",
                    "configDir": "/etc/server",
                    "version": "1.0"
                  },
                  "check": {
                    "fileExists": {
                      "file": "${serverDist}/bin/server"
                    }
                  }
                }
                """);
        assertEquals("/opt/server", descriptor.getProperties().get("serverDist"));
        assertEquals("/etc/server", descriptor.getProperties().get("configDir"));
        assertEquals("1.0", descriptor.getProperties().get("version"));
    }

    // --- Template installer.json validation tests ---

    @Test
    public void testVscodeJsDebugInstallerJson() throws IOException {
        assertTemplateInstaller("dap/vscode-js-debug", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testCodelldbInstallerJson() throws IOException {
        assertTemplateInstaller("dap/codelldb", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testPerlDapInstallerJson() throws IOException {
        assertTemplateInstaller("dap/perl-dap", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testClojureLspInstallerJson() throws IOException {
        assertTemplateInstaller("lsp/clojure-lsp", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testDockerLanguageServerInstallerJson() throws IOException {
        assertTemplateInstaller("lsp/docker-language-server", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testHarperLsInstallerJson() throws IOException {
        assertTemplateInstaller("lsp/harper-ls", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testJdtlsInstallerJson() throws IOException {
        assertTemplateInstaller("lsp/jdtls", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testLuauLspInstallerJson() throws IOException {
        assertTemplateInstaller("lsp/luau-lsp", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testPerlLspInstallerJson() throws IOException {
        assertTemplateInstaller("lsp/perl-lsp", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testRustAnalyzerInstallerJson() throws IOException {
        assertTemplateInstaller("lsp/rust-analyzer", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testTerraformLsInstallerJson() throws IOException {
        assertTemplateInstaller("lsp/terraform-ls", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testWgslAnalyzerInstallerJson() throws IOException {
        assertTemplateInstaller("lsp/wgsl-analyzer", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testAdaLanguageServerInstallerJson() throws IOException {
        assertTemplateInstaller("lsp/ada_language_server", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testCamelLspServerInstallerJson() throws IOException {
        assertTemplateInstaller("lsp/camel-lsp-server", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testClangdInstallerJson() throws IOException {
        assertTemplateInstaller("lsp/clangd", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testErlangLsInstallerJsonKeepsExecCheck() throws IOException {
        assertTemplateInstaller("lsp/erlang-ls", ExecTask.class, DownloadTask.class);
    }

    @Test
    public void testAllDapTemplatesParseWithoutError() throws IOException {
        assertAllTemplatesInDirectoryParse("dap");
    }

    @Test
    public void testAllLspTemplatesParseWithoutError() throws IOException {
        assertAllTemplatesInDirectoryParse("lsp");
    }

    // --- Helper methods ---

    private void assertTemplateInstaller(String templatePath,
                                         Class<? extends InstallerTask> expectedCheckType,
                                         Class<? extends InstallerTask> expectedRunType) throws IOException {
        InstallerTaskRegistry registry = createRegistry();
        Path jsonPath = Paths.get("src/main/resources/templates", templatePath, "installer.json");
        assertTrue(Files.exists(jsonPath), "installer.json not found: " + jsonPath);

        String json = Files.readString(jsonPath);
        ServerInstallerDescriptor descriptor = registry.loadInstaller(json);

        assertNotNull(descriptor.getName(), "Installer name should not be null");
        assertNotNull(descriptor.getCheck(), "Check task should not be null for " + templatePath);
        assertInstanceOf(expectedCheckType, descriptor.getCheck(),
                "Check task should be " + expectedCheckType.getSimpleName() + " for " + templatePath);
        assertNotNull(descriptor.getRun(), "Run task should not be null for " + templatePath);
        assertInstanceOf(expectedRunType, descriptor.getRun(),
                "Run task should be " + expectedRunType.getSimpleName() + " for " + templatePath);
    }

    private void assertAllTemplatesInDirectoryParse(String subDir) throws IOException {
        InstallerTaskRegistry registry = createRegistry();
        Path templatesDir = Paths.get("src/main/resources/templates", subDir);
        assertTrue(Files.isDirectory(templatesDir), "Templates directory not found: " + templatesDir);

        try (var dirs = Files.list(templatesDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                Path installerJson = dir.resolve("installer.json");
                if (Files.exists(installerJson)) {
                    try {
                        String json = Files.readString(installerJson);
                        ServerInstallerDescriptor descriptor = registry.loadInstaller(json);
                        assertNotNull(descriptor.getName(),
                                "Installer name should not be null for " + dir.getFileName());
                    } catch (Exception e) {
                        fail("Failed to parse " + installerJson + ": " + e.getMessage());
                    }
                }
            });
        }
    }
}

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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.TestApplicationManager;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.*;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InstallerTaskRegistry} parsing of the {@code fileExists} task type.
 */
public class InstallerTaskRegistryFileExistsTest {

    // How long a test waits for the notification of a task, in seconds
    private static final int NOTIFICATION_TIMEOUT = 30;

    // The download task looks up an application service, which an earlier platform test of the same JVM may have
    // started, or not, as when this test runs on its own
    @BeforeClass
    public static void startTheApplication() {
        TestApplicationManager.getInstance();
    }

    private InstallerTaskRegistry createRegistry() {
        InstallerTaskRegistry registry = new InstallerTaskRegistry();
        registry.registerFactory("fileExists", new FileExistsTaskFactory());
        registry.registerFactory("exec", new ExecTaskFactory());
        registry.registerFactory("download", new DownloadTaskFactory());
        registry.registerFactory("configureServer", new ConfigureServerTaskFactory());
        registry.registerFactory("replaceTemplateCommand", new ReplaceTemplateCommandTaskFactory());
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
    public void testErlangEdbInstallerJson() throws IOException {
        assertTemplateInstaller("dap/erlang-edb", FileExistsTask.class, DownloadTask.class);
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
    public void testErlangElpInstallerJson() throws IOException {
        assertTemplateInstaller("lsp/erlang-elp", FileExistsTask.class, DownloadTask.class);
    }

    @Test
    public void testErlangLsInstallerJsonKeepsExecCheck() throws IOException {
        assertTemplateInstaller("lsp/erlang-ls", ExecTask.class, DownloadTask.class);
    }

    @Test
    public void testErlangLsInstallerJsonChecksForErlangLsOnceItFindsEscript() throws IOException {
        // escript, of Erlang/OTP, starts erlang_ls, which the download puts in the folder of the template: without
        // looking for erlang_ls, the check would find erlang-ls installed wherever Erlang/OTP is, and never download it
        var descriptor = assertTemplateInstaller("lsp/erlang-ls", ExecTask.class, DownloadTask.class);
        assertInstanceOf(FileExistsTask.class, descriptor.getCheck().getOnSuccess(),
                "The check of erlang-ls should look for erlang_ls once it has found escript");
    }

    @Test
    public void testPerlLspChecksForTheServerWhereItsDownloadPutsIt() throws Exception {
        assertTheCheckFindsWhatTheDownloadInstalled("lsp/perl-lsp");
    }

    @Test
    public void testPerlDapChecksForTheDebugAdapterWhereItsDownloadPutsIt() throws Exception {
        assertTheCheckFindsWhatTheDownloadInstalled("dap/perl-dap");
    }

    @Test
    public void testClangdGetsFromItsCheckTheCommandThatItsDownloadGivesIt() throws Exception {
        // The download unpacks the folder of the archive, named after the version of clangd, next to an earlier one
        assertTheCheckGivesTheCommandThatTheDownloadGives("lsp/clangd", "clangd-mac-snapshot_20250518.zip",
                List.of("clangd_snapshot_20250518/bin/clangd", "clangd_snapshot_20250518/bin/clangd.exe",
                        "clangd_snapshot_20250518/lib/clang/include/stddef.h"),
                List.of("clangd_snapshot_20250101/bin/clangd", "clangd_snapshot_20250101/bin/clangd.exe"),
                "snapshot_20250518", "snapshot_20250101");
    }

    @Test
    public void testCamelLspServerGetsFromItsCheckTheCommandThatItsDownloadGivesIt() throws Exception {
        // A jar named after its version, next to an earlier one, which comes after it in the order of the characters
        assertTheCheckGivesTheCommandThatTheDownloadGives("lsp/camel-lsp-server", "camel-lsp-server-1.31.0.jar",
                List.of(), List.of("camel-lsp-server-1.9.0.jar"), "1.31.0", "1.9.0");
    }

    @Test
    public void testCodelldbGetsFromItsCheckTheCommandThatItsDownloadGivesIt() throws Exception {
        assertTheCheckGivesTheCommandThatTheDownloadGives("dap/codelldb", "codelldb-darwin-arm64.vsix",
                List.of("extension/adapter/codelldb", "extension/adapter/codelldb.exe", "extension.vsixmanifest"),
                List.of(), null, null);
    }

    @Test
    public void testPerlDapGetsFromItsCheckTheCommandThatItsDownloadGivesIt() throws Exception {
        String root = "perllsp-0.17.0-x86_64-pc-windows-msvc/";
        assertTheCheckGivesTheCommandThatTheDownloadGives("dap/perl-dap", "perllsp-0.17.0-x86_64-pc-windows-msvc.zip",
                List.of(root + "perl-dap", root + "perl-dap.exe", root + "README.md"), List.of(), null, null);
    }

    @Test
    public void testVscodeJsDebugGetsFromItsCheckTheCommandThatItsDownloadGivesIt() throws Exception {
        // The archive has the folder js-debug/
        assertTheCheckGivesTheCommandThatTheDownloadGives("dap/vscode-js-debug", "js-debug-dap-v1.100.1.zip",
                List.of("js-debug/src/dapDebugServer.js", "js-debug/package.json"), List.of(), null, null);
    }

    @Test
    public void testCppLsGetsFromItsCheckTheCommandThatItsDownloadGivesIt() throws Exception {
        // The template has the command of a cpp_ls on the PATH; the download puts it in the folder of the template
        assertTheCheckGivesTheCommandThatTheDownloadGives("lsp/cpp_ls", "cpp_ls-win32-x64.zip",
                List.of("cpp_ls", "cpp_ls.exe"), List.of(), null, null);
    }

    @Test
    public void testDockerLanguageServerGetsFromItsCheckTheCommandThatItsDownloadGivesIt() throws Exception {
        // The asset is the executable itself, which the download renames
        assertTheCheckGivesTheCommandThatTheDownloadGives("lsp/docker-language-server",
                "docker-language-server-darwin-arm64-v0.20.2", List.of(), List.of(), null, null);
    }

    @Test
    public void testTerraformLsGetsFromItsCheckTheCommandThatItsDownloadGivesIt() throws Exception {
        assertTheCheckGivesTheCommandThatTheDownloadGives("lsp/terraform-ls", "terraform-ls_0.39.1_darwin_arm64.zip",
                List.of("terraform-ls", "terraform-ls.exe", "LICENSE.txt"), List.of(), null, null);
    }

    @Test
    public void testErlangElpGetsFromItsCheckTheCommandThatItsDownloadGivesIt() throws Exception {
        // The template has the command of an elp on the PATH; the archive of ELP has only the program, at its root
        assertTheCheckGivesTheCommandThatTheDownloadGives("lsp/erlang-elp",
                "elp-macos-aarch64-apple-darwin-otp-27.3.tar.gz", List.of(SystemInfo.isWindows ? "elp.exe" : "elp"),
                List.of(), null, null);
    }

    @Test
    public void testErlangEdbGetsFromItsCheckTheCommandThatItsDownloadGivesIt() throws Exception {
        // The template has the command of an edb on the PATH; the archive of edb has the folder edb-<version>/, whose
        // content goes into the folder of the template, as the launcher edb finds its escripts next to it
        assertTheCheckGivesTheCommandThatTheDownloadGives("dap/erlang-edb", "edb-1.0.0.tar.gz",
                List.of("edb-1.0.0/edb", "edb-1.0.0/otp-28/edb", "edb-1.0.0/otp-29/edb"), List.of(), null, null);
    }

    @Test
    public void testOmniSharpRoslynInstallerJsonChecksTheDotNetSdkBeforeTheServer() throws IOException {
        // OmniSharp runs on the .NET SDK, which the check and the run look for first
        var descriptor = assertTemplateInstaller("lsp/omnisharp-roslyn", ExecTask.class, ExecTask.class);
        assertInstanceOf(FileExistsTask.class, descriptor.getCheck().getOnSuccess(),
                "The check of OmniSharp should look for OmniSharp once it has found the .NET SDK");
        assertInstanceOf(DownloadTask.class, descriptor.getRun().getOnSuccess(),
                "The run of OmniSharp should download OmniSharp once it has found the .NET SDK");
        assertInstanceOf(ShowMessageTask.class, descriptor.getRun().getOnFail(),
                "The run of OmniSharp should say that the .NET SDK is missing");
    }

    @Test
    public void testOmniSharpRoslynChecksForTheServerWhereItsDownloadPutsIt() throws Exception {
        Path serverDist = Files.createTempDirectory("lsp4ij-server-dist");
        Path downloads = Files.createTempDirectory("lsp4ij-release");
        try {
            Path archive = writeOmniSharpRelease(downloads);
            ServerInstallerDescriptor descriptor = loadOmniSharpInstaller(serverDist, archive, null);
            List<String> commands = new ArrayList<>(List.of(templateCommand("lsp/omnisharp-roslyn")));

            // The download, which the run starts once it has found the .NET SDK
            assertTrue(execute(descriptor, descriptor.getRun().getOnSuccess(), commands),
                    "The download should install OmniSharp");
            Path server = serverDist.resolve("OmniSharp.dll");
            assertTrue(Files.isRegularFile(server), "The download should extract OmniSharp.dll into " + serverDist);
            assertEquals("dotnet \"" + serverDist.toString().replace('\\', '/') + "/OmniSharp.dll\" --languageserver",
                    commands.get(commands.size() - 1), "The download should start OmniSharp.dll with dotnet");
            // The check of the file, once the check has found the .NET SDK
            assertTrue(execute(descriptor, descriptor.getCheck().getOnSuccess(), commands),
                    "The check should find what the download installed");
        } finally {
            FileUtil.delete(serverDist);
            FileUtil.delete(downloads);
        }
    }

    @Test
    public void testOmniSharpRoslynAsksForTheDotNetSdkInsteadOfDownloadingWithoutIt() throws Exception {
        Path serverDist = Files.createTempDirectory("lsp4ij-server-dist");
        Path downloads = Files.createTempDirectory("lsp4ij-release");
        Disposable disposable = Disposer.newDisposable();
        try {
            Path archive = writeOmniSharpRelease(downloads);
            // A dotnet which isn't on the PATH
            ServerInstallerDescriptor descriptor = loadOmniSharpInstaller(serverDist, archive,
                    List.of("lsp4ij-missing-dotnet-" + UUID.randomUUID(), "--version"));
            String templateCommand = templateCommand("lsp/omnisharp-roslyn");
            List<String> commands = new ArrayList<>(List.of(templateCommand));
            var notification = new CompletableFuture<Notification>();
            ApplicationManager.getApplication().getMessageBus().connect(disposable)
                    .subscribe(Notifications.TOPIC, new Notifications() {
                        @Override
                        public void notify(@NotNull Notification given) {
                            if (".NET SDK not found".equals(given.getTitle())) {
                                notification.complete(given);
                            }
                        }
                    });

            assertFalse(execute(descriptor, descriptor.getRun(), commands),
                    "The run should fail without the .NET SDK");
            Notification message;
            try {
                message = notification.get(NOTIFICATION_TIMEOUT, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new AssertionError("The run without the .NET SDK showed no \".NET SDK not found\" message in "
                        + NOTIFICATION_TIMEOUT + " seconds", e);
            }
            assertTrue(message.getContent().contains("https://dotnet.microsoft.com/download"), message.getContent());
            try (var installed = Files.list(serverDist)) {
                assertEquals(List.of(), installed.toList(), "The run should download nothing without the .NET SDK");
            }
            assertEquals(List.of(templateCommand), commands, "The run should leave the command as it is");
            assertFalse(execute(descriptor, descriptor.getCheck(), commands),
                    "The check should fail without the .NET SDK");
        } finally {
            Disposer.dispose(disposable);
            FileUtil.delete(serverDist);
            FileUtil.delete(downloads);
        }
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

    /**
     * Writes an archive laid out as the {@code omnisharp-<os>-<arch>.zip} assets of OmniSharp 2.0.0, with OmniSharp at
     * its root, next to folders, and returns it.
     */
    private static Path writeOmniSharpRelease(Path downloads) throws IOException {
        Path archive = downloads.resolve("omnisharp-osx-arm64.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String entry : List.of("OmniSharp", "OmniSharp.exe", "OmniSharp.dll", "OmniSharp.runtimeconfig.json",
                    "BuildHost-netcore/Microsoft.CodeAnalysis.Workspaces.MSBuild.BuildHost.dll", "cs/OmniSharp.resources.dll")) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(entry.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    /**
     * Loads the bundled OmniSharp installer, with the given folder as the folder of the server, and the given archive
     * as its download, and, if it isn't null, the given command instead of {@code dotnet --version} in its check and its
     * run.
     */
    private ServerInstallerDescriptor loadOmniSharpInstaller(Path serverDist, Path archive,
                                                             @Nullable List<String> dotnetCommand) throws IOException {
        JsonObject json = JsonParser.parseString(
                        Files.readString(Paths.get("src/main/resources/templates/lsp/omnisharp-roslyn/installer.json")))
                .getAsJsonObject();
        json.getAsJsonObject("properties").addProperty("serverDist", serverDist.toString().replace('\\', '/'));
        JsonObject run = json.getAsJsonObject("run").getAsJsonObject("exec");
        JsonObject download = run.getAsJsonObject("onSuccess").getAsJsonObject("download");
        download.remove("github");
        download.addProperty("url", archive.toUri().toString());
        if (dotnetCommand != null) {
            JsonArray command = new JsonArray();
            dotnetCommand.forEach(command::add);
            run.add("command", command);
            json.getAsJsonObject("check").getAsJsonObject("exec").add("command", command.deepCopy());
        }
        return createRegistry().loadInstaller(json.toString());
    }

    /**
     * Runs the download of the given bundled installer, twice, as a reinstall does, from an archive laid out as the
     * perl-lsp releases are, with one root folder, {@code perllsp-<version>-<target>/}; then its check, which must
     * find what the download installed, where the command that the download configures starts it.
     */
    private void assertTheCheckFindsWhatTheDownloadInstalled(String templatePath) throws Exception {
        Path serverDist = Files.createTempDirectory("lsp4ij-server-dist");
        Path downloads = Files.createTempDirectory("lsp4ij-release");
        try {
            String root = "perllsp-0.17.0-x86_64-pc-windows-msvc/";
            Path archive = downloads.resolve("perllsp-0.17.0-x86_64-pc-windows-msvc.zip");
            try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                for (String file : List.of("perllsp", "perllsp.exe", "perl-dap", "perl-dap.exe", "README.md")) {
                    zip.putNextEntry(new ZipEntry(root + file));
                    zip.write(file.getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            JsonObject json = JsonParser.parseString(
                    Files.readString(Paths.get("src/main/resources/templates", templatePath, "installer.json")))
                    .getAsJsonObject();
            json.getAsJsonObject("properties").addProperty("serverDist", serverDist.toString().replace('\\', '/'));
            JsonObject download = json.getAsJsonObject("run").getAsJsonObject("download");
            download.remove("github");
            download.addProperty("url", archive.toUri().toString());
            ServerInstallerDescriptor descriptor = createRegistry().loadInstaller(json.toString());

            List<String> commands = new ArrayList<>();
            assertTrue(execute(descriptor, descriptor.getRun(), commands), "The download should install " + templatePath);
            assertTrue(execute(descriptor, descriptor.getRun(), commands), "A reinstall should replace what is installed");
            assertTrue(execute(descriptor, descriptor.getCheck(), commands),
                    "The check should find what the download of " + templatePath + " installed");

            var started = Pattern.compile("^\"([^\"]+)\"").matcher(commands.get(commands.size() - 1));
            assertTrue(started.find(), "The command should start with the quoted path of the server: " + commands);
            Path server = Paths.get(started.group(1));
            assertTrue(Files.isRegularFile(server), "The command should start what the download installed: " + server);
            assertEquals(serverDist.toRealPath(), server.getParent().toRealPath(),
                    "The download should put the server in the folder that the check looks in");
        } finally {
            FileUtil.delete(serverDist);
            FileUtil.delete(downloads);
        }
    }

    /**
     * Runs the download of the given bundled installer, from an asset which is a zip of the given entries, or a
     * {@code .tar.gz} of them for an asset of that name, or a file without them, into a folder with the given files of
     * an earlier installation; then its check, for a server created from the template once the server is installed,
     * which has the command of the template: the check must give the server the command that the download gave it, and
     * leave a command that the user changed, or that an earlier download gave, of the given earlier version instead of
     * the given version, as it is (#2011).
     */
    private void assertTheCheckGivesTheCommandThatTheDownloadGives(String templatePath,
                                                                   String asset,
                                                                   List<String> entries,
                                                                   List<String> installedBefore,
                                                                   String version,
                                                                   String earlierVersion) throws Exception {
        Path serverDist = Files.createTempDirectory("lsp4ij-server-dist");
        Path downloads = Files.createTempDirectory("lsp4ij-release");
        try {
            for (String file : installedBefore) {
                Path installed = serverDist.resolve(file);
                Files.createDirectories(installed.getParent());
                Files.writeString(installed, file);
            }
            Path archive = downloads.resolve(asset);
            if (entries.isEmpty()) {
                Files.writeString(archive, asset);
            } else if (asset.endsWith(".tar.gz")) {
                writeTarGz(archive, entries);
            } else {
                try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                    for (String entry : entries) {
                        zip.putNextEntry(new ZipEntry(entry));
                        zip.write(entry.getBytes(StandardCharsets.UTF_8));
                        zip.closeEntry();
                    }
                }
            }
            JsonObject json = JsonParser.parseString(
                    Files.readString(Paths.get("src/main/resources/templates", templatePath, "installer.json")))
                    .getAsJsonObject();
            json.getAsJsonObject("properties").addProperty("serverDist", serverDist.toString().replace('\\', '/'));
            JsonObject download = json.getAsJsonObject("run").getAsJsonObject("download");
            download.remove("github");
            download.remove("maven");
            download.addProperty("url", archive.toUri().toString());
            ServerInstallerDescriptor descriptor = createRegistry().loadInstaller(json.toString());

            List<String> commands = new ArrayList<>();
            assertTrue(execute(descriptor, descriptor.getRun(), commands), "The download should install " + templatePath);
            String downloaded = commands.get(commands.size() - 1);

            commands.add(templateCommand(templatePath));
            assertTrue(execute(descriptor, descriptor.getCheck(), commands),
                    "The check should find what the download of " + templatePath + " installed");
            assertEquals(downloaded, commands.get(commands.size() - 1),
                    "The check should give a server with the command of the template the command of the download: "
                            + commands);

            List<String> kept = new ArrayList<>(List.of("/opt/server --verbose"));
            if (version != null) {
                assertTrue(downloaded.contains(version), downloaded);
                kept.add(downloaded.replace(version, earlierVersion));
            }
            for (String command : kept) {
                commands.add(command);
                int size = commands.size();
                assertTrue(execute(descriptor, descriptor.getCheck(), commands));
                assertEquals(size, commands.size(), "The check should leave the command " + command + " as it is: "
                        + commands);
            }
        } finally {
            FileUtil.delete(serverDist);
            FileUtil.delete(downloads);
        }
    }

    /**
     * Writes a {@code .tar.gz} of the given entries, each with its name as its content.
     */
    private static void writeTarGz(Path archive, List<String> entries) throws IOException {
        try (var tar = new TarArchiveOutputStream(new GZIPOutputStream(Files.newOutputStream(archive)))) {
            for (String name : entries) {
                byte[] content = name.getBytes(StandardCharsets.UTF_8);
                var entry = new TarArchiveEntry(name);
                entry.setSize(content.length);
                tar.putArchiveEntry(entry);
                tar.write(content);
                tar.closeArchiveEntry();
            }
        }
    }

    /**
     * Returns the command of the given bundled template on this OS: the {@code programArgs} of a language server, or
     * the {@code launch} of a debug adapter.
     */
    private static String templateCommand(String templatePath) throws IOException {
        JsonObject template = JsonParser.parseString(
                Files.readString(Paths.get("src/main/resources/templates", templatePath, "template.json")))
                .getAsJsonObject();
        JsonObject command = template.getAsJsonObject(templatePath.startsWith("dap/") ? "launch" : "programArgs");
        return command.has(LanguageServerTemplate.OS_KEY)
                ? command.get(LanguageServerTemplate.OS_KEY).getAsString()
                : command.get(LanguageServerTemplate.DEFAULT_KEY).getAsString();
    }

    private static boolean execute(ServerInstallerDescriptor descriptor, InstallerTask task, List<String> commands) {
        var context = new InstallerContext(null, InstallerContext.InstallerAction.CHECK_AND_RUN);
        context.setCommandLineUpdater(new CommandLineUpdater() {
            @Override
            public String getCommandLine() {
                return commands.isEmpty() ? null : commands.get(commands.size() - 1);
            }

            @Override
            public void setCommandLine(String commandLine) {
                commands.add(commandLine);
            }
        });
        descriptor.getProperties().forEach(context::putProperty);
        return task.execute(context);
    }

    private ServerInstallerDescriptor assertTemplateInstaller(String templatePath,
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
        return descriptor;
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

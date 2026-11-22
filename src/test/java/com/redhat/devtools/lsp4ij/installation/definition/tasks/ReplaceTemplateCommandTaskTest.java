/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.TestApplicationManager;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskRegistry;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@code replaceTemplateCommand} changes the command of the server only while it is the command of the
 * template, so that the check of an installer, which gives a server created from the template the command of the file
 * that it found, {@code ${check.file}}, leaves a command that the user or an earlier installation gave the server as it
 * is, and still passes (#2011). And that {@code configureServer}, without the condition, changes the command as before,
 * unless it has a {@code ${check.file}} that no check found.
 */
public class ReplaceTemplateCommandTaskTest {

    private static final String TEMPLATE_COMMAND = "clangd";
    private static final String FOUND = "$USER_HOME$/.lsp4ij/lsp/clangd_19.1.2/bin/clangd";

    @BeforeClass
    public static void initPlatform() {
        TestApplicationManager.getInstance();
    }

    @Test
    public void testTheCommandOfTheTemplateIsReplaced() {
        List<String> commands = new ArrayList<>(List.of(TEMPLATE_COMMAND));
        InstallerContext context = createContext(commands);
        context.putProperty(FileExistsTask.CHECK_FILE_PROPERTY, FOUND);

        assertTrue(createTask(TEMPLATE_COMMAND, "\"${check.file}\"").run(context));

        assertEquals(List.of(TEMPLATE_COMMAND, "\"" + FOUND + "\""), commands);
    }

    @Test
    public void testACommandWhichTheUserChangedStays() {
        for (String command : List.of("/opt/llvm/bin/clangd --log=verbose", "clangd ", "")) {
            List<String> commands = new ArrayList<>(List.of(command));
            InstallerContext context = createContext(commands);
            context.putProperty(FileExistsTask.CHECK_FILE_PROPERTY, FOUND);

            // The check, which found the server installed, still passes
            assertTrue(createTask(TEMPLATE_COMMAND, "\"${check.file}\"").run(context));

            assertEquals(List.of(command), commands, "The command of the user should stay");
        }
    }

    @Test
    public void testACommandWhichAnEarlierDownloadGaveStays() {
        // The download gave the server the command of clangd 19, before clangd 20 was installed too
        String downloaded = "\"" + FOUND + "\"";
        List<String> commands = new ArrayList<>(List.of(downloaded));
        InstallerContext context = createContext(commands);
        context.putProperty(FileExistsTask.CHECK_FILE_PROPERTY, "$USER_HOME$/.lsp4ij/lsp/clangd_20.1.0/bin/clangd");

        assertTrue(createTask(TEMPLATE_COMMAND, "\"${check.file}\"").run(context));

        assertEquals(List.of(downloaded), commands);
    }

    @Test
    public void testWithoutATemplateCommandForTheOsTheCommandStays() {
        List<String> commands = new ArrayList<>(List.of(TEMPLATE_COMMAND));
        InstallerContext context = createContext(commands);
        context.putProperty(FileExistsTask.CHECK_FILE_PROPERTY, FOUND);

        assertTrue(createTask(null, "\"${check.file}\"").run(context));

        assertEquals(List.of(TEMPLATE_COMMAND), commands);
    }

    @Test
    public void testWithoutACommandForTheOsTheCommandStays() {
        // As "command": {"windows": "clangd.exe"} gives on another OS
        List<String> commands = new ArrayList<>(List.of(TEMPLATE_COMMAND));
        InstallerContext context = createContext(commands);
        context.putProperty(FileExistsTask.CHECK_FILE_PROPERTY, FOUND);

        assertTrue(createTask(TEMPLATE_COMMAND, "").run(context));

        assertEquals(List.of(TEMPLATE_COMMAND), commands, "The server must keep its command");
    }

    @Test
    public void testACheckFileThatNoCheckFoundIsAnError() {
        List<String> commands = new ArrayList<>(List.of(TEMPLATE_COMMAND));
        InstallerContext context = createContext(commands);

        assertFalse(createTask(TEMPLATE_COMMAND, "\"${check.file}\"").run(context));

        assertEquals(List.of(TEMPLATE_COMMAND), commands, "The command should stay");
        assertTrue(context.getStatus().stream().anyMatch(status -> status.level() == Level.SEVERE
                && status.message().contains("${check.file}")), "The error should be told: " + context.getStatus());
    }

    @Test
    public void testConfigureServerChangesTheCommandAsBefore() {
        List<String> commands = new ArrayList<>(List.of("/opt/llvm/bin/clangd"));
        InstallerContext context = createContext(commands);
        context.putProperty("output.dir", "$USER_HOME$/.lsp4ij/lsp/clangd_19.1.2");
        context.putProperty("output.file.name", "bin/clangd");

        assertTrue(new ConfigureServerTask(null, "test", null, null, "\"${output.dir}/${output.file.name}\"",
                createDescriptor()).run(context));

        assertEquals(List.of("/opt/llvm/bin/clangd", "\"" + FOUND + "\""), commands);
    }

    @Test
    public void testConfigureServerWithACheckFileThatNoCheckFoundIsAnError() {
        List<String> commands = new ArrayList<>(List.of(TEMPLATE_COMMAND));
        InstallerContext context = createContext(commands);

        assertFalse(new ConfigureServerTask(null, "test", null, null, "\"${check.file}\"", createDescriptor())
                .run(context));

        assertEquals(List.of(TEMPLATE_COMMAND), commands, "The command should stay");
        assertTrue(context.getStatus().stream().anyMatch(status -> status.level() == Level.SEVERE
                && status.message().contains("${check.file}")), "The error should be told: " + context.getStatus());
    }

    @Test
    public void testTemplateCommandByOs() {
        InstallerTaskRegistry registry = new InstallerTaskRegistry();
        registry.registerFactory("replaceTemplateCommand", new ReplaceTemplateCommandTaskFactory());
        ServerInstallerDescriptor descriptor = registry.loadInstaller("""
                {
                  "name": "test-server",
                  "run": {
                    "replaceTemplateCommand": {
                      "templateCommand": {
                        "windows": "${BASE_DIR}/server.exe",
                        "default": "${BASE_DIR}/server"
                      },
                      "command": "\\"${check.file}\\""
                    }
                  }
                }
                """);
        // Compared as written: ${BASE_DIR} isn't a property of the installer
        String templateCommand = SystemInfo.isWindows ? "${BASE_DIR}/server.exe" : "${BASE_DIR}/server";
        List<String> commands = new ArrayList<>(List.of(templateCommand));
        InstallerContext context = createContext(commands);
        context.putProperty(FileExistsTask.CHECK_FILE_PROPERTY, "/opt/server/server");

        assertTrue(descriptor.getRun().execute(context));

        assertEquals(List.of(templateCommand, "\"/opt/server/server\""), commands);
    }

    @Test
    public void testThePluginRegistersTheTaskForTheBundledInstallers() throws IOException {
        // The registry of the plugin, from its extension point, as for an installer in the settings
        String json = Files.readString(Paths.get("src/main/resources/templates/lsp/clangd/installer.json"));
        ServerInstallerDescriptor descriptor = ServerInstallerManager.getInstance().loadInstaller(json);

        assertInstanceOf(ReplaceTemplateCommandTask.class, descriptor.getCheck().getOnSuccess());
    }

    @Test
    public void testAPassingCheckGivesTheServerTheCommandOfTheFileItFound() throws IOException {
        Path serverDist = Files.createTempDirectory("lsp4ij-server-dist");
        try {
            for (String version : List.of("clangd_19.1.2", "clangd_19.1.10")) {
                Files.createDirectories(serverDist.resolve(version).resolve("bin"));
                Files.createFile(serverDist.resolve(version).resolve("bin").resolve("clangd"));
                Files.createFile(serverDist.resolve(version).resolve("bin").resolve("clangd.exe"));
            }
            String dist = serverDist.toString().replace('\\', '/');
            InstallerTaskRegistry registry = new InstallerTaskRegistry();
            registry.registerFactory("fileExists", new FileExistsTaskFactory());
            registry.registerFactory("replaceTemplateCommand", new ReplaceTemplateCommandTaskFactory());
            ServerInstallerDescriptor descriptor = registry.loadInstaller("""
                    {
                      "name": "clangd",
                      "properties": {
                        "serverDist": "%s"
                      },
                      "check": {
                        "fileExists": {
                          "file": {
                            "windows": "${serverDist}/clangd*/bin/clangd.exe",
                            "default": "${serverDist}/clangd*/bin/clangd"
                          },
                          "onSuccess": {
                            "replaceTemplateCommand": {
                              "templateCommand": "clangd",
                              "command": "\\"${check.file}\\""
                            }
                          }
                        }
                      }
                    }
                    """.formatted(dist));
            String clangd = SystemInfo.isWindows ? "clangd.exe" : "clangd";

            // A server created from the template
            List<String> commands = new ArrayList<>(List.of(TEMPLATE_COMMAND));
            assertTrue(check(descriptor, commands));
            assertEquals(List.of(TEMPLATE_COMMAND, "\"" + dist + "/clangd_19.1.10/bin/" + clangd + "\""), commands);

            // Check installation, in the settings, of a server whose command the user changed
            commands = new ArrayList<>(List.of("/opt/llvm/bin/clangd"));
            assertTrue(check(descriptor, commands));
            assertEquals(List.of("/opt/llvm/bin/clangd"), commands);
        } finally {
            FileUtil.delete(serverDist);
        }
    }

    private static boolean check(@NotNull ServerInstallerDescriptor descriptor, @NotNull List<String> commands) {
        InstallerContext context = createContext(commands);
        descriptor.getProperties().forEach(context::putProperty);
        return descriptor.getCheck().execute(context);
    }

    private static ReplaceTemplateCommandTask createTask(@Nullable String templateCommand, @NotNull String command) {
        return new ReplaceTemplateCommandTask(null, "test", null, null, templateCommand, command, createDescriptor());
    }

    private static ServerInstallerDescriptor createDescriptor() {
        return new ServerInstallerDescriptor("test", false, new InstallerTaskRegistry());
    }

    /**
     * Returns a context whose server has the last of the given commands, and to which a change of the command adds one.
     */
    private static InstallerContext createContext(@NotNull List<String> commands) {
        InstallerContext context = new InstallerContext(null, InstallerContext.InstallerAction.CHECK);
        context.setCommandLineUpdater(new CommandLineUpdater() {
            @Override
            public String getCommandLine() {
                return commands.get(commands.size() - 1);
            }

            @Override
            public void setCommandLine(String commandLine) {
                commands.add(commandLine);
            }
        });
        return context;
    }
}

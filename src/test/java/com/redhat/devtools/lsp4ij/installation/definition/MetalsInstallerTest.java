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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.util.system.CpuArch;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.ConfigureServerTaskFactory;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.FileExistsTaskFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled Metals installer finds and installs Metals with the {@code cs} command of Coursier, or else with its
 * {@code coursier} command, which is the only one that Homebrew's {@code coursier} formula installs. Its commands are
 * run by a fake {@code exec}, which finds on the PATH only the commands that the test gives it, for each OS; the other
 * tasks are LSP4IJ's own.
 */
public class MetalsInstallerTest {

    // The command of the template, which the check runs first
    private static final String TEMPLATE_COMMAND = "\"$USER_HOME$/.lsp4ij/lsp/metals/metals\"";
    // The command that the run gives the server
    private static final String LAUNCHER = "\"${workingDir}/metals\"";

    private final List<String> ran = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final List<String> commands = new ArrayList<>();
    private Path workingDir;

    // The fileExists task resolves the macros of its file with an application service
    @BeforeClass
    public static void startTheApplication() {
        TestApplicationManager.getInstance();
    }

    @Before
    public void createTheWorkingDir() throws IOException {
        workingDir = Files.createTempDirectory("lsp4ij-metals");
    }

    @After
    public void deleteTheWorkingDir() throws IOException {
        FileUtil.delete(workingDir);
    }

    @Test
    public void testTheCheckFindsMetalsInstalledWithCoursierWithoutCs() throws IOException {
        installTheLauncher();

        assertTrue(check("mac", Set.of("coursier")), "The check should find the Metals that coursier installed: " + ran);
        assertEquals(List.of(TEMPLATE_COMMAND, "which cs", "which coursier"), ran);
        assertEquals(List.of(), messages);
    }

    @Test
    public void testTheCheckFindsMetalsInstalledWithCs() throws IOException {
        installTheLauncher();

        assertTrue(check("mac", Set.of("cs")), "The check should find the Metals that cs installed: " + ran);
        assertEquals(List.of(TEMPLATE_COMMAND, "which cs"), ran);
    }

    @Test
    public void testTheCheckFailsWithCoursierWhileMetalsIsNotInstalled() throws IOException {
        assertFalse(check("mac", Set.of("coursier")), "Metals isn't installed: " + ran);
        assertEquals(List.of(), messages, "Coursier is there: the run installs Metals with it");
    }

    @Test
    public void testTheRunInstallsMetalsWithCoursierWithoutCs() throws IOException {
        assertTrue(run("mac", Set.of("coursier")), "The run should install Metals with coursier: " + ran);
        assertEquals(List.of("which cs", "coursier install metals --install-dir ."), ran);
        assertEquals(List.of(LAUNCHER), commands);
    }

    @Test
    public void testTheRunInstallsMetalsWithCsWhenItIsThere() throws IOException {
        assertTrue(run("mac", Set.of("cs", "coursier")), "The run should install Metals with cs: " + ran);
        assertEquals(List.of("which cs", "cs install metals --install-dir ."), ran);
        assertEquals(List.of(LAUNCHER), commands);
    }

    @Test
    public void testOnWindowsTheCheckAndTheRunUseCoursierWithoutCs() throws IOException {
        installTheLauncher();

        assertTrue(check("windows", Set.of("coursier")), "The check should find the Metals that coursier installed: " + ran);
        assertEquals(List.of(TEMPLATE_COMMAND, "where cs", "where coursier"), ran);
        ran.clear();
        assertTrue(run("windows", Set.of("coursier")), "The run should install Metals with coursier: " + ran);
        // cmd runs coursier.bat
        assertEquals(List.of("where cs", "cmd /C coursier install metals --install-dir ."), ran);
    }

    @Test
    public void testWithoutCoursierTheMessageSaysHowToInstallItWithCsOrCoursier() throws IOException {
        assertFalse(check("mac", Set.of()));

        assertEquals(1, messages.size(), "Coursier isn't installed: " + ran);
        String message = messages.get(0);
        for (String text : List.of("cs setup", "brew install coursier", "brew install coursier/formulas/coursier",
                "<code>cs</code> or <code>coursier</code>")) {
            assertTrue(message.contains(text), "The message should say " + text + ": " + message);
        }
    }

    /**
     * Creates the launchers that {@code cs install metals} and {@code coursier install metals} create in the working
     * directory.
     */
    private void installTheLauncher() throws IOException {
        Files.writeString(workingDir.resolve("metals"), "#!/bin/sh");
        Files.writeString(workingDir.resolve("metals.bat"), "@echo off");
    }

    private boolean check(@NotNull String os, @NotNull Set<String> onThePath) throws IOException {
        var descriptor = loadInstaller(os, onThePath);
        return execute(descriptor, descriptor.getCheck());
    }

    private boolean run(@NotNull String os, @NotNull Set<String> onThePath) throws IOException {
        var descriptor = loadInstaller(os, onThePath);
        return execute(descriptor, descriptor.getRun());
    }

    /**
     * Loads the bundled installer, whose Metals goes into the working directory of the test, with the fake exec and
     * showMessage tasks.
     */
    private @NotNull ServerInstallerDescriptor loadInstaller(@NotNull String os,
                                                             @NotNull Set<String> onThePath) throws IOException {
        JsonObject json = JsonParser.parseString(
                Files.readString(Paths.get("src/main/resources/templates/lsp/metals/installer.json")))
                .getAsJsonObject();
        json.getAsJsonObject("properties").addProperty("workingDir", workingDir.toString().replace('\\', '/'));
        var registry = new InstallerTaskRegistry();
        registry.registerFactory("exec", new FakeExecTaskFactory(os, onThePath));
        registry.registerFactory("fileExists", new FileExistsTaskFactory());
        registry.registerFactory("configureServer", new ConfigureServerTaskFactory());
        registry.registerFactory("showMessage", new InstallerTaskFactoryBase() {
            @Override
            protected @NotNull InstallerTask create(@Nullable String id, @Nullable String name,
                                                    @Nullable InstallerTask onFail, @Nullable InstallerTask onSuccess,
                                                    @NotNull JsonObject json,
                                                    @NotNull ServerInstallerDescriptor descriptor) {
                JsonElement content = json.get("content");
                String text = content.isJsonArray()
                        ? StreamSupport.stream(content.getAsJsonArray().spliterator(), false)
                        .map(JsonElement::getAsString)
                        .collect(Collectors.joining(" "))
                        : content.getAsString();
                return new InstallerTask(id, name, onFail, onSuccess, descriptor) {
                    @Override
                    public boolean run(@NotNull InstallerContext context) {
                        messages.add(text);
                        return false;
                    }
                };
            }
        });
        return registry.loadInstaller(json.toString());
    }

    private boolean execute(@NotNull ServerInstallerDescriptor descriptor, @NotNull InstallerTask task) {
        var context = new InstallerContext(null, InstallerContext.InstallerAction.CHECK_AND_RUN);
        context.setCommandLineUpdater(new CommandLineUpdater() {
            @Override
            public String getCommandLine() {
                return TEMPLATE_COMMAND;
            }

            @Override
            public void setCommandLine(String commandLine) {
                // As written, with ${workingDir} put back, so that it doesn't depend on the temp dir
                commands.add(commandLine.replace(workingDir.toString().replace('\\', '/'), "${workingDir}"));
            }
        });
        context.putProperty("server.command", context.getCommandLineUpdater().getCommandLine());
        descriptor.getProperties().forEach(context::putProperty);
        return task.execute(context);
    }

    /**
     * An exec task which records its command, for the given OS, and succeeds for {@code which} or {@code where} of a
     * command on the PATH, and for a command on the PATH, as {@code cmd /C coursier}. The current command of the
     * server, the Metals launcher, never starts.
     */
    private final class FakeExecTaskFactory extends InstallerTaskFactoryBase {

        private final String os;
        private final Set<String> onThePath;

        FakeExecTaskFactory(@NotNull String os, @NotNull Set<String> onThePath) {
            this.os = os;
            this.onThePath = onThePath;
        }

        @Override
        protected @NotNull InstallerTask create(@Nullable String id, @Nullable String name,
                                                @Nullable InstallerTask onFail, @Nullable InstallerTask onSuccess,
                                                @NotNull JsonObject json,
                                                @NotNull ServerInstallerDescriptor descriptor) {
            String command = getStringFromOs(json, "command", os, CpuArch.ARM64, "aarch64");
            return new InstallerTask(id, name, onFail, onSuccess, descriptor) {
                @Override
                public boolean run(@NotNull InstallerContext context) {
                    String resolved = context.resolveValues(command);
                    ran.add(resolved);
                    List<String> words = List.of(resolved.split(" "));
                    String program = words.get(0).equals("cmd") ? words.get(2) : words.get(0);
                    if (program.equals("which") || program.equals("where")) {
                        return onThePath.contains(words.get(1));
                    }
                    return onThePath.contains(program);
                }
            };
        }
    }
}

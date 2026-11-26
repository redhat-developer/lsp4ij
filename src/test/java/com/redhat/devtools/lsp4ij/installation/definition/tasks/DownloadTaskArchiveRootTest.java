/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.TestApplicationManager;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskRegistry;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled cpp_ls installer, which downloads an archive whose root entry is {@code ./}, as the {@code .tar.gz} of
 * cpp_ls, gives {@code ${output.dir}} and the command of its server no {@code /./}, with or without
 * {@code stripRootFolder}, and an archive of one root folder, with or without {@code ./} before it, still adds the name
 * of that folder to {@code ${output.dir}} (#2139). The archives are built in a temp folder and downloaded from it.
 */
public class DownloadTaskArchiveRootTest {

    // The file of the server, output.file.name of the installer, for the OS that the test runs on
    private static final String SERVER = SystemInfo.isWindows ? "cpp_ls.exe" : "cpp_ls";
    private static final String ROOT_FOLDER = "cpp_ls-1.0";

    private final List<String> commands = new ArrayList<>();
    private Path dir;
    private Path serverDist;

    // The download resolves the macros of its output dir with an application service
    @BeforeClass
    public static void initPlatform() {
        TestApplicationManager.getInstance();
    }

    @Before
    public void createDir() throws IOException {
        dir = Files.createTempDirectory("lsp4ij-archive-root");
        serverDist = dir.resolve("lsp").resolve("cpp_ls");
    }

    @After
    public void deleteDir() throws IOException {
        NioFiles.deleteRecursively(dir);
    }

    @Test
    public void testATarGzWhoseRootEntryIsDotAddsNothingToTheOutputDir() throws IOException {
        Path asset = tarGz("cpp_ls-darwin-x64.tar.gz", "./", "./" + SERVER);

        assertInstalled(asset, false, serverDist);
    }

    @Test
    public void testAZipWhoseRootEntryIsDotAddsNothingToTheOutputDir() throws IOException {
        Path asset = zip("cpp_ls-win32-x64.zip", "./", "./" + SERVER);

        assertInstalled(asset, false, serverDist);
    }

    @Test
    public void testATarGzWhoseRootEntryIsDotInstallsWithStripRootFolder() throws IOException {
        Path asset = tarGz("cpp_ls-darwin-x64.tar.gz", "./", "./" + SERVER);

        assertInstalled(asset, true, serverDist);
    }

    @Test
    public void testAZipWhoseRootEntryIsDotInstallsWithStripRootFolder() throws IOException {
        Path asset = zip("cpp_ls-win32-x64.zip", "./", "./" + SERVER);

        assertInstalled(asset, true, serverDist);
    }

    @Test
    public void testAnArchiveOfOneRootFolderAddsItsName() throws IOException {
        Path asset = tarGz("cpp_ls-darwin-x64.tar.gz", ROOT_FOLDER + "/", ROOT_FOLDER + "/" + SERVER);

        assertInstalled(asset, false, serverDist.resolve(ROOT_FOLDER));
    }

    @Test
    public void testATarGzOfOneRootFolderUnderDotAddsItsName() throws IOException {
        Path asset = tarGz("cpp_ls-darwin-x64.tar.gz", "./", "./" + ROOT_FOLDER + "/",
                "./" + ROOT_FOLDER + "/" + SERVER);

        assertInstalled(asset, false, serverDist.resolve(ROOT_FOLDER));
    }

    @Test
    public void testAZipOfOneRootFolderUnderDotAddsItsName() throws IOException {
        Path asset = zip("cpp_ls-win32-x64.zip", "./", "./" + ROOT_FOLDER + "/", "./" + ROOT_FOLDER + "/" + SERVER);

        assertInstalled(asset, false, serverDist.resolve(ROOT_FOLDER));
    }

    @Test
    public void testStripRootFolderStripsOneRootFolderUnderDot() throws IOException {
        Path asset = tarGz("cpp_ls-darwin-x64.tar.gz", "./", "./" + ROOT_FOLDER + "/",
                "./" + ROOT_FOLDER + "/" + SERVER);

        assertInstalled(asset, true, serverDist);
        assertFalse(Files.exists(serverDist.resolve(ROOT_FOLDER)), "the root folder must be stripped");
    }

    @Test
    public void testStripRootFolderReplacesTheFileAndTheFolderOfAnEarlierInstall() throws IOException {
        // As an earlier install left them: the server, and a folder with files that the new release no longer has
        Files.createDirectories(serverDist.resolve("lib").resolve("nested"));
        Files.writeString(serverDist.resolve(SERVER), "old server");
        Files.writeString(serverDist.resolve("lib").resolve("old.jar"), "old");
        Files.writeString(serverDist.resolve("lib").resolve("nested").resolve("old.txt"), "old");
        Path asset = tarGz("cpp_ls-darwin-x64.tar.gz", ROOT_FOLDER + "/", ROOT_FOLDER + "/" + SERVER,
                ROOT_FOLDER + "/lib/", ROOT_FOLDER + "/lib/new.jar");

        assertInstalled(asset, true, serverDist);
        assertEquals("server", Files.readString(serverDist.resolve("lib").resolve("new.jar")),
                "the folder of the new release must be installed");
        assertFalse(Files.exists(serverDist.resolve("lib").resolve("old.jar")),
                "the folder of the earlier install must be replaced, not merged");
        assertFalse(Files.exists(serverDist.resolve("lib").resolve("nested")),
                "the folders inside the folder of the earlier install must be deleted too");
    }

    /**
     * Runs the bundled cpp_ls installer, which downloads the given archive into a temp folder, with or without
     * {@code stripRootFolder}, and asserts that it installs the server into the given folder, which is its
     * {@code ${output.dir}}, and gives it the command of the server file in that folder.
     */
    private void assertInstalled(@NotNull Path asset, boolean stripRootFolder, @NotNull Path expectedDir)
            throws IOException {
        var context = new InstallerContext(null, InstallerContext.InstallerAction.RUN);
        context.setCommandLineUpdater(new CommandLineUpdater() {
            @Override
            public String getCommandLine() {
                return SERVER;
            }

            @Override
            public void setCommandLine(String commandLine) {
                commands.add(commandLine);
            }
        });
        var descriptor = loadInstaller(asset, stripRootFolder);
        descriptor.getProperties().forEach(context::putProperty);

        assertTrue(descriptor.getRun().execute(context), "the install of " + asset.getFileName() + " must succeed: "
                + context.getStatus());

        String expected = slashes(expectedDir);
        assertEquals(expected, context.getProperty("output.dir"));
        assertEquals(List.of("\"" + expected + "/" + SERVER + "\""), commands);
        assertFalse(commands.get(0).contains("/./"), commands.get(0));
        assertEquals("server", Files.readString(expectedDir.resolve(SERVER)), "the server must be in " + expectedDir);
    }

    /**
     * Loads the bundled cpp_ls installer, whose server goes into the temp folder of the test, and which downloads the
     * given archive instead of the asset of the latest GitHub release of cpp_ls.
     */
    private @NotNull ServerInstallerDescriptor loadInstaller(@NotNull Path asset,
                                                             boolean stripRootFolder) throws IOException {
        JsonObject json = JsonParser.parseString(
                        Files.readString(Paths.get("src/main/resources/templates/lsp/cpp_ls/installer.json")))
                .getAsJsonObject();
        json.getAsJsonObject("properties").addProperty("serverDist", slashes(serverDist));
        JsonObject download = json.getAsJsonObject("run").getAsJsonObject("download");
        download.remove("github");
        download.addProperty("url", asset.toUri().toString());
        if (stripRootFolder) {
            download.getAsJsonObject("output").addProperty("stripRootFolder", true);
        }
        var registry = new InstallerTaskRegistry();
        registry.registerFactory("download", new DownloadTaskFactory());
        registry.registerFactory("configureServer", new ConfigureServerTaskFactory());
        return registry.loadInstaller(json);
    }

    /**
     * Returns a {@code .tar.gz} with the given entries, in order, in the temp folder: a folder for a name which ends
     * with a slash, else a file with the content {@code server}.
     */
    private @NotNull Path tarGz(@NotNull String fileName, @NotNull String... entries) throws IOException {
        Path asset = Files.createDirectories(dir.resolve("assets")).resolve(fileName);
        try (var tar = new TarArchiveOutputStream(new GZIPOutputStream(Files.newOutputStream(asset)))) {
            for (String name : entries) {
                var entry = new TarArchiveEntry(name);
                byte[] content = content(name);
                entry.setSize(content.length);
                tar.putArchiveEntry(entry);
                tar.write(content);
                tar.closeArchiveEntry();
            }
        }
        return asset;
    }

    /**
     * Returns a {@code .zip} with the given entries, in order, in the temp folder: a folder for a name which ends with
     * a slash, else a file with the content {@code server}.
     */
    private @NotNull Path zip(@NotNull String fileName, @NotNull String... entries) throws IOException {
        Path asset = Files.createDirectories(dir.resolve("assets")).resolve(fileName);
        try (var zip = new ZipOutputStream(Files.newOutputStream(asset))) {
            for (String name : entries) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(content(name));
                zip.closeEntry();
            }
        }
        return asset;
    }

    private static byte[] content(@NotNull String entryName) {
        return entryName.endsWith("/") ? new byte[0] : "server".getBytes(StandardCharsets.UTF_8);
    }

    private static @NotNull String slashes(@NotNull Path path) {
        return path.toString().replace('\\', '/');
    }
}

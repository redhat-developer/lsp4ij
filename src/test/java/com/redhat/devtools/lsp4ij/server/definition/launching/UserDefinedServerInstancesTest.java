/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tests a user-defined language server whose workspace folder configuration asks for several instances.
 */
public class UserDefinedServerInstancesTest extends BasePlatformTestCase {

    private static final String MARKER = "lsp4ij-test-marker";
    private static final List<ServerMappingSettings> MAPPINGS =
            List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.user-defined"), "plaintext"));

    private UserDefinedLanguageServerDefinition definition;

    @Override
    protected void tearDown() throws Exception {
        try {
            if (definition != null) {
                LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), definition);
            }
        } finally {
            super.tearDown();
        }
    }

    public void testOneInstancePerProjectIsTheDefault() {
        addDefinition(null, null);

        assertFalse(definition.hasServerInstances());
    }

    public void testTheConfigurationAsksForServerInstances() {
        addDefinition(null, "{\"serverInstances\": {}}");

        assertTrue(definition.hasServerInstances());
    }

    public void testAChangedConfigurationIsUsed() {
        addDefinition(null, null);
        assertFalse(definition.hasServerInstances());

        update("{\"serverInstances\": {}}");
        assertTrue(definition.hasServerInstances());

        update("{}");
        assertFalse(definition.hasServerInstances());
    }

    public void testTheConfigurationDecidesTheRootOfAFile() throws IOException {
        var root = localDirectory();
        Files.writeString(root.toNioPath().resolve(MARKER), "");
        var file = localFile(root, "src/one.user-defined");
        addDefinition(null, "{\"serverInstances\": {\"markers\": [\"" + MARKER + "\"]}}");

        var folder = definition.getServerInstanceRoot(file, getProject());

        assertNotNull(folder);
        assertEquals(root.getName(), folder.getName());
    }

    public void testAnInstanceRunsInItsRootWithoutAWorkingDirectory() throws IOException {
        var folder = localDirectory();
        addDefinition(null, "{\"serverInstances\": {}}");

        assertEquals(new File(folder.getPath()), workDirectoryOfInstance(folder));
    }

    public void testAnInstanceRunsInItsRootWithABlankWorkingDirectory() throws IOException {
        var folder = localDirectory();
        addDefinition(" ", "{\"serverInstances\": {}}");

        assertEquals(new File(folder.getPath()), workDirectoryOfInstance(folder));
    }

    public void testAConfiguredWorkingDirectoryAppliesToAnInstance() throws IOException {
        var folder = localDirectory();
        var workingDirectory = localDirectory();
        addDefinition(workingDirectory.getPath(), "{\"serverInstances\": {}}");

        assertEquals(new File(workingDirectory.getPath()), workDirectoryOfInstance(folder));
    }

    public void testAnInstanceWithARootInAnArchiveGetsNoWorkingDirectory() throws IOException {
        var jar = localDirectory().toNioPath().resolve("archive.jar");
        try (var archive = new ZipOutputStream(Files.newOutputStream(jar))) {
            archive.putNextEntry(new ZipEntry("one.user-defined"));
            archive.closeEntry();
        }
        var localJar = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jar);
        assertNotNull(localJar);
        var folder = JarFileSystem.getInstance().getJarRootForLocalFile(localJar);
        assertNotNull(folder);
        addDefinition(null, "{\"serverInstances\": {}}");

        assertNull(workDirectoryOfInstance(folder));
    }

    public void testOneInstancePerProjectGetsNoWorkingDirectoryOfItsOwn() {
        addDefinition(null, null);

        var provider = (OSProcessStreamConnectionProvider) definition.createConnectionProvider(getProject());

        assertNull(provider.getCommandLine().getWorkDirectory());
    }

    private @Nullable File workDirectoryOfInstance(@NotNull VirtualFile folder) {
        var instanceRoot = new WorkspaceFolder(FileUriSupport.DEFAULT.toString(folder), folder.getName());
        var provider = (OSProcessStreamConnectionProvider) definition.createConnectionProvider(getProject(), instanceRoot);
        return provider.getCommandLine().getWorkDirectory();
    }

    private void addDefinition(@Nullable String workingDir, @Nullable String configuration) {
        definition = new UserDefinedLanguageServerDefinition("user-defined-instances", null, "User-defined per folder", null, null,
                "my-server --stdio", workingDir, Map.of(), false, null, false, null, null, null, null, null, configuration);
        LanguageServersRegistry.getInstance().addServerDefinition(getProject(), definition, MAPPINGS);
    }

    private void update(@Nullable String configuration) {
        LanguageServersRegistry.getInstance().updateServerDefinition(new LanguageServersRegistry.UpdateServerDefinitionRequest(
                getProject(), definition, definition.getDisplayName(), null, definition.getCommandLine(), null, Map.of(), false,
                MAPPINGS, null, null, configuration, definition.getTransport()), false);
    }

    private @NotNull VirtualFile localDirectory() throws IOException {
        Path directory = Files.createTempDirectory("user-defined-instances").toRealPath();
        Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(directory.toFile()));
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), directory.toString());
        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory);
        assertNotNull(file);
        return file;
    }

    private static @NotNull VirtualFile localFile(@NotNull VirtualFile directory, @NotNull String path) throws IOException {
        Path file = directory.toNioPath().resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");
        var virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
        assertNotNull(virtualFile);
        return virtualFile;
    }
}

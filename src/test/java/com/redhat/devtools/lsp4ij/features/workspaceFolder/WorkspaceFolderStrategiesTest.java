/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.workspaceFolder;

import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests which workspace folders the built-in strategies give. With one language server instance per folder, they also
 * decide which instances run.
 */
public class WorkspaceFolderStrategiesTest extends BasePlatformTestCase {

    private static final String MARKER = "lsp4ij-test-marker";

    public void testBuiltInStrategiesKeepOneInstancePerProject() {
        assertFalse(new ProjectWorkspaceFolderStrategy().isServerInstancePerFolder());
        assertFalse(new SourceRootsWorkspaceFolderStrategy().isServerInstancePerFolder());
        assertFalse(new MarkersWorkspaceFolderStrategy(MARKER).isServerInstancePerFolder());
        assertFalse(new ConfigurableWorkspaceFolderStrategy().isServerInstancePerFolder());
    }

    public void testProjectStrategyGivesTheContentRootOfAFile() {
        var file = myFixture.addFileToProject("a/one.txt", "").getVirtualFile();

        assertFolder(contentRoot(), folderOf(new ProjectWorkspaceFolderStrategy(), file));
    }

    public void testProjectStrategyGivesTheLocalContentRootOfAFile() throws IOException {
        var root = localDirectory();
        var file = localFile(root, "a/one.txt");
        PsiTestUtil.addContentRoot(getModule(), root);
        try {
            assertFolder(root, folderOf(new ProjectWorkspaceFolderStrategy(), file));
        } finally {
            PsiTestUtil.removeContentEntry(getModule(), root);
        }
    }

    public void testProjectStrategyGivesNoFolderForAFileOutsideOfTheProject() throws IOException {
        var file = localFile(localDirectory(), "a/one.txt");

        assertNull(folderOf(new ProjectWorkspaceFolderStrategy(), file));
    }

    public void testSourceRootsStrategyGivesTheSourceRootOfAFile() {
        var file = myFixture.addFileToProject("a/one.txt", "").getVirtualFile();

        assertFolder(sourceRoot(), folderOf(new SourceRootsWorkspaceFolderStrategy(), file));
    }

    public void testSourceRootsStrategyGivesTheDeepestLocalSourceRootOfAFile() throws IOException {
        var root = localDirectory();
        var inOuter = localFile(root, "src/one.txt");
        var inInner = localFile(root, "src/generated/two.txt");
        var outer = inOuter.getParent();
        var inner = inInner.getParent();
        PsiTestUtil.addContentRoot(getModule(), root);
        try {
            PsiTestUtil.addSourceRoot(getModule(), outer);
            PsiTestUtil.addSourceRoot(getModule(), inner);

            assertFolder(outer, folderOf(new SourceRootsWorkspaceFolderStrategy(), inOuter));
            assertFolder(inner, folderOf(new SourceRootsWorkspaceFolderStrategy(), inInner));
        } finally {
            PsiTestUtil.removeContentEntry(getModule(), root);
        }
    }

    public void testSourceRootsStrategyGivesNoFolderForAFileOutsideOfASourceRoot() throws IOException {
        var root = localDirectory();
        var file = localFile(root, "docs/one.txt");
        PsiTestUtil.addContentRoot(getModule(), root);
        try {
            assertNull(folderOf(new SourceRootsWorkspaceFolderStrategy(), file));
        } finally {
            PsiTestUtil.removeContentEntry(getModule(), root);
        }
    }

    public void testMarkersStrategyGivesTheNearestFolderWithAMarker() throws IOException {
        var root = localDirectory();
        localFile(root, MARKER);
        localFile(root, "nested/" + MARKER);
        var inRoot = localFile(root, "docs/one.txt");
        var inNested = localFile(root, "nested/src/two.txt");
        var strategy = new MarkersWorkspaceFolderStrategy(MARKER);

        assertFolder(root, folderOf(strategy, inRoot));
        assertFolder(root.findChild("nested"), folderOf(strategy, inNested));
    }

    public void testMarkersStrategyFindsAMarkerDirectory() throws IOException {
        var root = localDirectory();
        localFile(root, MARKER + "/inside.txt");
        var file = localFile(root, "src/one.txt");

        assertFolder(root, folderOf(new MarkersWorkspaceFolderStrategy(MARKER), file));
    }

    public void testMarkersStrategyGivesNoFolderWithoutAMarker() throws IOException {
        var file = localFile(localDirectory(), "src/one.txt");

        assertNull(folderOf(new MarkersWorkspaceFolderStrategy(MARKER), file));
    }

    public void testMarkersStrategyWorksOutsideOfTheLocalFileSystem() {
        var withMarker = MockVirtualFile.file("one.txt");
        var withoutMarker = MockVirtualFile.file("two.txt");
        var folder = MockVirtualFile.dir("a", MockVirtualFile.file(MARKER), MockVirtualFile.dir("src", withMarker));
        MockVirtualFile.dir("b", withoutMarker);
        var strategy = new MarkersWorkspaceFolderStrategy(MARKER);

        assertFolder(folder, folderOf(strategy, withMarker));
        assertNull(folderOf(strategy, withoutMarker));
    }

    public void testRootsWorkOutsideOfTheLocalFileSystem() {
        var inInner = MockVirtualFile.file("one.txt");
        var inOuter = MockVirtualFile.file("two.txt");
        var outside = MockVirtualFile.file("three.txt");
        var inner = MockVirtualFile.dir("inner", inInner);
        var outer = MockVirtualFile.dir("outer", inner, inOuter);
        MockVirtualFile.dir("other", outside);
        var strategy = new ProjectWorkspaceFolderStrategy() {
            @Override
            public @NotNull List<VirtualFile> getRoots(@NotNull Project project) {
                return List.of(outer, inner);
            }
        };

        assertFolder(inner, folderOf(strategy, inInner));
        assertFolder(outer, folderOf(strategy, inOuter));
        assertNull(folderOf(strategy, outside));
    }

    public void testRootTypeNoneGivesNoFolder() {
        var file = myFixture.addFileToProject("a/one.txt", "").getVirtualFile();
        var strategy = new ConfigurableWorkspaceFolderStrategy();
        strategy.configure("{\"rootType\": \"NONE\"}");

        assertNull(folderOf(strategy, file));
        assertEmpty(strategy.getWorkspaceFolders(getProject(), FileUriSupport.DEFAULT));
    }

    public void testConfigurableStrategyUsesTheConfiguredRootType() {
        var strategy = new ConfigurableWorkspaceFolderStrategy();
        assertEquals(RootType.PROJECT_BASE, strategy.getRootType());

        strategy.configure("{\"rootType\": \"SOURCE_ROOTS\"}");

        assertEquals(RootType.SOURCE_ROOTS, strategy.getRootType());
        assertFalse(strategy.isLazy());
        assertTrue(strategy.sendAllFoldersOnInitialization());
    }

    public void testConfigurableStrategyUsesTheConfiguredMarkers() throws IOException {
        var root = localDirectory();
        localFile(root, MARKER);
        var file = localFile(root, "src/one.txt");
        var strategy = new ConfigurableWorkspaceFolderStrategy();

        strategy.configure("{\"markers\": [\"" + MARKER + "\"]}");

        assertEquals(RootType.MARKERS, strategy.getRootType());
        assertEquals(List.of(MARKER), strategy.getMarkers());
        assertTrue(strategy.isLazy());
        assertFalse(strategy.sendAllFoldersOnInitialization());
        assertFolder(root, folderOf(strategy, file));
    }

    public void testConfigurableStrategyGoesBackToItsDefaults() {
        var strategy = new ConfigurableWorkspaceFolderStrategy();
        strategy.configure("{\"rootType\": \"SOURCE_ROOTS\", \"lazy\": true}");
        assertTrue(strategy.isLazy());

        strategy.configure(null);

        assertEquals(RootType.PROJECT_BASE, strategy.getRootType());
        assertFalse(strategy.isLazy());
        assertNull(strategy.getMarkers());
    }

    public void testConfigurableStrategyKeepsItsConfigurationWithInvalidJson() {
        var strategy = new ConfigurableWorkspaceFolderStrategy();
        strategy.configure("{\"rootType\": \"SOURCE_ROOTS\"}");

        strategy.configure("{not json");

        assertEquals(RootType.SOURCE_ROOTS, strategy.getRootType());
    }

    public void testConfigurableStrategyUsesTheProjectBaseWithAnUnknownRootType() {
        var strategy = new ConfigurableWorkspaceFolderStrategy();
        strategy.configure("{\"rootType\": \"SOURCE_ROOTS\"}");

        strategy.configure("{\"rootType\": \"UNKNOWN\"}");

        assertEquals(RootType.PROJECT_BASE, strategy.getRootType());
    }

    public void testLazyStrategiesSendNoFoldersOnInitialization() {
        var eager = new ProjectWorkspaceFolderStrategy();
        var lazy = new ProjectWorkspaceFolderStrategy(true);

        assertTrue(eager.sendAllFoldersOnInitialization());
        assertNotEmpty(eager.getInitialWorkspaceFolders(getProject(), FileUriSupport.DEFAULT));
        assertFalse(lazy.sendAllFoldersOnInitialization());
        assertEmpty(lazy.getInitialWorkspaceFolders(getProject(), FileUriSupport.DEFAULT));
        assertNotEmpty(lazy.getWorkspaceFolders(getProject(), FileUriSupport.DEFAULT));
    }

    private @Nullable WorkspaceFolder folderOf(@NotNull WorkspaceFolderStrategy strategy, @NotNull VirtualFile file) {
        return strategy.getWorkspaceFolderForFile(file, getProject(), FileUriSupport.DEFAULT);
    }

    private static void assertFolder(@Nullable VirtualFile expected, @Nullable WorkspaceFolder folder) {
        assertNotNull(expected);
        assertNotNull(folder);
        assertEquals(FileUriSupport.DEFAULT.toString(expected), folder.getUri());
        assertEquals(expected.getName(), folder.getName());
    }

    private @NotNull VirtualFile contentRoot() {
        return ModuleRootManager.getInstance(getModule()).getContentRoots()[0];
    }

    private @NotNull VirtualFile sourceRoot() {
        return ModuleRootManager.getInstance(getModule()).getSourceRoots()[0];
    }

    private @NotNull VirtualFile localDirectory() throws IOException {
        Path directory = Files.createTempDirectory("workspace-folders").toRealPath();
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

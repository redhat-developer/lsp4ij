/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.ConfigurableWorkspaceFolderStrategy;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the preview of the workspace folders tab with one language server instance per folder.
 */
public class WorkspaceFoldersPanelTest extends BasePlatformTestCase {

    private WorkspaceFoldersPanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        panel = new WorkspaceFoldersPanel(getProject(), new ConfigurableWorkspaceFolderStrategy());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Disposer.dispose(panel);
        } finally {
            super.tearDown();
        }
    }

    public void testEachRootIsAnInstance() throws IOException {
        var root = localSourceRoot();
        try {
            configure("{\"serverInstances\": {\"rootType\": \"SOURCE_ROOTS\"}}");

            assertTrue(rows().toString(), rows().stream().anyMatch(row ->
                    row.contains(root.getPath()) && row.contains("(own language server instance)")));
        } finally {
            PsiTestUtil.removeContentEntry(getModule(), root);
        }
    }

    public void testAFolderGoesWithTheInitializeRequestWithoutInstances() throws IOException {
        var root = localSourceRoot();
        try {
            configure("{\"rootType\": \"SOURCE_ROOTS\"}");

            assertTrue(rows().toString(), rows().stream().anyMatch(row ->
                    row.contains(root.getPath()) && row.contains("(initialize request)")));
        } finally {
            PsiTestUtil.removeContentEntry(getModule(), root);
        }
    }

    public void testTheInitializeModeDoesNotApplyWithInstances() throws IOException {
        var root = localSourceRoot();
        try {
            configure("{\"serverInstances\": {\"rootType\": \"SOURCE_ROOTS\"}, \"lazy\": true}");
            var checkBox = UIUtil.findComponentOfType(panel, JBCheckBox.class);
            assertNotNull(checkBox);
            assertFalse(checkBox.isEnabled());

            checkBox.setSelected(true);
            configure("{\"serverInstances\": {\"rootType\": \"SOURCE_ROOTS\"}, \"lazy\": true}");

            assertTrue(rows().toString(), rows().stream().anyMatch(row ->
                    row.contains(root.getPath()) && row.contains("(own language server instance)")));
        } finally {
            PsiTestUtil.removeContentEntry(getModule(), root);
        }
    }

    public void testTheRootOfATestedFileIsAnInstance() throws IOException {
        var directory = localDirectory();
        Files.writeString(directory.resolve("lsp4ij-test-marker"), "");
        var folder = localFile(directory);
        var json = "{\"serverInstances\": {\"markers\": [\"lsp4ij-test-marker\"]}}";
        usePanelWith(json);

        test(localFile(Files.writeString(directory.resolve("one.txt"), "")));

        PlatformTestUtil.waitWithEventsDispatching("The preview must show the folder", () -> rows().stream().anyMatch(row ->
                row.contains(folder.getPath()) && row.contains("(own language server instance)")), 5);

        // A changed configuration tests the file again
        configure(json);

        assertTrue(rows().toString(), rows().stream().anyMatch(row ->
                row.contains(folder.getPath()) && row.contains("(own language server instance)")));
    }

    public void testTheRowOfAnInstanceIsNoLink() throws IOException {
        var root = localSourceRoot();
        try {
            configure("{\"serverInstances\": {\"rootType\": \"SOURCE_ROOTS\"}}");

            assertEquals(Cursor.DEFAULT_CURSOR, cursorOverTheRowOf(root));
        } finally {
            PsiTestUtil.removeContentEntry(getModule(), root);
        }
    }

    public void testTheRowOfAFolderIsALink() throws IOException {
        var root = localSourceRoot();
        try {
            configure("{\"rootType\": \"SOURCE_ROOTS\"}");

            assertEquals(Cursor.HAND_CURSOR, cursorOverTheRowOf(root));
        } finally {
            PsiTestUtil.removeContentEntry(getModule(), root);
        }
    }

    public void testAFolderInsideAnInstanceIsARowBelowIt() throws IOException {
        var directory = localDirectory();
        Files.writeString(directory.resolve("instance-marker"), "");
        Files.createDirectories(directory.resolve("src"));
        Files.writeString(directory.resolve("src/folder-marker"), "");
        var file = localFile(Files.writeString(directory.resolve("src/one.txt"), ""));
        usePanelWith("{\"serverInstances\": {\"markers\": [\"instance-marker\"]}, \"markers\": [\"folder-marker\"]}");

        test(file);

        PlatformTestUtil.waitWithEventsDispatching("The preview must show the file", () -> rows().size() == 3, 5);
        var rows = rows();
        assertTrue(rows.toString(), rows.get(0).contains(localFile(directory).getPath()) && rows.get(0).contains("(own language server instance)"));
        assertTrue(rows.toString(), rows.get(1).contains(file.getParent().getPath()) && rows.get(1).contains("(workspace/didChangeWorkspaceFolders notification)"));
        assertTrue(rows.toString(), rows.get(2).contains("one.txt"));
    }

    public void testAFolderOutsideAnInstanceGivesItsRoot() throws IOException {
        var directory = localDirectory();
        Files.writeString(directory.resolve("folder-marker"), "");
        Files.createDirectories(directory.resolve("instance"));
        Files.writeString(directory.resolve("instance/instance-marker"), "");
        var file = localFile(Files.writeString(directory.resolve("instance/one.txt"), ""));
        usePanelWith("{\"serverInstances\": {\"markers\": [\"instance-marker\"]}, \"markers\": [\"folder-marker\"]}");

        test(file);

        PlatformTestUtil.waitWithEventsDispatching("The preview must show the file", () -> rows().size() == 2, 5);
        var rows = rows();
        assertTrue(rows.toString(), rows.get(0).contains(file.getParent().getPath()) && rows.get(0).contains("(own language server instance)"));
        assertTrue(rows.toString(), rows.get(1).contains("one.txt"));
    }

    public void testRemovingTheLastFileOfAFolderRemovesItsInstanceToo() throws IOException {
        var directory = localDirectory();
        Files.writeString(directory.resolve("instance-marker"), "");
        Files.createDirectories(directory.resolve("src"));
        Files.writeString(directory.resolve("src/folder-marker"), "");
        var file = localFile(Files.writeString(directory.resolve("src/one.txt"), ""));
        usePanelWith("{\"serverInstances\": {\"markers\": [\"instance-marker\"]}, \"markers\": [\"folder-marker\"]}");
        test(file);
        PlatformTestUtil.waitWithEventsDispatching("The preview must show the file", () -> rows().size() == 3, 5);

        panel.getWorkspaceFoldersTree().setSelectionRow(2);
        panel.removeSelectedFile();

        assertEmpty(rows());
    }

    public void testAPreviewWithoutFilesSaysThatTheInstancesComeWithThem() {
        configure("{\"serverInstances\": {\"markers\": [\"lsp4ij-test-marker\"]}}");

        assertTrue(((Tree) panel.getWorkspaceFoldersTree()).getEmptyText().toString(),
                ((Tree) panel.getWorkspaceFoldersTree()).getEmptyText().toString().contains("Open a file"));
    }

    public void testAPreviewWithRootTypeNoneSaysThatThereAreNoFolders() {
        configure("{\"rootType\": \"NONE\"}");

        assertTrue(((Tree) panel.getWorkspaceFoldersTree()).getEmptyText().toString(),
                ((Tree) panel.getWorkspaceFoldersTree()).getEmptyText().toString().contains("No workspace folders"));
    }

    public void testATestedFileWithoutInstancesKeepsItsRow() throws IOException {
        var directory = localDirectory();
        Files.writeString(directory.resolve("folder-marker"), "");
        var file = localFile(Files.writeString(directory.resolve("one.txt"), ""));
        usePanelWith("{\"markers\": [\"folder-marker\"]}");

        test(file);

        PlatformTestUtil.waitWithEventsDispatching("The preview must show the file", () -> rows().size() == 2, 5);
        assertTrue(rows().toString(), rows().get(0).contains("(initialize request)"));
    }

    public void testAFileWithoutARootGetsNoInstance() throws IOException {
        configure("{\"serverInstances\": {}}");

        test(localFileOutsideOfTheProject());

        PlatformTestUtil.waitWithEventsDispatching("The preview must show the file",
                () -> rows().stream().anyMatch(row -> row.contains("(No root: no language server instance)")), 5);
    }

    public void testAFileWithoutAFolderHasNoRootWithoutInstances() throws IOException {
        configure("{}");

        test(localFileOutsideOfTheProject());

        PlatformTestUtil.waitWithEventsDispatching("The preview must show the file",
                () -> rows().stream().anyMatch(row -> row.contains("(No root)")), 5);
    }

    /**
     * Configured before the panel exists, so that no update of the panel is pending when a file gets its node.
     */
    private void usePanelWith(@NotNull String json) {
        var strategy = new ConfigurableWorkspaceFolderStrategy();
        strategy.configure(json);
        Disposer.dispose(panel);
        panel = new WorkspaceFoldersPanel(getProject(), strategy);
    }

    private void configure(@NotNull String json) {
        panel.setJsonConfiguration(json);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    private void test(@NotNull VirtualFile file) {
        panel.testFile(file);
    }

    private int cursorOverTheRowOf(@NotNull VirtualFile folder) {
        var row = ContainerUtil.indexOf(rows(), text -> text.contains(folder.getPath()));
        assertTrue(rows().toString(), row >= 0);
        var tree = panel.getWorkspaceFoldersTree();
        var bounds = tree.getRowBounds(row);
        var moved = new MouseEvent(tree, MouseEvent.MOUSE_MOVED, 0, 0, bounds.x + 1, bounds.y + 1, 0, false);
        for (var listener : tree.getMouseMotionListeners()) {
            listener.mouseMoved(moved);
        }
        return tree.getCursor().getType();
    }

    private @NotNull List<String> rows() {
        var tree = panel.getWorkspaceFoldersTree();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        var rows = new ArrayList<String>();
        for (int row = 0; row < tree.getRowCount(); row++) {
            TreePath path = tree.getPathForRow(row);
            var component = tree.getCellRenderer().getTreeCellRendererComponent(tree, path.getLastPathComponent(), false, false, false, row, false);
            rows.add(component.toString());
        }
        return rows;
    }

    private @NotNull VirtualFile localSourceRoot() throws IOException {
        var root = localFile(localDirectory());
        PsiTestUtil.addContentRoot(getModule(), root);
        PsiTestUtil.addSourceRoot(getModule(), root);
        return root;
    }

    private @NotNull VirtualFile localFileOutsideOfTheProject() throws IOException {
        return localFile(Files.writeString(localDirectory().resolve("one.txt"), ""));
    }

    private @NotNull Path localDirectory() throws IOException {
        Path directory = Files.createTempDirectory("workspace-folders-panel").toRealPath();
        Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(directory.toFile()));
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), directory.toString());
        return directory;
    }

    private static @NotNull VirtualFile localFile(@NotNull Path path) {
        var virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(virtualFile);
        return virtualFile;
    }
}

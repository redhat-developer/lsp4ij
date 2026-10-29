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
package com.redhat.devtools.lsp4ij.dap.client.files;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * A debug adapter may write the sources of its stack frames into a folder it makes, after the VFS has listed the
 * folder's parent. The VFS then knows the parent's children without the new folder, and a lookup that does not refresh
 * finds no file there.
 */
public class LocalSourceFilesTest extends LightPlatformTestCase {

    private Path root;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        root = Files.createTempDirectory("lsp4ij-sources").toRealPath();
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), root.toString());
        // the VFS lists the parent, as an earlier file of the session or another part of the IDE can make it do
        VirtualFile parent = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root);
        assertNotNull(parent);
        parent.getChildren();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            FileUtil.delete(root);
        } finally {
            super.tearDown();
        }
    }

    public void testAFileInAFolderMadeAfterItsParentWasListedIsFound() throws Exception {
        Path source = writeSource();
        assertNull("the VFS does not know the new folder", LocalFileSystem.getInstance().findFileByNioFile(source));

        VirtualFile file = offTheEdt(() -> LocalSourceFiles.findFile(source));

        assertNotNull(file);
        assertEquals(source.toString(), file.toNioPath().toString());
    }

    public void testAFileTheVfsKnowsIsFoundInAReadAction() throws Exception {
        Path source = writeSource();
        assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source));

        VirtualFile file = offTheEdt(() -> ReadAction.compute(() -> LocalSourceFiles.findFile(source)));

        assertNotNull(file);
    }

    public void testAFileTheVfsDoesNotKnowIsNotFoundInAReadAction() throws Exception {
        Path source = writeSource();

        // a refresh in a read action could deadlock, so the VFS is not refreshed there
        VirtualFile file = offTheEdt(() -> ReadAction.compute(() -> LocalSourceFiles.findFile(source)));

        assertNull(file);
    }

    public void testNoFileIsFoundWhereThereIsNone() throws Exception {
        assertNull(offTheEdt(() -> LocalSourceFiles.findFile(root.resolve("missing").resolve("Source.x"))));
    }

    private Path writeSource() throws Exception {
        Path folder = Files.createDirectories(root.resolve("adapter-sources"));
        return Files.writeString(folder.resolve("Source.x"), "module Source {}\n");
    }

    /**
     * Runs the lookup on a pooled thread, as the debugger does, while the test, on the EDT, dispatches the events that a
     * refresh needs the EDT for.
     */
    private static <T> T offTheEdt(Callable<T> lookup) {
        return PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(lookup), 30_000);
    }
}

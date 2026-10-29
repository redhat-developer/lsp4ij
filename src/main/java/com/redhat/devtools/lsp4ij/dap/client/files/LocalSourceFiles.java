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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Finds the local file of a stack frame's source.
 *
 * {@link LocalFileSystem#findFileByNioFile(Path)} never refreshes, so it does not find a file that the VFS has not
 * seen yet, such as one in a folder made after the VFS listed the folder's parent: a debug adapter may write sources
 * into a temporary folder of its own. Off the EDT and outside a read action, where a synchronous refresh is allowed,
 * such a file is found by refreshing the VFS.
 */
public final class LocalSourceFiles {

    private LocalSourceFiles() {
    }

    /**
     * @param path the path of a local file
     * @return the file, or null if there is none at the path, or if the VFS does not know it and cannot be refreshed
     * on the calling thread
     */
    public static @Nullable VirtualFile findFile(@NotNull Path path) {
        var fileSystem = LocalFileSystem.getInstance();
        VirtualFile file = fileSystem.findFileByNioFile(path);
        if (file == null && !ApplicationManager.getApplication().isReadAccessAllowed() && Files.isRegularFile(path)) {
            file = fileSystem.refreshAndFindFileByNioFile(path);
        }
        return file != null && file.isValid() ? file : null;
    }
}

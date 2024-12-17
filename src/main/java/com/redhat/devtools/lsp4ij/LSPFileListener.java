/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.*;
import com.redhat.devtools.lsp4ij.features.files.watcher.FileSystemWatcherManager;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * LSP file listener.
 */
class LSPFileListener implements FileEditorManagerListener, VirtualFileListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPFileListener.class);

    private final LanguageServerWrapper languageServerWrapper;
    private final FileSystemWatcherManager fileSystemWatcherManager;

    public LSPFileListener(LanguageServerWrapper languageServerWrapper) {
        this.languageServerWrapper = languageServerWrapper;
        this.fileSystemWatcherManager = new FileSystemWatcherManager();
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (!Objects.equals(source.getProject(), languageServerWrapper.getProject())) {
            // The file has been closed from another project,don't send textDocument/didClose
            return;
        }
        if (source.getAllEditors(file).length > 0) {
            // The file has been opened by multiple editors (e.g. via 'Split Right' menu),
            // we ignore the fileClosed event to ensure that a 'textDocument/didClose'
            // is sent when the last editor of the file has closed.
            return;
        }
        // Manage textDocument/didClose
        try {
            // Disconnect the given file from the current language servers
            languageServerWrapper.disconnect(file, !languageServerWrapper.isDisposed());
        } catch (Exception e) {
            LOGGER.warn("Error while disconnecting the file '" + file.getUrl() + "' from language server '" + languageServerWrapper.getServerDefinition().getDisplayName() + "'.", e);
        }
    }

    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        // Either a file (Test1.java) has been renamed (to Test2.java) by using Refactor / Rename from IJ
        // or a "properties" file is changed or saved.
        if (isRenameFile(event)) {
            // 1. Send a textDocument/didClose for the old file name (Test1.java) followed
            //    by a textDocument/didOpen for the new file name (Test2.java)
            VirtualFile newFile = event.getFile();
            URI oldFileUri = didRename(newFile.getParent(), (String) event.getOldValue(), newFile);

            // 2. Send a workspace/didChangeWatchedFiles
            moveFile(oldFileUri, newFile);
        }
    }

    private static boolean isRenameFile(@NotNull VirtualFilePropertyEvent event) {
        if (event.getPropertyName().equals(VirtualFile.PROP_NAME) &&
                event.getOldValue() instanceof String oldValue &&
                event.getNewValue() instanceof String newValue) {
            return !Objects.equals(oldValue, newValue);
        }
        return false;
    }

    private void moveFile(URI oldFileUri, VirtualFile newFile) {
        if (hasFilePatterns()) {
            List<FileEvent> changes = new ArrayList<>(2);
            if (isMatchFilePatterns(oldFileUri, WatchKind.Delete)) {
                changes.add(fe(oldFileUri, FileChangeType.Deleted));
            }
            URI newFileUri = languageServerWrapper.toUri(newFile);
            if (isMatchFilePatterns(newFileUri, WatchKind.Create)) {
                changes.add(fe(newFileUri, FileChangeType.Created));
            }
            if (!changes.isEmpty()) {
                didChangeWatchedFiles(changes.toArray(new FileEvent[changes.size()]));
            }
        }
    }

    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        URI uri = languageServerWrapper.toUri(file);
        if (uri != null) {
            LSPVirtualFileData documentListener = languageServerWrapper.connectedDocuments.get(uri);
            if (documentListener != null) {
                // 1. Send a textDocument/didSave for the saved file
                documentListener.getSynchronizer().documentSaved();
            }
            if (isMatchFilePatterns(uri, WatchKind.Change)) {
                // 2. Send a workspace/didChangeWatchedFiles
                didChangeWatchedFiles(fe(uri, FileChangeType.Changed));
            }
        }
    }

    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        URI uri = languageServerWrapper.toUri(file);
        if (isMatchFilePatterns(uri, WatchKind.Create)) {
            // 2. Send a workspace/didChangeWatchedFiles with 'Created' file change type.
            didChangeWatchedFiles(fe(uri, FileChangeType.Created));
        }
    }

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        URI uri = languageServerWrapper.toUri(file);
        if (isMatchFilePatterns(uri, WatchKind.Delete)) {
            // Send a workspace/didChangeWatchedFiles with 'Deleted' file change type.
            didChangeWatchedFiles(fe(uri, FileChangeType.Deleted));
        }
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        // A file (foo.Test1.java) has been moved (to bar1.Test1.java)

        // 1. Send a textDocument/didClose for the old file location (foo.Test1.java) followed
        //    by a textDocument/didOpen for the new file location (bar1.Test1.java)
        VirtualFile movedFile = event.getFile();
        URI oldFileUri = didRename(event.getOldParent(), movedFile.getName(), movedFile);

        // 2. Send a workspace/didChangeWatchedFiles
        moveFile(oldFileUri, movedFile);
    }

    private FileEvent fe(URI uri, FileChangeType type) {
        return new FileEvent(uri.toASCIIString(), type);
    }

    /**
     * Implements the <a href=https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didRename">
     * LSP specification for document renaming</a>. Used for both file renames within the same directory and file shifts
     * to a different directory.
     *
     * @param virtualParentFile directory of the file after renaming
     * @param oldFileName       file name before renaming
     * @param virtualNewFile    virtual file after renaming
     * @return URI of the file before renaming
     */
    private @NotNull URI didRename(@NotNull VirtualFile virtualParentFile,
                                      @NotNull String oldFileName,
                                      @NotNull VirtualFile virtualNewFile) {
        URI parentFileUri = languageServerWrapper.toUri(virtualParentFile);
        URI oldUri = parentFileUri.resolve(oldFileName);
        boolean docIsConnected = languageServerWrapper.isConnectedTo(oldUri);
        if (docIsConnected) {
            // 1. Send a textDocument/didClose for the old file name
            languageServerWrapper.disconnect(oldUri, false);
            // 2. Send a textDocument/didOpen for the new file name
            URI newUri = languageServerWrapper.toUri(virtualNewFile);
            if (!languageServerWrapper.isConnectedTo(newUri)) {
                languageServerWrapper.connect(virtualNewFile, null);
            }
        }
        return oldUri;
    }

    private void didChangeWatchedFiles(FileEvent... changes) {
        languageServerWrapper.sendNotification(ls -> {
            DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams(Arrays.asList(changes));
            ls.getWorkspaceService()
                    .didChangeWatchedFiles(params);
        });
    }

    private boolean hasFilePatterns() {
        return fileSystemWatcherManager.hasFilePatterns();
    }

    private boolean isMatchFilePatterns(@Nullable URI uri, int kind) {
        if (uri == null || !hasFilePatterns()) {
            return false;
        }
        return fileSystemWatcherManager.isMatchFilePattern(uri, kind);
    }

    public void registerFileSystemWatchers(String id, List<FileSystemWatcher> watchers) {
        fileSystemWatcherManager.registerFileSystemWatchers(id, watchers);
    }

    public void unregisterFileSystemWatchers(String id) {
        fileSystemWatcherManager.unregisterFileSystemWatchers(id);
    }
}

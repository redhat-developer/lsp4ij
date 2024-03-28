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
import com.redhat.devtools.lsp4ij.features.filewatchers.FileSystemWatcherManager;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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
        if (languageServerWrapper.initialProject != null && !Objects.equals(source.getProject(), languageServerWrapper.initialProject)) {
            // The file has been closed from another project,don't send textDocument/didClose
            return;
        }
        // Manage textDocument/didClose
        URI uri = LSPIJUtils.toUri(file);
        if (uri != null) {
            try {
                // Disconnect the given file from the current language servers
                languageServerWrapper.disconnect(uri, !languageServerWrapper.isDisposed());
            } catch (Exception e) {
                LOGGER.warn("Error while disconnecting the file '" + uri + "' from all language servers", e);
            }
        }
    }

    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        if (event.getPropertyName().equals(VirtualFile.PROP_NAME) && event.getOldValue() instanceof String) {
            // A file (Test1.java) has been renamed (to Test2.java) by using Refactor / Rename from IJ

            // 1. Send a textDocument/didClose for the renamed file (Test1.java)
            URI oldFileUri = didClose(event.getFile().getParent(), (String) event.getOldValue());

            // 2. Send a workspace/didChangeWatchedFiles
            VirtualFile newFile = event.getFile();
            moveFile(oldFileUri, newFile);
        }
    }

    private void moveFile(URI oldFileUri, VirtualFile newFile) {
        if (hasFilePatterns()) {
            List<FileEvent> changes = new ArrayList<>(2);
            if (isMatchFilePatterns(oldFileUri, WatchKind.Delete)) {
                changes.add(fe(oldFileUri, FileChangeType.Deleted));
            }
            URI newFileUri = LSPIJUtils.toUri(newFile);
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
        URI uri = LSPIJUtils.toUri(file);
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
        URI uri = LSPIJUtils.toUri(file);
        if (isMatchFilePatterns(uri, WatchKind.Create)) {
            // 2. Send a workspace/didChangeWatchedFiles with 'Created' file change type.
            didChangeWatchedFiles(fe(uri, FileChangeType.Created));
        }
    }

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        URI uri = LSPIJUtils.toUri(file);
        if (isMatchFilePatterns(uri, WatchKind.Delete)) {
            // Send a workspace/didChangeWatchedFiles with 'Deleted' file change type.
            didChangeWatchedFiles(fe(uri, FileChangeType.Deleted));
        }
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        // A file (foo.Test1.java) has been moved (to bar1.Test1.java)

        // 1. Send a textDocument/didClose for the moved file (foo.Test1.java)
        URI oldFileUri = didClose(event.getOldParent(), event.getFileName());
        moveFile(oldFileUri, event.getFile());
    }

    private FileEvent fe(URI uri, FileChangeType type) {
        return new FileEvent(uri.toASCIIString(), type);
    }

    private @NotNull URI didClose(VirtualFile virtualParentFile, String fileName) {
        File parent = VfsUtilCore.virtualToIoFile(virtualParentFile);
        URI uri = LSPIJUtils.toUri(new File(parent, fileName));
        if (languageServerWrapper.isConnectedTo(uri)) {
            languageServerWrapper.disconnect(uri, false);
        }
        return uri;
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

    public void setFileSystemWatchers(List<FileSystemWatcher> fileSystemWatchers) {
        fileSystemWatcherManager.setFileSystemWatchers(fileSystemWatchers);
    }
}

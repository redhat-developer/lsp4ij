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
import com.redhat.devtools.lsp4ij.features.files.operations.FileOperationsManager;
import com.redhat.devtools.lsp4ij.features.files.watcher.FileSystemWatcherManager;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP file listener.
 */
class LSPFileListener implements FileEditorManagerListener, VirtualFileListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPFileListener.class);

    private final LanguageServerWrapper languageServerWrapper;
    private final FileSystemWatcherManager fileSystemWatcherManager;

    private final FileOperationsManager fileOperationsManager;

    public LSPFileListener(LanguageServerWrapper languageServerWrapper) {
        this.languageServerWrapper = languageServerWrapper;
        this.fileSystemWatcherManager = new FileSystemWatcherManager();
        this.fileOperationsManager = new FileOperationsManager(languageServerWrapper);
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (languageServerWrapper.initialProject != null && !Objects.equals(source.getProject(), languageServerWrapper.initialProject)) {
            // The file has been closed from another project,don't send textDocument/didClose
            return;
        }
        // Manage textDocument/didClose
        URI uri = LSPIJUtils.toUri(file);
        try {
            // Disconnect the given file from the current language servers
            languageServerWrapper.disconnect(uri, !languageServerWrapper.isDisposed());
        } catch (Exception e) {
            LOGGER.warn("Error while disconnecting the file '" + uri + "' from all language servers", e);
        }
    }

    // ------------------------ Create files support

    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
        VirtualFile createdFile = event.getFile();
        URI createdFileUri = LSPIJUtils.toUri(createdFile);

        boolean isFolder = event.getFile().isDirectory();
        if (fileOperationsManager.canWillCreateFiles(createdFileUri, isFolder)) {
            // The language server support the willCreateFiles for this given Uri.

            // Get the willCreateFiles executor and execute it asynchronously
            // to avoid stopping the other fileCreated listeners.
            var executor = fileOperationsManager.getWillCreateFilesExecutor();
            CompletableFuture
                    .runAsync(() -> executor.executeWillOperationFiles(createdFileUri, isFolder))
                    .thenAccept(unused ->
                            // Send 'workspace/didChangeWatchedFiles' notification after:
                            // - executing 'workspace/willCreateFiles' request
                            // - sending 'workspace/didCreateFiles'
                            sendDidChangeWatchedFilesForCreateFiles(createdFileUri));
        } else {
            // Send 'workspace/didChangeWatchedFiles' notification
            sendDidChangeWatchedFilesForCreateFiles(createdFileUri);
        }
    }

    private void sendDidChangeWatchedFilesForCreateFiles(@NotNull URI uri) {
        if (isMatchFilePatterns(uri, WatchKind.Create)) {
            // Send a workspace/didChangeWatchedFiles with 'Created' file change type.
            didChangeWatchedFiles(fe(uri, FileChangeType.Created));
        }
    }

    // ------------------------ Delete files support

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
        VirtualFile deletedFile = event.getFile();
        URI deletedFileUri = LSPIJUtils.toUri(deletedFile);

        boolean isFolder = event.getFile().isDirectory();
        if (fileOperationsManager.canWillDeleteFiles(deletedFileUri, isFolder)) {
            // The language server support the willDeleteFiles for this given Uri.

            // Get the willDeleteFiles executor and execute it asynchronously
            // to avoid stopping the other fileDeleted listeners.
            var executor = fileOperationsManager.getWillDeleteFilesExecutor();
            CompletableFuture
                    .runAsync(() -> executor.executeWillOperationFiles(deletedFileUri, isFolder))
                    .thenAccept(unused ->
                            // Send 'workspace/didChangeWatchedFiles' notification after:
                            // - executing 'workspace/willDeleteFiles' request
                            // - sending 'workspace/didDeleteFiles'
                            sendDidChangeWatchedFilesForDeleteFiles(deletedFileUri));
        } else {
            // Send 'workspace/didChangeWatchedFiles' notification
            sendDidChangeWatchedFilesForDeleteFiles(deletedFileUri);
        }
    }

    private void sendDidChangeWatchedFilesForDeleteFiles(@NotNull URI uri) {
        if (isMatchFilePatterns(uri, WatchKind.Delete)) {
            // Send a workspace/didChangeWatchedFiles with 'Deleted' file change type.
            didChangeWatchedFiles(fe(uri, FileChangeType.Deleted));
        }
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        // A file (foo.Test1.java) has been moved (to bar1.Test1.java)

        // 1. Send a textDocument/didClose for the moved file (foo.Test1.java)
        URI oldFileUri = getMovedFileUri(event);
        didClose(oldFileUri);
        moveFile(oldFileUri, event.getFile());
    }

    @NotNull
    private static URI getMovedFileUri(VirtualFileMoveEvent event) {
        return getFileUri(event.getOldParent(), event.getFileName());
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
                didChangeWatchedFiles(changes.toArray(new FileEvent[0]));
            }
        }
    }

    // ------------------------ Rename files support


    @Override
    public void beforePropertyChange(@NotNull VirtualFilePropertyEvent event) {
        /*        if (isRenameFileEvent(event)) {
            // A file (Test1.java) has been renamed (to Test2.java) by using Refactor / Rename from IJ
            URI oldFileUri = getRenamedFileUri(event);

            boolean isFolder = event.getFile().isDirectory();
            if (fileOperationsManager.canWillRenameFiles(oldFileUri, isFolder)) {
                // The language server support the willRenameFiles for this given Uri.
                //VirtualFile newFile = event.getFile();
                URI newFileUri = getRenamedFileUri2(event);
                // Get the willRenameFiles executor and execute it in a async to avoid stopping the other propertyChanged listeners.
                var executor = fileOperationsManager.getWillRenameFilesExecutor();
                Document document = LSPIJUtils.getDocument(event.getFile());
                executor.executeWillOperationFiles(oldFileUri, newFileUri, isFolder, document);
            }
        }
        */
    }

    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        if (false) {//isRenameFileEvent(event)) {
            // A file (Test1.java) has been renamed (to Test2.java) by using Refactor / Rename from IJ
            URI oldFileUri = getRenamedFileUri(event);

            boolean isFolder = event.getFile().isDirectory();
            if (!fileOperationsManager.canWillRenameFiles(oldFileUri, isFolder)) {
                // The language server support the willRenameFiles for this given Uri.
                VirtualFile newFile = event.getFile();
                URI newFileUri = LSPIJUtils.toUri(newFile);
                // Get the willRenameFiles executor and execute it in a async to avoid stopping the other propertyChanged listeners.
                var executor = fileOperationsManager.getWillRenameFilesExecutor();
                executor.executeWillOperationFiles(oldFileUri, newFileUri, isFolder, null)
                        .thenAccept(unused ->
                                // Send 'workspace/didChangeWatchedFiles' notification after:
                                // - executing 'workspace/willRenameFiles' request
                                // - sending 'workspace/didRenameFiles'
                                sendDidChangeWatchedFilesForRenameFiles(event, oldFileUri));
            } else {
                // Send 'workspace/didChangeWatchedFiles' notification
                sendDidChangeWatchedFilesForRenameFiles(event, oldFileUri);
            }
        }
    }

    private void sendDidChangeWatchedFilesForRenameFiles(@NotNull VirtualFilePropertyEvent event, URI oldFileUri) {
        // 1. Send a textDocument/didClose for the renamed file (Test1.java)
        didClose(oldFileUri);

        // 2. Send a workspace/didChangeWatchedFiles
        VirtualFile newFile = event.getFile();
        moveFile(oldFileUri, newFile);
    }

    @NotNull
    private static URI getRenamedFileUri(VirtualFilePropertyEvent event) {
        return getFileUri(event.getFile().getParent(), (String) event.getOldValue());
    }

    private static URI getRenamedFileUri2(VirtualFilePropertyEvent event) {
        return getFileUri(event.getFile().getParent(), (String) event.getNewValue());
    }

    private static boolean isRenameFileEvent(@NotNull VirtualFilePropertyEvent event) {
        return event.getPropertyName().equals(VirtualFile.PROP_NAME) && event.getOldValue() instanceof String;
    }

    // ------------------------ Changed files support

    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        URI uri = LSPIJUtils.toUri(file);
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

    private FileEvent fe(URI uri, FileChangeType type) {
        return new FileEvent(uri.toASCIIString(), type);
    }

    private void didClose(URI fileUri) {
        if (languageServerWrapper.isConnectedTo(fileUri)) {
            languageServerWrapper.disconnect(fileUri, false);
        }
    }

    @NotNull
    private static URI getFileUri(VirtualFile virtualParentFile, String fileName) {
        File parent = VfsUtilCore.virtualToIoFile(virtualParentFile);
        return LSPIJUtils.toUri(new File(parent, fileName));
    }

    private void didChangeWatchedFiles(FileEvent... changes) {
        languageServerWrapper.sendNotification(ls -> {
            DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams(List.of(changes));
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

    public void setServerCapabilities(ServerCapabilities serverCapabilities) {
        this.fileOperationsManager.setServerCapabilities(serverCapabilities);
    }

}

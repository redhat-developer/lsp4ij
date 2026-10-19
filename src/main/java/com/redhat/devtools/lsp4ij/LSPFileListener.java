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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.redhat.devtools.lsp4ij.features.files.AbstractLSPFileListener;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * {@code LSPFileListener} bridges IntelliJ Virtual File System (VFS) events
 * with the Language Server Protocol (LSP) notifications.
 * <p>
 * This concrete implementation of {@link AbstractLSPFileListener} handles:
 * <ul>
 *   <li>File creation, deletion, and renaming.</li>
 *   <li>Sending {@code workspace/will*} and {@code workspace/did*} events.</li>
 *   <li>Document synchronization between IntelliJ and the language server.</li>
 * </ul>
 *
 * <p>It also manages file closure events via {@link FileEditorManager},
 * sending a {@code textDocument/didClose} notification when the last
 * editor tab for a file is closed.</p>
 */
class LSPFileListener extends AbstractLSPFileListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPFileListener.class);

    /**
     * Creates a new {@link LSPFileListener} for the given language server wrapper.
     *
     * @param languageServerWrapper the language server wrapper
     */
    public LSPFileListener(@NotNull LanguageServerWrapper languageServerWrapper) {
        super(languageServerWrapper);
    }

    @Override
    public ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events) {
        var delegate = super.prepareChange(events);
        var directories = events.stream().map(event -> {
            if (event instanceof VFileMoveEvent move) {
                return move.getFile();
            }
            if (event instanceof VFilePropertyChangeEvent rename &&
                    VirtualFile.PROP_NAME.equals(rename.getPropertyName())) {
                return rename.getFile();
            }
            return null;
        }).filter(Objects::nonNull).filter(VirtualFile::isDirectory).toList();
        if (directories.isEmpty()) {
            return delegate;
        }
        return new ChangeApplier() {
            @Override
            public void beforeVfsChange() {
                if (delegate != null) {
                    delegate.beforeVfsChange();
                }
                // Descendants do not receive individual VFS move events. Capture their URIs
                // before the directory changes, and retire each document only once even when
                // the transaction contains overlapping parent and child directory moves.
                var files = languageServerWrapper.getOpenedDocuments().stream().map(OpenedDocument::getFile)
                        .distinct().filter(file -> directories.stream().anyMatch(dir -> VfsUtilCore.isAncestor(dir, file, true)))
                        .toList();
                files.forEach(file -> languageServerWrapper.disconnect(file, false));
                files.forEach(LSPFileListener.this::reconnectMovedDocument);
            }

            @Override
            public void afterVfsChange() {
                if (delegate != null) {
                    delegate.afterVfsChange();
                }
            }
        };
    }

    private void reconnectMovedDocument(VirtualFile file) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!isOpenDocument(file)) {
                return;
            }
            URI uri = languageServerWrapper.toUri(file);
            // This runs on the EDT, which already has the read access the connection info needs.
            var connection = languageServerWrapper.connect(file,
                    languageServerWrapper.createFileConnectionInfo(file, LSPIJUtils.getDocument(file), true));
            // Never wait for transport under the VFS write lock. A later Move, Undo, Redo or
            // tab close may overtake this attempt; retire only this file's stale connection.
            connection.whenComplete((server, failure) -> ApplicationManager.getApplication().invokeLater(() -> {
                if (languageServerWrapper.isDisposed() || languageServerWrapper.getProject().isDisposed()) {
                    return;
                }
                boolean open = isOpenDocument(file);
                if (!open || !Objects.equals(uri, languageServerWrapper.toUri(file))) {
                    var opened = languageServerWrapper.getOpenedDocument(uri);
                    if (opened != null && opened.getFile() == file) {
                        languageServerWrapper.disconnect(uri, false);
                    }
                    if (open) {
                        reconnectMovedDocument(file);
                    }
                } else if (failure != null) {
                    LOGGER.warn("Cannot reconnect moved document {}", uri, failure);
                }
            }, ModalityState.any()));
        }, ModalityState.any());
    }

    private boolean isOpenDocument(VirtualFile file) {
        return !languageServerWrapper.isDisposed() && !languageServerWrapper.getProject().isDisposed() &&
                file.isValid() && (FileEditorManager.getInstance(languageServerWrapper.getProject()).isFileOpen(file) ||
                LSPIJUtils.editorsForFile(file, languageServerWrapper.getProject()).length > 0);
    }

    /**
     * Called when a file is closed in the IntelliJ editor.
     * <p>
     * If the file is still open in another editor instance (e.g., via "Split Right"),
     * the event is ignored to ensure {@code textDocument/didClose} is only sent
     * when the last editor tab for the file is actually closed.
     *
     * @param source the editor source
     * @param file   the closed file
     */
    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (!Objects.equals(source.getProject(), languageServerWrapper.getProject())) {
            // The file has been closed from another project, don't send textDocument/didClose
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
            LOGGER.warn("Error while disconnecting the file '" + file.getUrl() +
                    "' from language server '" +
                    languageServerWrapper.getServerDefinition().getDisplayName() + "'.", e);
        }
    }

    /**
     * Handles a file creation event before it occurs, preparing a {@code workspace/willCreateFiles} request.
     *
     * @param createdFile the file that will be created
     * @param fileCreates the list of LSP {@link FileCreate} events to populate
     */
    @Override
    protected void onFileCreateBefore(@NotNull VirtualFile createdFile,
                                      @NotNull List<FileCreate> fileCreates) {
        if (languageServerWrapper.isWillCreateFilesSupported(createdFile)) {
            URI createdFileUri = languageServerWrapper.toUri(createdFile);
            fileCreates.add(new FileCreate(languageServerWrapper.toUriString(createdFileUri)));
        }
    }

    /**
     * Handles a file deletion event before it occurs, preparing a {@code workspace/willDeleteFiles} request.
     *
     * @param deletedFile the file that will be deleted
     * @param fileDeletes the list of LSP {@link FileDelete} events to populate
     */
    @Override
    protected void onFileDeleteBefore(@NotNull VirtualFile deletedFile,
                                      @NotNull List<FileDelete> fileDeletes) {
        if (languageServerWrapper.isWillDeleteFilesSupported(deletedFile)) {
            URI deletedFileUri = languageServerWrapper.toUri(deletedFile);
            fileDeletes.add(new FileDelete(languageServerWrapper.toUriString(deletedFileUri)));
        }
    }

    /**
     * Handles a file rename (or move) before it occurs, preparing a {@code workspace/willRenameFiles} request.
     *
     * @param parentFile  the parent directory
     * @param oldFile     the file being renamed
     * @param newFileName the new name
     * @param fileRenames the list of LSP {@link FileRename} events to populate
     */
    @Override
    protected void onFileRenameBefore(@NotNull VirtualFile parentFile,
                                      @NotNull VirtualFile oldFile,
                                      @NotNull String newFileName,
                                      @NotNull List<FileRename> fileRenames) {
        if (languageServerWrapper.isWillRenameFilesSupported(oldFile)) {
            URI oldFileUri = languageServerWrapper.toUri(oldFile);
            URI newFileUri = getUri(parentFile, newFileName);
            fileRenames.add(new FileRename(languageServerWrapper.toUriString(oldFileUri),
                    languageServerWrapper.toUriString(newFileUri)));
        }
    }

    /**
     * Handles a file creation event after it occurs.
     * <p>Adds events for {@code workspace/didCreateFiles} and {@code workspace/didChangeWatchedFiles}.</p>
     *
     * @param createdFile the created file
     * @param fileEvents  the list of {@link FileEvent}s for didChangeWatchedFiles
     * @param fileCreates the list of {@link FileCreate}s for didCreateFiles
     */
    @Override
    protected void onFileCreateAfter(@NotNull VirtualFile createdFile,
                                     @NotNull List<FileEvent> fileEvents,
                                     @NotNull List<FileCreate> fileCreates) {
        URI uri = languageServerWrapper.toUri(createdFile);
        if (languageServerWrapper.isDidCreateFilesSupported(createdFile)) {
            // Add file create event to send a workspace/didCreateFiles
            fileCreates.add(new FileCreate(languageServerWrapper.toUriString(uri)));
        }
        if (isMatchFilePatterns(uri, WatchKind.Create)) {
            // Add file event to send a workspace/didChangeWatchedFiles with 'Created' file change type.
            fileEvents.add(fe(uri, FileChangeType.Created));
        }
    }

    /**
     * Handles a file deletion event after it occurs.
     * <p>Adds events for {@code workspace/didDeleteFiles} and {@code workspace/didChangeWatchedFiles}.</p>
     *
     * @param deletedFile the deleted file
     * @param fileDeletes the list of {@link FileDelete}s for didDeleteFiles
     * @param fileEvents  the list of {@link FileEvent}s for didChangeWatchedFiles
     */
    @Override
    protected void onFileDeleteAfter(@NotNull VirtualFile deletedFile,
                                     @NotNull List<FileDelete> fileDeletes,
                                     @NotNull List<FileEvent> fileEvents) {
        URI uri = languageServerWrapper.toUri(deletedFile);
        if (languageServerWrapper.isDidDeleteFilesSupported(deletedFile)) {
            // Add file create event to send a workspace/didDeleteFiles
            fileDeletes.add(new FileDelete(languageServerWrapper.toUriString(uri)));
        }
        if (isMatchFilePatterns(uri, WatchKind.Delete)) {
            // Add file event to send a workspace/didChangeWatchedFiles with 'Deleted' file change type.
            fileEvents.add(fe(uri, FileChangeType.Deleted));
        }
    }

    /**
     * Handles file content changes after they occur.
     * <p>
     * If the document is tracked by the LSP, this method triggers a
     * {@code textDocument/didSave}. It also adds the corresponding
     * {@code didChangeWatchedFiles} event if applicable.
     * </p>
     *
     * @param changedFile the file whose content changed
     * @param fileEvents  the list of {@link FileEvent}s for didChangeWatchedFiles
     */
    @Override
    protected void onFileContentChangedAfter(@NotNull VirtualFile changedFile,
                                             @NotNull List<FileEvent> fileEvents) {
        URI fileChangedUri = languageServerWrapper.toUri(changedFile);
        OpenedDocument openedDocument = languageServerWrapper.getOpenedDocument(fileChangedUri);
        if (openedDocument != null && openedDocument.getSynchronizer() != null) {
            // 1. Send a textDocument/didSave for the saved file
            openedDocument.getSynchronizer().documentSaved();
        }
        if (isMatchFilePatterns(fileChangedUri, WatchKind.Change)) {
            // Add file event to send a workspace/didChangeWatchedFiles with 'Changed' file change type.
            fileEvents.add(fe(fileChangedUri, FileChangeType.Changed));
        }
    }

    /**
     * Handles file rename or move events after they occur.
     * <p>
     * This includes:
     * <ul>
     *   <li>Disconnecting the old URI and connecting the new one.</li>
     *   <li>Sending {@code workspace/didRenameFiles} and {@code didChangeWatchedFiles} events.</li>
     * </ul>
     *
     * @param virtualParentFile the parent directory of the original file
     * @param oldFileName       the previous file name
     * @param newFile           the renamed or moved file
     * @param fileEvents        the list of {@link FileEvent}s
     * @param fileRenames       the list of {@link FileRename}s
     */
    @Override
    protected void onFileRenameAfter(@NotNull VirtualFile virtualParentFile,
                                     @NotNull String oldFileName,
                                     @NotNull VirtualFile newFile,
                                     @NotNull List<FileEvent> fileEvents,
                                     @NotNull List<FileRename> fileRenames) {
        // A file (foo.Test1.java) has been moved or renamed (to bar1.Test1.java)

        // 1. Send a textDocument/didClose for the old file location (foo.Test1.java) followed
        //    by a textDocument/didOpen for the new file location (bar1.Test1.java)

        URI oldFileUri = getUri(virtualParentFile, oldFileName);
        URI newFileUri = languageServerWrapper.toUri(newFile);
        boolean docIsConnected = languageServerWrapper.isConnectedTo(oldFileUri);
        if (docIsConnected) {
            // 1. Send a textDocument/didClose for the old file name
            languageServerWrapper.disconnect(oldFileUri, false);
            // 2. Reconnect after the VFS write action has completed, instead of waiting here for
            // the server while holding the write lock. The notification queue keeps didClose
            // ahead of the new didOpen.
            reconnectMovedDocument(newFile);
        }

        // 2. Prepare file events to send workspace/didRenameFiles
        if (languageServerWrapper.isDidRenameFilesSupported(newFile)) {
            fileRenames.add(new FileRename(languageServerWrapper.toUriString(oldFileUri),
                    languageServerWrapper.toUriString(newFileUri)));
        }

        // 3. Prepare file events to send workspace/didChangeWatchedFiles
        if (isMatchFilePatterns(newFileUri, WatchKind.Create)) {
            fileEvents.add(fe(newFileUri, FileChangeType.Created));
        }
        if (isMatchFilePatterns(oldFileUri, WatchKind.Delete)) {
            fileEvents.add(fe(oldFileUri, FileChangeType.Deleted));
        }
    }

    /**
     * Resolves a new file URI from a parent directory and a file name.
     *
     * @param parentFile the parent directory
     * @param fileName   the new file name
     * @return the resolved {@link URI}
     */
    private URI getUri(@NotNull VirtualFile parentFile,
                       @NotNull String fileName) {
        URI parentFileUri = languageServerWrapper.toUri(parentFile);
        return parentFileUri.resolve(fileName);
    }
}

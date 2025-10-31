/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.files;

import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.features.files.watcher.FileSystemWatcherManager;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Abstract listener that bridges IntelliJ Virtual File System (VFS) events
 * with Language Server Protocol (LSP) workspace notifications.
 * <p>
 * This listener handles creation, deletion, renaming, moving, and content change
 * events on files and directories, translating them into corresponding LSP events:
 * <ul>
 *   <li>{@code workspace/willCreateFiles}</li>
 *   <li>{@code workspace/willDeleteFiles}</li>
 *   <li>{@code workspace/willRenameFiles}</li>
 *   <li>{@code workspace/didCreateFiles}</li>
 *   <li>{@code workspace/didDeleteFiles}</li>
 *   <li>{@code workspace/didRenameFiles}</li>
 *   <li>{@code workspace/didChangeWatchedFiles}</li>
 * </ul>
 * <p>
 * Subclasses must implement the abstract hook methods to specify how IntelliJ
 * VFS events are converted into LSP {@link FileEvent}, {@link FileCreate},
 * {@link FileDelete}, and {@link FileRename} structures.
 */
public abstract class AbstractLSPFileListener implements FileEditorManagerListener, AsyncFileListener {

    /**
     * The associated language server wrapper.
     */
    protected final LanguageServerWrapper languageServerWrapper;

    /**
     * Manages file system watchers declared by the LSP client.
     */
    protected final FileSystemWatcherManager fileSystemWatcherManager;

    /**
     * Creates a new LSP-aware file listener.
     *
     * @param languageServerWrapper the language server wrapper instance
     */
    public AbstractLSPFileListener(@NotNull LanguageServerWrapper languageServerWrapper) {
        this.languageServerWrapper = languageServerWrapper;
        this.fileSystemWatcherManager = new FileSystemWatcherManager(languageServerWrapper.getProject());
    }

    @Override
    public @Nullable ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
        var supportedEvents = getSupportedEvents(events);
        if (supportedEvents.isEmpty()) {
            return null;
        }
        return new ChangeApplier() {
            @Override
            public void beforeVfsChange() {
                before(supportedEvents);
            }

            @Override
            public void afterVfsChange() {
                after(supportedEvents);
            }
        };
    }

    /**
     * Filters the list of VFS events to only those supported by the current LSP server.
     *
     * @param events the original list of VFS events
     * @return a filtered list containing only events supported by the LSP server
     */
    private @NotNull List<? extends @NotNull VFileEvent> getSupportedEvents(@NotNull List<? extends @NotNull VFileEvent> events) {
        if (!languageServerWrapper.isActive()) {
            return Collections.emptyList();
        }
        boolean createFilesSupported = canCreateFilesSupported();
        boolean deleteFilesSupported = canDeleteFilesSupported();
        boolean renameFilesSupported = canRenameFilesSupported();
        boolean changeFilesSupported = canChangeFilesSupported();
        boolean saveSupported = languageServerWrapper.isSaveSupported();
        if (!createFilesSupported && !deleteFilesSupported && !renameFilesSupported && !changeFilesSupported && !saveSupported) {
            return Collections.emptyList();
        }
        List<@NotNull VFileEvent> supportedEvents = null;
        for (VFileEvent event : events) {
            if (event instanceof VFileCreateEvent || event instanceof VFileCopyEvent) {
                if (createFilesSupported) {
                    if (supportedEvents == null) {
                        supportedEvents = new ArrayList<>();
                    }
                    supportedEvents.add(event);
                }
            } else if (event instanceof VFileDeleteEvent) {
                if (deleteFilesSupported) {
                    if (supportedEvents == null) {
                        supportedEvents = new ArrayList<>();
                    }
                    supportedEvents.add(event);
                }
            } else if (event instanceof VFilePropertyChangeEvent || event instanceof VFileMoveEvent) {
                if (renameFilesSupported) {
                    if (supportedEvents == null) {
                        supportedEvents = new ArrayList<>();
                    }
                    supportedEvents.add(event);
                }
            } else if (event instanceof VFileContentChangeEvent) {
                if (changeFilesSupported || saveSupported && isOpened(event.getFile())) {
                    if (supportedEvents == null) {
                        supportedEvents = new ArrayList<>();
                    }
                    supportedEvents.add(event);
                }
            }
        }
        return supportedEvents != null ? supportedEvents : Collections.emptyList();
    }

    private boolean isOpened(@Nullable VirtualFile file) {
        if (file == null) {
            return false;
        }
        URI fileUri = languageServerWrapper.toUri(file);
        return languageServerWrapper.getOpenedDocument(fileUri) != null;
    }

    private boolean canCreateFilesSupported() {
        return languageServerWrapper.canCreateFilesSupported() || fileSystemWatcherManager.hasFilePatternsFor(WatchKind.Create);
    }

    private boolean canDeleteFilesSupported() {
        return languageServerWrapper.canDeleteFilesSupported() || fileSystemWatcherManager.hasFilePatternsFor(WatchKind.Delete);
    }

    private boolean canRenameFilesSupported() {
        return languageServerWrapper.canRenameFilesSupported();
    }

    private boolean canChangeFilesSupported() {
        return fileSystemWatcherManager.hasFilePatternsFor(WatchKind.Change);
    }

    /**
     * Called before VFS events are applied.
     * <p>
     * Sends "will*" LSP requests to the server for file creation, deletion, and renaming/movement.
     *
     * @param events the list of VFS events before execution
     */
    private void before(@NotNull List<? extends @NotNull VFileEvent> events) {
        List<FileCreate> fileCreates = new ArrayList<>(); // workspace/didCreateFiles events
        List<FileDelete> fileDeletes = new ArrayList<>(); // workspace/didDeleteFiles events
        List<FileRename> fileRenames = new ArrayList<>(); // workspace/didRenameFiles events
        for (VFileEvent event : events) {
            if (event instanceof VFileCreateEvent ce) {
                // Create file
                VirtualFile createdFile = ce.getFile();
                if (createdFile != null) {
                    onFileCreateBefore(createdFile, fileCreates);
                }
            } else if (event instanceof VFileCopyEvent ce) {
                // Copy file --> same as Create file
                VirtualFile copiedFile = ce.getNewParent().findChild(ce.getNewChildName());
                if (copiedFile != null) {
                    onFileCreateBefore(copiedFile, fileCreates);
                }
            } else if (event instanceof VFileDeleteEvent de) {
                // Delete file
                VirtualFile deletedFile = de.getFile();
                if (deletedFile != null) {
                    onFileDeleteBefore(deletedFile, fileDeletes);
                }
            } else if (event instanceof VFilePropertyChangeEvent pce) {
                // Rename file
                var pe = new VirtualFilePropertyEvent(event.getRequestor(), pce.getFile(), pce.getPropertyName(), pce.getOldValue(), pce.getNewValue());
                if (isRenameFile(pe)) {
                    VirtualFile parentFile = pe.getParent();
                    String newFileName = (String) pe.getNewValue();
                    VirtualFile oldFile = pe.getFile();
                    onFileRenameBefore(parentFile, oldFile, newFileName, fileRenames);
                }
            } else if (event instanceof VFileMoveEvent me) {
                // Move file --> same as Rename file
                VirtualFile parentFile = me.getOldParent();
                VirtualFile oldFile = event.getFile();
                String newFileName = me.getOldPath();
                onFileRenameBefore(parentFile, oldFile, newFileName, fileRenames);
            }
        }
        var ls = languageServerWrapper.getLanguageServer();
        if (ls != null) {
            if (!fileCreates.isEmpty()) {
                var params = new CreateFilesParams(fileCreates);
                applyWorkspaceEdit(ls.getWorkspaceService().willCreateFiles(params));
            }
            if (!fileDeletes.isEmpty()) {
                var params = new DeleteFilesParams(fileDeletes);
                applyWorkspaceEdit(ls.getWorkspaceService().willDeleteFiles(params));
            }
            if (!fileRenames.isEmpty()) {
                var params = new RenameFilesParams(fileRenames);
                applyWorkspaceEdit(ls.getWorkspaceService().willRenameFiles(params));
            }
        }
    }

    /**
     * Applies a {@link WorkspaceEdit} returned by a "will*" LSP request.
     * Waits for up to 500ms to avoid blocking the UI thread.
     *
     * @param future the future returning the workspace edit
     */
    private static void applyWorkspaceEdit(CompletableFuture<WorkspaceEdit> future) {
        try {
            CancellationSupport cancellationSupport = new CancellationSupport();
            future = cancellationSupport.execute(future);
            CompletableFutures.waitUntilDone(future, null, 500);
        } catch (ExecutionException e) {

        } catch (TimeoutException e) {

        }
        if (CompletableFutures.isDoneNormally(future)) {
            var workspaceEdit = future.getNow(null);
            if (workspaceEdit != null) {
                LSPIJUtils.applyWorkspaceEdit(workspaceEdit);
            }
        }
    }

    /**
     * Called after VFS events are applied.
     * <p>
     * Sends "did*" LSP notifications for file creation, deletion, renaming/movement, and content changes.
     *
     * @param events the list of VFS events after execution
     */
    private void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        List<FileEvent> fileEvents = new ArrayList<>(); // workspace/didChangeWatchedFiles events
        List<FileCreate> fileCreates = new ArrayList<>(); // workspace/didCreateFiles events
        List<FileDelete> fileDeletes = new ArrayList<>(); // workspace/didDeleteFiles events
        List<FileRename> fileRenames = new ArrayList<>(); // workspace/didRenameFiles events

        for (VFileEvent event : events) {
            if (event instanceof VFileCreateEvent ce) {
                // Create file
                VirtualFile createdFile = ce.getFile();
                if (createdFile != null) {
                    onFileCreateAfter(createdFile, fileEvents, fileCreates);
                }
            } else if (event instanceof VFileCopyEvent ce) {
                // Copy file --> same as Create file
                VirtualFile copiedFile = ce.getNewParent().findChild(ce.getNewChildName());
                if (copiedFile != null) {
                    onFileCreateAfter(copiedFile, fileEvents, fileCreates);
                }
            } else if (event instanceof VFileDeleteEvent de) {
                // Delete file
                VirtualFile deletedFile = de.getFile();
                if (deletedFile != null) {
                    onFileDeleteAfter(deletedFile, fileDeletes, fileEvents);
                }
            } else if (event instanceof VFileContentChangeEvent ce) {
                // File content changed
                VirtualFile changedFile = ce.getFile();
                onFileContentChangedAfter(changedFile, fileEvents);
            } else if (event instanceof VFilePropertyChangeEvent pce) {
                // Rename file
                var pe = new VirtualFilePropertyEvent(event.getRequestor(), pce.getFile(), pce.getPropertyName(), pce.getOldValue(), pce.getNewValue());
                if (isRenameFile(pe)) {
                    VirtualFile parentFile = pe.getParent();
                    String oldFileName = (String) pe.getOldValue();
                    VirtualFile newFile = pe.getFile();
                    onFileRenameAfter(parentFile, oldFileName, newFile, fileEvents, fileRenames);
                }
            } else if (event instanceof VFileMoveEvent me) {
                // Move file --> same as Rename file
                VirtualFile parentFile = me.getOldParent();
                VirtualFile movedFile = event.getFile();
                String oldFileName = movedFile.getName();
                onFileRenameAfter(parentFile, oldFileName, movedFile, fileEvents, fileRenames);
            }
        }

        if (!fileCreates.isEmpty()) {
            didCreateFiles(fileCreates);
        }
        if (!fileDeletes.isEmpty()) {
            didDeleteFiles(fileDeletes);
        }
        if (!fileRenames.isEmpty()) {
            didRenameFiles(fileRenames);
        }
        if (!fileEvents.isEmpty()) {
            didChangeWatchedFiles(fileEvents);
        }
    }

    // -------------------- Abstract hooks --------------------

    /**
     * Called before a file is created.
     */
    protected abstract void onFileCreateBefore(@NotNull VirtualFile createdFile,
                                               @NotNull List<FileCreate> fileCreates);

    /**
     * Called before a file is deleted.
     */
    protected abstract void onFileDeleteBefore(@NotNull VirtualFile deletedFile,
                                               @NotNull List<FileDelete> fileDeletes);

    /**
     * Called before a file is renamed or moved.
     */
    protected abstract void onFileRenameBefore(@NotNull VirtualFile parentFile,
                                               @NotNull VirtualFile oldFile,
                                               @NotNull String newFileName,
                                               @NotNull List<FileRename> fileRenames);

    /**
     * Called after a file has been created.
     */
    protected abstract void onFileCreateAfter(@NotNull VirtualFile createdFile,
                                              @NotNull List<FileEvent> fileEvents,
                                              @NotNull List<FileCreate> fileCreates);

    /**
     * Called after a file has been deleted.
     */
    protected abstract void onFileDeleteAfter(@NotNull VirtualFile deletedFile,
                                              @NotNull List<FileDelete> fileDeletes,
                                              @NotNull List<FileEvent> fileEvents);

    /**
     * Called after the content of a file has changed.
     */
    protected abstract void onFileContentChangedAfter(@NotNull VirtualFile changedFile,
                                                      @NotNull List<FileEvent> fileEvents);

    /**
     * Called after a file has been renamed or moved.
     */
    protected abstract void onFileRenameAfter(@NotNull VirtualFile virtualParentFile,
                                              @NotNull String oldFileName,
                                              @NotNull VirtualFile newFile,
                                              @NotNull List<FileEvent> fileEvents,
                                              @NotNull List<FileRename> fileRenames);

    // -------------------- Utilities --------------------

    /**
     * Determines if the given property change event represents a file rename.
     */
    private static boolean isRenameFile(@NotNull VirtualFilePropertyEvent event) {
        if (event.getPropertyName().equals(VirtualFile.PROP_NAME) &&
                event.getOldValue() instanceof String oldValue &&
                event.getNewValue() instanceof String newValue) {
            return !Objects.equals(oldValue, newValue);
        }
        return false;
    }

    /**
     * Sends a {@code workspace/didCreateFiles} notification.
     */
    private void didCreateFiles(@NotNull List<FileCreate> fileCreates) {
        languageServerWrapper.sendNotification(ls -> {
            var params = new CreateFilesParams(fileCreates);
            ls.getWorkspaceService().didCreateFiles(params);
            return ls;
        });
    }

    /**
     * Sends a {@code workspace/didDeleteFiles} notification.
     */
    private void didDeleteFiles(@NotNull List<FileDelete> fileDeletes) {
        languageServerWrapper.sendNotification(ls -> {
            var params = new DeleteFilesParams(fileDeletes);
            ls.getWorkspaceService().didDeleteFiles(params);
            return ls;
        });
    }

    /**
     * Sends a {@code workspace/didRenameFiles} notification.
     */
    private void didRenameFiles(@NotNull List<FileRename> fileRenames) {
        languageServerWrapper.sendNotification(ls -> {
            var params = new RenameFilesParams(fileRenames);
            ls.getWorkspaceService().didRenameFiles(params);
            return ls;
        });
    }

    /**
     * Sends a {@code workspace/didChangeWatchedFiles} notification.
     */
    private void didChangeWatchedFiles(@NotNull List<FileEvent> fileEvents) {
        languageServerWrapper.sendNotification(ls -> {
            DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams(fileEvents);
            ls.getWorkspaceService().didChangeWatchedFiles(params);
            return ls;
        });
    }

    /**
     * Returns whether any file watchers are currently registered.
     */
    private boolean hasFilePatterns() {
        return fileSystemWatcherManager.hasFilePatterns();
    }

    /**
     * Checks if the given URI matches any registered file watcher patterns.
     */
    protected boolean isMatchFilePatterns(@Nullable URI uri, int kind) {
        if (uri == null || !hasFilePatterns()) {
            return false;
        }
        return fileSystemWatcherManager.isMatchFilePattern(uri, kind);
    }

    /**
     * Registers new file system watchers with a given ID.
     */
    public void registerFileSystemWatchers(String id, List<FileSystemWatcher> watchers) {
        fileSystemWatcherManager.registerFileSystemWatchers(id, watchers);
    }

    /**
     * Unregisters file system watchers associated with the given ID.
     */
    public void unregisterFileSystemWatchers(String id) {
        fileSystemWatcherManager.unregisterFileSystemWatchers(id);
    }

    /**
     * Creates a new {@link FileEvent} for the given URI and change type.
     */
    protected FileEvent fe(URI uri, FileChangeType type) {
        return new FileEvent(languageServerWrapper.toUriString(uri), type);
    }

}

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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.features.files.WillRenameFilesPreparation.FileRenamePlan;
import com.redhat.devtools.lsp4ij.features.files.watcher.FileSystemWatcherManager;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
 * The {@code workspace/will*} requests are sent during {@link #prepareChange}. For an
 * operation made in the IDE, IntelliJ calls it in the write action that makes the change,
 * so waiting there for the language server response blocks the UI. A rename or move
 * refactoring therefore sends {@code workspace/willRenameFiles} before its write action
 * and waits for the response under its progress (see {@link WillRenameFilesPreparation}),
 * and {@link #prepareChange} uses that response instead of sending the request again.
 * The resulting {@link WorkspaceEdit}s are then applied under the write lock in
 * {@code beforeVfsChange()}.
 * <p>
 * Subclasses must implement the abstract hook methods to specify how IntelliJ
 * VFS events are converted into LSP {@link FileEvent}, {@link FileCreate},
 * {@link FileDelete}, and {@link FileRename} structures.
 */
public abstract class AbstractLSPFileListener implements FileEditorManagerListener, AsyncFileListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLSPFileListener.class);

    /**
     * Returns the timeout in milliseconds for {@code workspace/will*} file operation requests,
     * reading the value from global settings (configurable like VS Code's {@code files.participants.timeout}).
     *
     * @return the timeout in milliseconds
     */
    private static long getFileOperationsTimeoutMs() {
        return GlobalLanguageServerSettings.getInstance().getFileOperationsTimeout();
    }

    /**
     * Returns the deadline, as an absolute {@link System#nanoTime()}, for the responses to
     * {@code workspace/will*} requests sent now.
     */
    private static long getFileOperationsDeadlineNanos() {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(getFileOperationsTimeoutMs());
    }

    /**
     * The associated language server wrapper.
     */
    protected final LanguageServerWrapper languageServerWrapper;

    /**
     * Manages file system watchers declared by the LSP client.
     */
    protected final FileSystemWatcherManager fileSystemWatcherManager;

    /**
     * The {@code workspace/willRenameFiles} requests that a refactoring sent before its write action,
     * which the VFS events of the refactoring use instead of sending the requests again.
     */
    private final List<PreparedRenames> preparedRenames = new ArrayList<>();

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
        // Capture the server instance so we can detect connection recycling
        // between prepareChange() and beforeVfsChange().
        var serverSnapshot = languageServerWrapper.getLanguageServer();
        // Capture document modification stamps before sending will* requests.
        // These stamps are checked before applying edits to detect and discard
        // stale edits whose target documents were modified while waiting.
        Map<String, Long> documentStamps = captureDocumentStamps();
        List<WillEdit> workspaceEdits = collectWillEdits(supportedEvents, documentStamps);
        // The documents without an editor that the edits change, saved once the VFS event is over
        Set<Document> documentsToSave = new LinkedHashSet<>();
        return new ChangeApplier() {
            @Override
            public void beforeVfsChange() {
                // Discard all edits if the server connection was recycled
                if (serverSnapshot != languageServerWrapper.getLanguageServer()) {
                    LOGGER.warn("Discarding workspace/will* edits: server connection was recycled");
                    return;
                }
                for (WillEdit edit : workspaceEdits) {
                    if (areTargetDocumentsUnchanged(edit.edit(), edit.documentStamps())) {
                        // A document cannot be saved during a VFS event
                        LSPIJUtils.applyWorkspaceEdit(edit.edit(), null, false, documentsToSave);
                    } else {
                        LOGGER.warn("Discarding stale workspace/will* edit: target document(s) modified during request");
                    }
                }
            }

            @Override
            public void afterVfsChange() {
                Runnable sendNotifications = after(supportedEvents);
                if (documentsToSave.isEmpty()) {
                    sendNotifications.run();
                } else {
                    // A language server reads a document without an editor from disk, so save the edited
                    // ones, which cannot happen during the VFS event this runs in, before telling the server
                    // of the file operations, whose edits were applied before them
                    ApplicationManager.getApplication().invokeLater(() -> {
                        try {
                            LSPIJUtils.saveDocumentsWithoutEditor(documentsToSave);
                        } finally {
                            sendNotifications.run();
                        }
                    });
                }
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
     * Sends {@code workspace/will*} requests to the language server and collects the
     * resulting {@link WorkspaceEdit}s, which are then applied under the write lock in
     * {@code beforeVfsChange()}.
     * <p>
     * Per the LSP specification (§3.16.4), {@code workspace/will*} requests are only sent
     * for file operations triggered from within the client (user actions or workspace edits).
     * Events originating from an external VFS refresh ({@link VFileEvent#isFromRefresh()})
     * are skipped, matching VS Code's behavior.
     * <p>
     * <b>Note on inside-IDE operations:</b> For file operations initiated by the user within
     * the IDE, IntelliJ calls {@code prepareChange()} in the write action that makes the
     * change, so the UI is blocked for the duration of the server response. The configurable
     * {@code fileOperationsTimeout} setting bounds this wait. The renames and moves of a
     * refactoring avoid it: their {@code workspace/willRenameFiles} response was received
     * before the write action, and is used here without sending the request again.
     *
     * @param events         the list of VFS events
     * @param documentStamps the modification stamps of the opened documents
     * @return the non-null workspace edits returned by the server
     */
    private @NotNull List<WillEdit> collectWillEdits(@NotNull List<? extends @NotNull VFileEvent> events,
                                                     @NotNull Map<String, Long> documentStamps) {
        List<FileCreate> fileCreates = new ArrayList<>(); // workspace/willCreateFiles events
        List<FileDelete> fileDeletes = new ArrayList<>(); // workspace/willDeleteFiles events
        List<FileRename> fileRenames = new ArrayList<>(); // workspace/willRenameFiles events
        for (VFileEvent event : events) {
            // Per LSP spec §3.16.4, workspace/will* requests are only sent for changes
            // triggered from within the client (user actions or workspace edits),
            // not for external file system changes detected during VFS refresh.
            // This matches VS Code behavior, which only fires willDeleteFiles for
            // user gestures or workspace.applyEdit.
            if (event.isFromRefresh()) {
                continue;
            }
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
                VirtualFile parentFile = me.getNewParent();
                VirtualFile oldFile = event.getFile();
                String newFileName = oldFile.getName();
                onFileRenameBefore(parentFile, oldFile, newFileName, fileRenames);
            }
        }

        var edits = new ArrayList<WillEdit>();
        var ls = languageServerWrapper.getLanguageServer();
        if (ls != null) {
            long deadlineNanos = getFileOperationsDeadlineNanos();
            if (!fileCreates.isEmpty()) {
                var params = new CreateFilesParams(fileCreates);
                var edit = getWorkspaceEdit(ls.getWorkspaceService().willCreateFiles(params), deadlineNanos);
                if (edit != null) {
                    edits.add(new WillEdit(edit, documentStamps));
                }
            }
            if (!fileDeletes.isEmpty()) {
                var params = new DeleteFilesParams(fileDeletes);
                var edit = getWorkspaceEdit(ls.getWorkspaceService().willDeleteFiles(params), deadlineNanos);
                if (edit != null) {
                    edits.add(new WillEdit(edit, documentStamps));
                }
            }
            List<FileRename> unpreparedRenames = takePreparedRenames(fileRenames, ls, edits);
            if (!unpreparedRenames.isEmpty()) {
                var params = new RenameFilesParams(unpreparedRenames);
                var edit = getWorkspaceEdit(ls.getWorkspaceService().willRenameFiles(params), deadlineNanos);
                if (edit != null) {
                    edits.add(new WillEdit(edit, documentStamps));
                }
            }
        }
        return edits;
    }

    /**
     * Sends a {@code workspace/willRenameFiles} request for the renames and moves that a refactoring
     * is about to make, before its write action. The VFS events of the refactoring then use the
     * response instead of sending the request again, once {@link #awaitWillRenameFiles()} waited for it.
     *
     * @param renames the files and directories the refactoring renames or moves
     */
    void sendWillRenameFiles(@NotNull List<FileRenamePlan> renames) {
        var ls = languageServerWrapper.getLanguageServer();
        if (ls == null || !languageServerWrapper.isActive() ||
                !LanguageServerItem.isWillRenameFilesSupported(languageServerWrapper.getServerCapabilitiesSync())) {
            return;
        }
        var fileRenames = new ArrayList<FileRename>();
        renames.forEach(rename -> onFileRenameBefore(rename.newParent(), rename.file(), rename.newName(), fileRenames));
        var unmade = new LinkedHashSet<>(fileRenames);
        if (unmade.isEmpty()) {
            return;
        }
        long deadlineNanos = getFileOperationsDeadlineNanos();
        var documentStamps = captureDocumentStamps();
        var request = ls.getWorkspaceService().willRenameFiles(new RenameFilesParams(List.copyOf(unmade)));
        synchronized (preparedRenames) {
            preparedRenames.add(new PreparedRenames(ls, unmade, documentStamps, request, deadlineNanos));
        }
    }

    /**
     * Waits for the responses to the requests that {@link #sendWillRenameFiles} sent, each for up to
     * the file operations timeout.
     */
    void awaitWillRenameFiles() {
        List<PreparedRenames> prepared;
        synchronized (preparedRenames) {
            prepared = List.copyOf(preparedRenames);
        }
        prepared.forEach(PreparedRenames::await);
    }

    /**
     * Drops the responses that the VFS events of a refactoring did not use, once it is done,
     * and cancels the requests still pending.
     */
    void clearWillRenameFiles() {
        synchronized (preparedRenames) {
            preparedRenames.forEach(renames -> renames.request.cancel(true));
            preparedRenames.clear();
        }
    }

    /**
     * Takes the renames that a refactoring requested before its write action, and adds the edit of
     * their response, once, to the given edits.
     *
     * @param fileRenames the renames of the VFS events
     * @param ls          the language server to send requests to now
     * @param edits       the edits to apply before the VFS events
     * @return the renames that still need a request
     */
    private @NotNull List<FileRename> takePreparedRenames(@NotNull List<FileRename> fileRenames,
                                                          @NotNull LanguageServer ls,
                                                          @NotNull List<WillEdit> edits) {
        synchronized (preparedRenames) {
            if (preparedRenames.isEmpty()) {
                return fileRenames;
            }
            var unprepared = new ArrayList<FileRename>();
            for (FileRename fileRename : fileRenames) {
                // A response from before the server connection was recycled does not apply to the new one
                var prepared = preparedRenames.stream()
                        .filter(renames -> renames.server == ls && renames.unmade.contains(fileRename))
                        .findFirst()
                        .orElse(null);
                if (prepared == null) {
                    unprepared.add(fileRename);
                } else {
                    prepared.unmade.remove(fileRename);
                    // The edit of a request for several renames is applied before the first of them
                    var edit = prepared.takeEdit();
                    if (edit != null) {
                        edits.add(new WillEdit(edit, prepared.documentStamps));
                    }
                }
            }
            preparedRenames.removeIf(renames -> renames.unmade.isEmpty());
            return unprepared;
        }
    }

    /**
     * Waits for a {@code workspace/will*} response and returns the resulting {@link WorkspaceEdit},
     * or {@code null} if the request timed out or failed.
     * <p>
     * If the enclosing progress indicator is cancelled while waiting,
     * {@link ProcessCanceledException} is rethrown so that the caller
     * ({@code collectWillEdits}) aborts immediately instead of continuing
     * to the next {@code workspace/will*} request.
     *
     * @param future        the future returning the workspace edit
     * @param deadlineNanos shared deadline (absolute {@link System#nanoTime()})
     * @return the workspace edit, or {@code null}
     * @throws ProcessCanceledException if the progress indicator is cancelled
     */
    // Package-private for testing
    static @Nullable WorkspaceEdit getWorkspaceEdit(@NotNull CompletableFuture<WorkspaceEdit> future,
                                                    long deadlineNanos) {
        CancellationSupport cancellationSupport = new CancellationSupport();
        future = cancellationSupport.execute(future);
        long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        if (remainingMs <= 0) {
            future.cancel(true);
            return null;
        }
        try {
            var f = future;
            WorkspaceEdit result = ProgressIndicatorUtils.withTimeout(remainingMs,
                    () -> ProgressIndicatorUtils.awaitWithCheckCanceled(f));
            if (result == null) {
                // withTimeout returns null when its internal timeout fires;
                // cancel the LSP future so the server receives $/cancelRequest
                future.cancel(true);
            }
            return result;
        } catch (ProcessCanceledException e) {
            // The enclosing progress indicator was cancelled (e.g. the VFS
            // operation is being aborted). Cancel the LSP future and rethrow
            // so collectWillEdits stops immediately instead of trying the
            // remaining workspace/will* requests.
            future.cancel(true);
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Captures modification stamps for all documents currently opened in the
     * language server. Used to detect stale edits before applying them.
     *
     * @return a map of document URI to modification stamp
     */
    private @NotNull Map<String, Long> captureDocumentStamps() {
        Map<String, Long> stamps = new HashMap<>();
        for (var openedDoc : languageServerWrapper.getOpenedDocuments()) {
            VirtualFile file = openedDoc.getFile();
            Document doc = FileDocumentManager.getInstance().getCachedDocument(file);
            if (doc != null) {
                stamps.put(languageServerWrapper.toUriString(file), doc.getModificationStamp());
            }
        }
        return stamps;
    }

    /**
     * Checks that none of the documents targeted by the workspace edit have been
     * modified since the stamps were captured. If any target document changed,
     * the edit is stale and should be discarded.
     *
     * @param edit   the workspace edit to validate
     * @param stamps the document modification stamps captured before the will* request
     * @return {@code true} if all target documents are unchanged, {@code false} if any are stale
     */
    // Package-private for testing
    static boolean areTargetDocumentsUnchanged(@NotNull WorkspaceEdit edit,
                                               @NotNull Map<String, Long> stamps) {
        if (stamps.isEmpty()) {
            return true;
        }
        if (edit.getDocumentChanges() != null) {
            for (Either<TextDocumentEdit, ResourceOperation> change : edit.getDocumentChanges()) {
                if (change.isLeft()) {
                    String uri = change.getLeft().getTextDocument().getUri();
                    if (isDocumentStale(uri, stamps)) {
                        return false;
                    }
                }
            }
        }
        if (edit.getChanges() != null) {
            for (String uri : edit.getChanges().keySet()) {
                if (isDocumentStale(uri, stamps)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isDocumentStale(@NotNull String uri, @NotNull Map<String, Long> stamps) {
        VirtualFile file = LSPIJUtils.findResourceFor(uri);
        if (file == null) {
            return false;
        }
        Document doc = FileDocumentManager.getInstance().getCachedDocument(file);
        return isDocumentStale(uri, doc, stamps);
    }

    // Package-private for testing
    static boolean isDocumentStale(@NotNull String uri,
                                   @Nullable Document doc,
                                   @NotNull Map<String, Long> stamps) {
        if (doc == null) {
            return false;
        }
        Long capturedStamp = stamps.get(uri);
        if (capturedStamp == null) {
            // Document was not open when stamps were captured but is now cached;
            // we cannot verify the server's assumptions about its content.
            return true;
        }
        return doc.getModificationStamp() != capturedStamp;
    }

    /**
     * Called after VFS events are applied.
     * <p>
     * Collects the "did*" LSP notifications for file creation, deletion, renaming/movement, and content changes.
     *
     * @param events the list of VFS events after execution
     * @return the action that sends the collected notifications
     */
    private @NotNull Runnable after(@NotNull List<? extends @NotNull VFileEvent> events) {
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

        return () -> {
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
        };
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
     * Unregisters all the file system watchers, as when the language server that registered them stops.
     */
    public void clearFileSystemWatchers() {
        fileSystemWatcherManager.clear();
    }

    /**
     * Unregisters all the file system watchers and releases the roots watched for them, when the language server
     * wrapper is disposed.
     */
    public void disposeFileSystemWatchers() {
        fileSystemWatcherManager.dispose();
    }

    /**
     * Creates a new {@link FileEvent} for the given URI and change type.
     */
    protected FileEvent fe(URI uri, FileChangeType type) {
        return new FileEvent(languageServerWrapper.toUriString(uri), type);
    }

    /**
     * A {@code workspace/will*} edit, with the modification stamps of the opened documents
     * from when its request was sent.
     */
    private record WillEdit(@NotNull WorkspaceEdit edit, @NotNull Map<String, Long> documentStamps) {
    }

    /**
     * A {@code workspace/willRenameFiles} request that a refactoring sent before its write action.
     */
    private static final class PreparedRenames {

        private final @NotNull LanguageServer server;
        // The renames of the request whose VFS events have not come yet, guarded by preparedRenames
        private final @NotNull Set<FileRename> unmade;
        private final @NotNull Map<String, Long> documentStamps;
        private final @NotNull CompletableFuture<WorkspaceEdit> request;
        private final long deadlineNanos;
        // Guarded by preparedRenames
        private boolean editTaken;

        private PreparedRenames(@NotNull LanguageServer server,
                                @NotNull Set<FileRename> unmade,
                                @NotNull Map<String, Long> documentStamps,
                                @NotNull CompletableFuture<WorkspaceEdit> request,
                                long deadlineNanos) {
            this.server = server;
            this.unmade = unmade;
            this.documentStamps = documentStamps;
            this.request = request;
            this.deadlineNanos = deadlineNanos;
        }

        private void await() {
            // Cancels the request when it times out
            getWorkspaceEdit(request, deadlineNanos);
        }

        /**
         * Returns the edit of the response the first time only, or {@code null} if the request
         * has not completed, timed out or failed.
         */
        private @Nullable WorkspaceEdit takeEdit() {
            if (editTaken) {
                return null;
            }
            editTaken = true;
            return request.isDone() && !request.isCompletedExceptionally() ? request.join() : null;
        }
    }

}

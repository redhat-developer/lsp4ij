/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.DeleteFile;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Checks that a workspace edit still belongs to the connection and documents it was prepared for.
 * Capture before sending a rename request, then validate the response inside the write command
 * that applies it. For an incoming {@code workspace/applyEdit}, capture when the request arrives.
 * <p>
 * Two independent checks are needed: editor modification stamps detect typing while a reply is
 * pending, and LSP versions identify the text the server used. A matching version alone is not
 * enough when local changes have not yet been synchronized. Synchronizer identity also detects
 * a close/reopen, even if IntelliJ reuses the same {@link com.intellij.openapi.editor.Document}.
 * <p>
 * All text targets are checked before any mutation. This prevents partial application due to a
 * stale target. Failures inside {@link LSPIJUtils#applyWorkspaceEdit(WorkspaceEdit)} still follow
 * the existing application behavior, without rollback. An unversioned incoming edit can only
 * be checked for changes since receipt, not before it. A version below 1 is a placeholder, not one
 * LSP4IJ sent, since it numbers document versions from 1; intellij-quarkus and Liberty Tools send 0.
 * A reply with such a version is accepted only if the server had been sent the text when its request
 * was captured, since the server reads that text before the request. An incoming edit follows no
 * request of ours and can cross a change on its way, so its placeholder version can describe any
 * text; it is refused, as VS Code does. Documents that were not synchronized with
 * this server at capture have no captured buffer state, so edits to them retain the existing
 * behavior unless this server has opened them since.
 * <p>
 * The targets of a reply are not known at capture, so a document that this server opened since is checked
 * against {@link TextChanges}: until it was opened, the server could only read the file, and after, it was sent
 * the document. The edit applies to it only if both are the text the file had at capture: the document has no
 * unsaved changes, and neither it nor the file has changed since.
 */
@ApiStatus.Internal
public final class WorkspaceEditSnapshot {

    private final LanguageServerWrapper wrapper;
    private final @Nullable LanguageServer server;
    private final BooleanSupplier ownerActive;
    private final boolean request;
    // The TextChanges sequence at capture
    private final long changes;
    private final Map<URI, DocumentState> documents;

    private WorkspaceEditSnapshot(@NotNull LanguageServerWrapper wrapper,
                                  @Nullable LanguageServer server,
                                  @NotNull BooleanSupplier ownerActive,
                                  boolean request) {
        this.wrapper = wrapper;
        this.server = server;
        this.ownerActive = ownerActive;
        this.request = request;
        // Read before the documents: a change after it counts as one since capture
        this.changes = TextChanges.current();
        // Rename replies can edit other files, so the targets are not known at capture time.
        var states = new HashMap<URI, DocumentState>();
        for (var opened : wrapper.getOpenedDocuments()) {
            var synchronizer = opened.getSynchronizer();
            URI uri = wrapper.toUri(opened.getFile());
            if (synchronizer != null && uri != null) {
                // The stamp is read first: a change between the two reads either leaves the text unsent
                // or changes the stamp, and both refuse an edit with a placeholder version.
                long stamp = synchronizer.getDocument().getModificationStamp();
                states.put(uri, new DocumentState(synchronizer, stamp, request && synchronizer.isCurrentTextSent()));
            }
        }
        this.documents = Map.copyOf(states);
    }

    /**
     * Captures state for the exact server selected for a request, even if its wrapper restarts.
     * Capture before the request is sent: a reply that names a placeholder version, below 1, is
     * accepted only if the server had been sent the document's text by then.
     */
    public static @NotNull WorkspaceEditSnapshot capture(@NotNull LanguageServerItem server) {
        return capture(server.getServerWrapper(), server.getServer(), () -> true, true);
    }

    /**
     * Also checks the lifetime of the client receiving an edit. Its wrapper can outlive it
     * after a restart, so comparing the wrapper's current server alone is not sufficient.
     * The edit follows no request, so a placeholder version, below 1, is refused.
     */
    public static @NotNull WorkspaceEditSnapshot capture(@NotNull LanguageServerWrapper wrapper,
                                                         @NotNull BooleanSupplier ownerActive) {
        return capture(wrapper, wrapper.getLanguageServer(), ownerActive, false);
    }

    private static @NotNull WorkspaceEditSnapshot capture(@NotNull LanguageServerWrapper wrapper,
                                                          @Nullable LanguageServer server,
                                                          @NotNull BooleanSupplier ownerActive,
                                                          boolean request) {
        // Only thread-safe state is read: the opened-document map, the synchronizers and the documents'
        // volatile modification stamps, the sequence of TextChanges, and for a request whether the text was sent,
        // under the synchronizer's briefly held monitor. So the capture never waits for a write action:
        // workspace/applyEdit captures on LSP4J's message thread, which must keep reading replies a write action
        // may wait for.
        return new WorkspaceEditSnapshot(wrapper, server, ownerActive, request);
    }

    /**
     * Returns a refusal reason, or {@code null} if the connection and the documents that the edit changes,
     * renames, overwrites or deletes are current.
     * The caller must retain write access from this check through application: releasing it
     * between the two would let typing invalidate a successful check. Validation itself does
     * not mutate files, including when a later target in the edit is stale.
     */
    public @Nullable String validate(@NotNull WorkspaceEdit edit) {
        ApplicationManager.getApplication().assertWriteAccessAllowed();
        if (!ownerActive.getAsBoolean() || wrapper.isDisposed() || wrapper.getProject().isDisposed()
                || server == null || wrapper.getLanguageServer() != server) {
            return LanguageServerBundle.message("lsp.workspace.edit.connection.changed");
        }
        if (edit.getDocumentChanges() != null) {
            // Resource edits may rename a document before its following text edit. Versions
            // and request ownership still belong to the original synchronized URI.
            var originalUris = new HashMap<String, String>();
            for (var change : edit.getDocumentChanges()) {
                if (change.isLeft()) {
                    TextDocumentEdit text = change.getLeft();
                    String uri = text.getTextDocument().getUri();
                    String reason = validateDocument(originalUri(originalUris, uri), text.getTextDocument().getVersion());
                    if (reason != null) {
                        return reason;
                    }
                } else if (change.getRight() instanceof RenameFile rename) {
                    String original = originalUri(originalUris, rename.getOldUri());
                    String reason = validateDocument(original, null);
                    if (reason != null) {
                        return reason;
                    }
                    originalUris.put(withoutTrailingSlash(rename.getNewUri()), withoutTrailingSlash(original));
                } else if (change.getRight() instanceof CreateFile create) {
                    // Creating with overwrite replaces the text of an existing document
                    String reason = validateDocument(originalUri(originalUris, create.getUri()), null);
                    if (reason != null) {
                        return reason;
                    }
                } else if (change.getRight() instanceof DeleteFile delete) {
                    // Deleting a document discards its text
                    String reason = validateDocument(originalUri(originalUris, delete.getUri()), null);
                    if (reason != null) {
                        return reason;
                    }
                }
            }
        } else if (edit.getChanges() != null) {
            for (String uri : edit.getChanges().keySet()) {
                String reason = validateDocument(uri, null);
                if (reason != null) {
                    return reason;
                }
            }
        }
        return null;
    }

    private @Nullable String validateDocument(@NotNull String uriString, @Nullable Integer version) {
        URI uri;
        try {
            uri = URI.create(uriString);
            VirtualFile file = FileUriSupport.findFileByUri(uriString, wrapper.getClientFeatures());
            if (file != null) {
                // Use the same URI as the opened-document map, including custom URI support.
                uri = wrapper.toUri(file);
            }
        } catch (IllegalArgumentException e) {
            return LanguageServerBundle.message("lsp.workspace.edit.uri.invalid", uriString);
        }
        var opened = wrapper.getOpenedDocument(uri);
        var synchronizer = opened != null ? opened.getSynchronizer() : null;
        var state = documents.get(uri);
        // Identity detects close/reopen; the stamp detects changes even if the text was restored.
        if (state != null && !state.matches(synchronizer)) {
            return LanguageServerBundle.message("lsp.workspace.edit.document.changed", uriString);
        }
        // Without a captured state, a document this server opened since has no request-time stamp. The server read
        // the file until the open, and was sent the document after it, so both must still be the file's text at capture.
        if (state == null && synchronizer != null && !isUnchangedSinceCapture(opened.getFile(), synchronizer.getDocument())) {
            return LanguageServerBundle.message("lsp.workspace.edit.document.opened", uriString);
        }
        // Documents that were never synchronized retain the existing behavior, as before.
        if (version != null && synchronizer != null) {
            if (version > 0) {
                // A supplied version must describe the current buffer, including pending local edits.
                if (!synchronizer.isCurrentVersion(version)) {
                    return LanguageServerBundle.message("lsp.workspace.edit.version.changed", uriString);
                }
            } else if (state != null ? !state.textSent() : !request) {
                // A version below 1 was never sent by LSP4IJ, so it can't tell which text the server used. A reply
                // describes the captured text if that was sent before its request, and the stamp above shows it is
                // still the document's. A document opened since had one text from the request on, checked above.
                // An incoming edit follows no request, so its placeholder is refused.
                return LanguageServerBundle.message("lsp.workspace.edit.version.changed", uriString);
            }
        }
        return null;
    }

    /**
     * Returns whether the text of a document that this server opened since capture is the file's text at capture:
     * the document has no unsaved changes, so its text is the file's, and neither the document nor the file has
     * changed since. A change that is undone still counts, since the server may have been sent the changed text.
     */
    private boolean isUnchangedSinceCapture(@NotNull VirtualFile file, @NotNull Document document) {
        return !FileDocumentManager.getInstance().isDocumentUnsaved(document) && !TextChanges.changedSince(file, changes);
    }

    /**
     * Resolves a destination URI back to its location at capture time. For example, after
     * {@code /old -> /new}, a text edit for {@code /new/a} must validate {@code /old/a}.
     * Keys and values have no trailing slash. The slash boundary excludes siblings such as
     * {@code /newer}; the longest prefix handles a later rename of a child directory.
     */
    private static String originalUri(Map<String, String> originalUris, String uri) {
        String destination = originalUris.keySet().stream()
                .filter(candidate -> uri.equals(candidate) || uri.startsWith(candidate + "/"))
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
        if (destination == null) {
            return uri;
        }
        return originalUris.get(destination) + uri.substring(destination.length());
    }

    private static String withoutTrailingSlash(String uri) {
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    private record DocumentState(DocumentContentSynchronizer synchronizer, long modificationStamp, boolean textSent) {
        boolean matches(@Nullable DocumentContentSynchronizer current) {
            return current == synchronizer && synchronizer.getDocument().getModificationStamp() == modificationStamp;
        }
    }

    /**
     * Marks a file each time its text may change: when its document changes, and when a VFS event changes,
     * creates, copies, moves, renames or deletes it, with the next number of a sequence. A snapshot records the
     * sequence at capture, so it can tell whether a file it had no state for changed since. Modification stamps
     * can't tell: an undo restores an earlier one. A mark is set after its change, so a file marked with a number
     * the snapshot had seen changed before the capture. A file in a directory marked since then counts as changed.
     */
    @ApiStatus.Internal
    public static final class TextChanges implements BulkFileListener, DocumentListener {

        private static final AtomicLong SEQUENCE = new AtomicLong();
        private static final Key<Long> CHANGED = Key.create("lsp4ij.workspaceEdit.textChanged");

        static long current() {
            return SEQUENCE.get();
        }

        /**
         * Returns whether the file, or a directory that contains it, may have changed since the given sequence.
         */
        static boolean changedSince(@NotNull VirtualFile file, long sequence) {
            for (VirtualFile current = file; current != null; current = current.getParent()) {
                Long changed = current.getUserData(CHANGED);
                if (changed != null && changed > sequence) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
            VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
            if (file != null) {
                mark(file, SEQUENCE.incrementAndGet());
            }
        }

        @Override
        public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
            long sequence = SEQUENCE.incrementAndGet();
            for (VFileEvent event : events) {
                // A copy creates a file, which a create event would have named
                VirtualFile file = event instanceof VFileCopyEvent copy ? copy.findCreatedFile() : event.getFile();
                if (file != null) {
                    mark(file, sequence);
                }
            }
        }

        private static void mark(@NotNull VirtualFile file, long sequence) {
            // A deleted file has no text: the target's URI then finds another file or none
            if (file.isValid()) {
                file.putUserData(CHANGED, sequence);
            }
        }
    }
}

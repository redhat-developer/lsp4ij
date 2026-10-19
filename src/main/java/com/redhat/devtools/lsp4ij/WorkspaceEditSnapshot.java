/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
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
import java.util.Map;
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
 * be checked for changes since receipt, not before it. Documents that were not synchronized with
 * this server at capture have no captured buffer state, so edits to them retain the existing
 * behavior unless this server has opened them since.
 */
@ApiStatus.Internal
public final class WorkspaceEditSnapshot {

    private final LanguageServerWrapper wrapper;
    private final @Nullable LanguageServer server;
    private final BooleanSupplier ownerActive;
    private final Map<URI, DocumentState> documents;

    private WorkspaceEditSnapshot(@NotNull LanguageServerWrapper wrapper,
                                  @Nullable LanguageServer server,
                                  @NotNull BooleanSupplier ownerActive) {
        this.wrapper = wrapper;
        this.server = server;
        this.ownerActive = ownerActive;
        // Rename replies can edit other files, so the targets are not known at capture time.
        var states = new HashMap<URI, DocumentState>();
        for (var opened : wrapper.getOpenedDocuments()) {
            var synchronizer = opened.getSynchronizer();
            URI uri = wrapper.toUri(opened.getFile());
            if (synchronizer != null && uri != null) {
                states.put(uri, new DocumentState(synchronizer, synchronizer.getDocument().getModificationStamp()));
            }
        }
        this.documents = Map.copyOf(states);
    }

    /**
     * Captures state for the exact server selected for a request, even if its wrapper restarts.
     */
    public static @NotNull WorkspaceEditSnapshot capture(@NotNull LanguageServerItem server) {
        return capture(server.getServerWrapper(), server.getServer(), () -> true);
    }

    /**
     * Also checks the lifetime of the client receiving an edit. Its wrapper can outlive it
     * after a restart, so comparing the wrapper's current server alone is not sufficient.
     */
    public static @NotNull WorkspaceEditSnapshot capture(@NotNull LanguageServerWrapper wrapper,
                                                         @NotNull BooleanSupplier ownerActive) {
        return capture(wrapper, wrapper.getLanguageServer(), ownerActive);
    }

    private static @NotNull WorkspaceEditSnapshot capture(@NotNull LanguageServerWrapper wrapper,
                                                          @Nullable LanguageServer server,
                                                          @NotNull BooleanSupplier ownerActive) {
        // Only thread-safe state is read: the opened-document map, the synchronizers and the documents'
        // volatile modification stamps. So the capture never waits for a write action: workspace/applyEdit
        // captures on LSP4J's message thread, which must keep reading replies a write action may wait for.
        return new WorkspaceEditSnapshot(wrapper, server, ownerActive);
    }

    /**
     * Returns a refusal reason, or {@code null} if the connection and text targets are current.
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
        // Without a captured state, a document this server opened since has no request-time stamp.
        if (state == null && synchronizer != null) {
            return LanguageServerBundle.message("lsp.workspace.edit.document.opened", uriString);
        }
        // A supplied version must describe the current buffer, including pending local edits.
        // Documents that were never synchronized retain the existing behavior, as before.
        if (version != null && state != null && !synchronizer.isCurrentVersion(version)) {
            return LanguageServerBundle.message("lsp.workspace.edit.version.changed", uriString);
        }
        return null;
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

    private record DocumentState(DocumentContentSynchronizer synchronizer, long modificationStamp) {
        boolean matches(@Nullable DocumentContentSynchronizer current) {
            return current == synchronizer && synchronizer.getDocument().getModificationStamp() == modificationStamp;
        }
    }
}

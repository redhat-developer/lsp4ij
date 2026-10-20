/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentContent;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.eclipse.lsp4j.TextDocumentContentParams;
import org.eclipse.lsp4j.TextDocumentContentResult;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Connection-owned LSP 3.18 document content. URI lookup never performs I/O; callers
 * resolve navigation targets asynchronously before entering PSI read actions.
 */
public final class DocumentContentProvider implements Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentContentProvider.class);
    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    private final Project project;
    private final LanguageServer server;
    private final Supplier<List<String>> schemes;
    // All state below is guarded by this provider's monitor. No server call or EDT wait
    // occurs under that monitor. Replies must retain both entry and generation ownership.
    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<CompletableFuture<TextDocumentContentResult>, CompletableFuture<Void>> requests = new HashMap<>();
    private boolean disposed;

    private static final class Entry {
        private final URI uri;
        private final CompletableFuture<VirtualFile> available = new CompletableFuture<>();
        private DocumentContentFile file;
        private long generation;

        private Entry(URI uri) {
            this.uri = uri;
        }
    }

    public DocumentContentProvider(@NotNull Project project, @NotNull LanguageServer server,
                                   @NotNull Supplier<List<String>> schemes) {
        this.project = project;
        this.server = server;
        this.schemes = schemes;
    }

    public boolean owns(@NotNull LanguageServer candidate) {
        return server == candidate;
    }

    public synchronized @Nullable VirtualFile findFile(@NotNull String uri) {
        var entry = entries.get(uri);
        return disposed || project.isDisposed() || entry == null ? null : entry.file;
    }

    /** Coalesces initial reads; cancelling a caller's future does not cancel another caller. */
    public CompletableFuture<VirtualFile> resolve(@NotNull String value) {
        URI uri = acceptedUri(value);
        if (uri == null) {
            return CompletableFuture.completedFuture(null);
        }
        Entry entry;
        boolean created = false;
        synchronized (this) {
            if (disposed || project.isDisposed()) {
                return CompletableFuture.completedFuture(null);
            }
            entry = entries.get(value);
            if (entry == null) {
                entry = new Entry(uri);
                entries.put(value, entry);
                created = true;
            }
        }
        if (created) {
            fetch(entry);
        }
        return entry.available.thenApply(file -> findFile(value));
    }

    /** Refreshes only content already requested from this connection. */
    public CompletableFuture<Void> refresh(@NotNull String uri) {
        Entry entry;
        synchronized (this) {
            entry = disposed || project.isDisposed() ? null : entries.get(uri);
        }
        return entry == null ? CompletableFuture.completedFuture(null) : fetch(entry);
    }

    private @Nullable URI acceptedUri(String value) {
        try {
            var uri = URI.create(value);
            var scheme = uri.getScheme();
            var supported = schemes.get();
            // Disk resources remain owned by IntelliJ's local filesystem.
            return scheme != null && !"file".equalsIgnoreCase(scheme) && supported != null
                    && supported.stream().anyMatch(scheme::equalsIgnoreCase) ? uri : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private CompletableFuture<Void> fetch(Entry entry) {
        long generation;
        synchronized (this) {
            if (disposed || project.isDisposed() || entries.get(entry.uri.toString()) != entry) {
                return CompletableFuture.completedFuture(null);
            }
            generation = ++entry.generation;
        }
        CompletableFuture<TextDocumentContentResult> request;
        try {
            request = server.getWorkspaceService().textDocumentContent(new TextDocumentContentParams(entry.uri.toString()));
        } catch (RuntimeException e) {
            failed(entry, generation, e);
            return CompletableFuture.completedFuture(null);
        }
        var completed = new CompletableFuture<Void>();
        boolean retired;
        synchronized (this) {
            retired = disposed;
            if (!retired) {
                requests.put(request, completed);
            }
        }
        if (retired) {
            request.cancel(true);
            completed.complete(null);
            return completed;
        }
        var tracked = request;
        request.orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((result, error) -> {
            synchronized (this) {
                requests.remove(tracked);
            }
            if (error != null || result == null || result.getText() == null) {
                failed(entry, generation, error);
                completed.complete(null);
            } else {
                publish(entry, generation, result.getText(), completed);
            }
        });
        return completed;
    }

    private synchronized boolean current(Entry entry, long generation) {
        return !disposed && !project.isDisposed() && entries.get(entry.uri.toString()) == entry
                && entry.generation == generation;
    }

    private void failed(Entry entry, long generation, Throwable error) {
        boolean unavailable;
        synchronized (this) {
            unavailable = current(entry, generation) && entry.file == null;
            if (unavailable) {
                entries.remove(entry.uri.toString());
            }
        }
        if (unavailable) {
            entry.available.complete(null);
        }
        if (error != null) {
            LOGGER.debug("Cannot load language server document {}", entry.uri, error);
        }
    }

    private void publish(Entry entry, long generation, String text, CompletableFuture<Void> completed) {
        DocumentContentFile initial = null;
        boolean stale;
        synchronized (this) {
            stale = !current(entry, generation);
            if (!stale && entry.file == null) {
                // Creating an unobserved LightVirtualFile needs no EDT/write action.
                // In particular, navigation may be awaiting this result under a read action.
                initial = new DocumentContentFile(entry.uri, text);
                entry.file = initial;
            }
        }
        if (stale) {
            completed.complete(null);
            return;
        }
        if (initial != null) {
            entry.available.complete(initial);
            completed.complete(null);
            return;
        }
        // The reload changes the document, which needs a write-safe modality: with a modal dialog
        // open, it waits for the dialog to close.
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                WriteAction.run(() -> {
                    synchronized (this) {
                        if (!current(entry, generation)) {
                            return;
                        }
                        entry.file.replaceContent(text);
                        var manager = FileDocumentManager.getInstance();
                        var document = manager.getCachedDocument(entry.file);
                        if (document != null) {
                            // Reload retains the file identity and keeps the buffer clean/read-only.
                            manager.reloadFromDisk(document, project);
                        }
                    }
                });
                completed.complete(null);
            } catch (RuntimeException e) {
                failed(entry, generation, e);
                completed.completeExceptionally(e);
            }
        }, ModalityState.nonModal());
    }

    @Override
    public void dispose() {
        List<Entry> retired;
        Map<CompletableFuture<TextDocumentContentResult>, CompletableFuture<Void>> pending;
        synchronized (this) {
            if (disposed) {
                return;
            }
            disposed = true;
            retired = new ArrayList<>(entries.values());
            entries.clear();
            pending = new HashMap<>(requests);
            requests.clear();
        }
        pending.forEach((request, completion) -> {
            request.cancel(true);
            completion.complete(null);
        });
        retired.forEach(entry -> entry.available.complete(null));
        // Lookup ownership is already retired. Close editors and invalidate under the same
        // write action, so no background PSI reader can observe an invalid file still in use.
        // Like the reload, it waits for a modal dialog to close.
        ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(() -> {
            for (var entry : retired) {
                if (entry.file != null) {
                    if (!project.isDisposed()) {
                        FileEditorManager.getInstance(project).closeFile(entry.file);
                    }
                    entry.file.setValid(false);
                }
            }
        }), ModalityState.nonModal());
    }
}

/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.redhat.devtools.lsp4ij.internal.ApplicationUtils.runCancellableReadAction;

/**
 * Synchronize IntelliJ document (open, content changed, close, save)
 * with LSP notifications (didOpen, didChanged, didClose, didSave).
 */
public class DocumentContentSynchronizer implements DocumentListener, Disposable {

    public enum RefreshPullDiagnosticOrigin {
        ON_REGISTER_CAPABILITY,
        ON_WORKSPACE_REFRESH
    }

    private static final long WAIT_AFTER_SENDING_DID_OPEN = 500L;

    private final @NotNull LanguageServerWrapper languageServerWrapper;
    private final @NotNull Document document;
    private final @NotNull String fileUri;
    private final @NotNull TextDocumentSyncOptions syncOptions;
    private final @NotNull VirtualFile file;
    private final @Nullable String documentText;
    private final long documentModificationStamp;
    private final @Nullable String languageId;

    // Update the LSP version and its matching editor stamp together under changeEvents.
    // The stamp is -1 until the text associated with a version is known. Volatile also
    // supports callers that only need getVersion(), without inspecting the pending changes.
    private volatile int version = 0;
    private long synchronizedModificationStamp = -1;
    private final List<TextDocumentContentChangeEvent> changeEvents;
    private volatile @NotNull CompletableFuture<Void> lastDidChangeFuture = CompletableFuture.completedFuture(null);
    private @Nullable CompletableFuture<LanguageServer> didOpenFuture;
    // Whether didOpen and didClose have been queued. Both change under changeEvents, where the
    // notifications they describe are queued, so that the server receives didOpen before any
    // didChange or didClose, nothing after didClose, and didClose only after a didOpen.
    private boolean opened;
    private boolean closed;

    // Create the Alarm while its owner is alive, never from a delayed document callback.
    // It does not schedule work until a request is added.
    private final Disposable diagnosticLifetime;
    private final Alarm debouncePullDiagnosticsAlarm;
    private boolean diagnosticNotPulledOnDidOpen;

    /**
     * Compatibility constructor for callers without a stamp for the supplied text.
     * If documentText is supplied, versioned edits cannot be accepted until a didChange
     * establishes which editor state the protocol version represents.
     */
    public DocumentContentSynchronizer(@NotNull LanguageServerWrapper languageServerWrapper,
                                       @NotNull String fileUri,
                                       @NotNull VirtualFile file,
                                       @NotNull Document document,
                                       @Nullable String documentText,
                                       @Nullable String languageId) {
        this(languageServerWrapper, fileUri, file, document, documentText, -1, languageId);
    }

    DocumentContentSynchronizer(@NotNull LanguageServerWrapper languageServerWrapper,
                                @NotNull String fileUri,
                                @NotNull VirtualFile file,
                                @NotNull Document document,
                                @Nullable String documentText,
                                long documentModificationStamp,
                                @Nullable String languageId) {
        this.languageServerWrapper = languageServerWrapper;
        this.file = file;
        this.fileUri = fileUri;
        this.syncOptions = languageServerWrapper.getSyncOptions();
        this.document = document;
        this.documentText = documentText;
        this.documentModificationStamp = documentModificationStamp;
        this.languageId = languageId;

        // Initialize LSP change events
        changeEvents = new ArrayList<>();
        diagnosticLifetime = Disposer.newDisposable();
        debouncePullDiagnosticsAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, diagnosticLifetime);
    }

    public @NotNull CompletableFuture<LanguageServer> getDidOpenFuture() {
        if (didOpenFuture != null) {
            return didOpenFuture;
        }
        return getDidOpenFutureSync();
    }

    private synchronized @NotNull CompletableFuture<LanguageServer> getDidOpenFutureSync() {
        if (didOpenFuture != null) {
            return didOpenFuture;
        }

        if (!syncOptions.getOpenClose()) {
            // Don't send textDocument/didOpen
            return didOpenFuture = languageServerWrapper
                    .getInitializedServer();
        }

        // add a document buffer
        TextDocumentItem textDocument = new TextDocumentItem();
        textDocument.setUri(this.fileUri);
        long modificationStamp;
        if (documentText != null) {
            // Reuse the captured text and stamp: rereading the document here can deadlock
            // when a VFS rename holds the write lock while waiting for reconnection.
            textDocument.setText(documentText);
            modificationStamp = documentModificationStamp;
        } else {
            modificationStamp = readDocumentState(() -> {
                // The current text already contains the changes queued so far, since this read
                // cannot overlap the write action that queued them. A didChange sending them after
                // this didOpen would apply them twice.
                synchronized (changeEvents) {
                    changeEvents.clear();
                }
                textDocument.setText(document.getText());
                return document.getModificationStamp();
            });
        }

        @NotNull String languageId = this.languageId != null ? this.languageId :
                languageServerWrapper.getServerDefinition().getLanguageId(file, languageServerWrapper.getProject());
        textDocument.setLanguageId(languageId);
        var initializedServer = languageServerWrapper.getInitializedServer();
        CompletableFuture<LanguageServer> didOpen;
        synchronized (changeEvents) {
            if (closed) {
                // The document was closed first; an open now could never be balanced by a close
                return didOpenFuture = CompletableFuture.failedFuture(
                        new CancellationException("The document was closed before it was opened"));
            }
            version = languageServerWrapper.nextDocumentVersion();
            synchronizedModificationStamp = modificationStamp;
            textDocument.setVersion(version);
            // Send didOpen like didChange and didClose, so that it keeps its place among them, and
            // decide it under the same monitor as them, so that neither can reach the server first
            opened = true;
            didOpen = languageServerWrapper.sendNotification(initializedServer, ls -> {
                ls.getTextDocumentService()
                        .didOpen(new DidOpenTextDocumentParams(textDocument));
                return ls;
            });
        }
        didOpenFuture = didOpen
                // Changes made since a captured text was read wait for the didOpen; send them after it,
                // before the requests that wait for the open. Sending them may wait for read access, so
                // it runs on a pooled thread: a caller can hold the wrapper's documentsLock, which a
                // write action can be waiting for.
                .thenCompose(ls -> CompletableFuture.supplyAsync(this::sendDidChangeEvents)
                        .thenCompose(sent -> sent)
                        .thenApply(sent -> ls))
                .thenCompose(ls ->
                        CompletableFuture.supplyAsync(() -> {
                            if (!ApplicationManager.getApplication().isUnitTestMode()) {
                                try {
                                    // Wait 500ms after sending didOpen notification
                                    // to be sure that the notification has been sent before
                                    // consuming other LSP request like 'textDocument/codeLens'.
                                    TimeUnit.MILLISECONDS.sleep(WAIT_AFTER_SENDING_DID_OPEN);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                            return ls;
                        })
                );
        processPullDiagnosticIfNeeded(didOpenFuture, version);
        return didOpenFuture;
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        DocumentListener.super.documentChanged(event);
        var syncKind = getTextDocumentSyncKind();
        if (syncKind == TextDocumentSyncKind.None) {
            return;
        }
        if (syncKind == TextDocumentSyncKind.Full) {
            synchronized (changeEvents) {
                changeEvents.clear();
                changeEvents.add(createChangeEvent(event));
            }
        }

        if (ApplicationManager.getApplication().isUnitTestMode()) {
            sendDidChangeEvents();
        } else {
            Project project = languageServerWrapper.getProject();
            PsiDocumentManager.getInstance(project)
                    .performForCommittedDocument(event.getDocument(), this::sendDidChangeEvents);
        }
    }

    /**
     * Flushes any pending textDocument/didChange notifications to the language server.
     * This ensures the server has an up-to-date view of the document before other requests.
     * <p>
     * This method should be called before sending requests that depend on the current
     * document state (e.g., onTypeFormatting) to avoid out-of-bounds position errors.
     * <p>
     * If events are pending, sends them and returns a future that completes when the
     * notification has been dispatched. If events were already sent by another caller
     * (e.g., {@code performForCommittedDocument}), returns the future tracking that
     * dispatch, ensuring the caller waits for it to complete before proceeding.
     *
     * @return a CompletableFuture that completes when all didChange notifications have been dispatched
     */
    public CompletableFuture<Void> flushPendingChanges() {
        return sendDidChangeEvents();
    }

    /**
     * Reads document state on this thread when it already has read access, as inside a write action,
     * and otherwise in a cancellable read action, so a pending write action is never blocked.
     */
    private <T> T readDocumentState(@NotNull Supplier<T> read) {
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return read.get();
        }
        return runCancellableReadAction(read::get, languageServerWrapper);
    }

    private CompletableFuture<Void> sendDidChangeEvents() {
        synchronized (changeEvents) {
            if (changeEvents.isEmpty()) {
                // No document state to read. On-type formatting may await this barrier in a write action.
                return lastDidChangeFuture;
            }
        }
        // Incremental events are queued before the document changes. Acquire read access before
        // consuming them under the monitor so the batch and stamp describe the same completed state.
        // The block makes no cancellation check once it starts, so a cancellable read consumes the
        // batch once: it is only retried before starting, while a write action is pending.
        var initializedServer = languageServerWrapper.getInitializedServer();
        return readDocumentState(() -> {
            CompletableFuture<Void> barrier;
            final int version;
            final CompletableFuture<LanguageServer> didChange;
            synchronized (changeEvents) {
                if (changeEvents.isEmpty()) {
                    return lastDidChangeFuture;
                }
                if (closed || syncOptions.getOpenClose() && !opened) {
                    // Before didOpen the changes wait for it, which either contains them or is
                    // followed by them; after didClose the server no longer has the document.
                    return lastDidChangeFuture;
                }
                List<TextDocumentContentChangeEvent> events = new ArrayList<>(changeEvents);
                changeEvents.clear();
                barrier = new CompletableFuture<>();
                lastDidChangeFuture = barrier;
                // Record the state represented by this batch before another flush can advance it.
                version = languageServerWrapper.nextDocumentVersion();
                this.version = version;
                synchronizedModificationStamp = document.getModificationStamp();

                // send 'textDocument/didChange' notification, queued under the monitor so that a
                // concurrent close cannot queue its didClose before it
                DidChangeTextDocumentParams changeParamsToSend = new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(), events);
                changeParamsToSend.getTextDocument().setUri(fileUri);
                changeParamsToSend.getTextDocument().setVersion(version);
                didChange = languageServerWrapper.sendNotification(initializedServer, ls -> {
                    ls.getTextDocumentService().didChange(changeParamsToSend);
                    return ls;
                });
            }
            processPullDiagnosticIfNeeded(didChange, version);
            didChange.whenComplete((ls, ex) -> {
                if (ex != null) {
                    barrier.completeExceptionally(ex);
                } else {
                    barrier.complete(null);
                }
            });
            return barrier;
        });
    }

    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
        if (getTextDocumentSyncKind() == TextDocumentSyncKind.Incremental) {
            // this really needs to happen before event gets actually
            // applied, to properly compute positions
            synchronized (changeEvents) {
                changeEvents.add(createChangeEvent(event));
            }
        }
    }

    private TextDocumentContentChangeEvent createChangeEvent(DocumentEvent event) {
        Document document = event.getDocument();
        TextDocumentSyncKind syncKind = getTextDocumentSyncKind();
        switch (syncKind) {
            case None:
                return null;
            case Full: {
                TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent();
                changeEvent.setText(event.getDocument().getText());
                return changeEvent;
            }
            case Incremental: {
                TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent();
                CharSequence newText = event.getNewFragment();
                int offset = event.getOffset();
                int length = event.getOldLength();
                try {
                    // try to convert the Eclipse start/end offset to LS range.
                    Range range = new Range(LSPIJUtils.toPosition(offset, document),
                            LSPIJUtils.toPosition(offset + length, document));
                    changeEvent.setRange(range);
                    changeEvent.setText(newText.toString());
                    changeEvent.setRangeLength(length);
                } catch (Exception e) {
                    // error while conversion (should never occur)
                    // set the full document text as changes.
                    changeEvent.setText(document.getText());
                }
                return changeEvent;
            }
        }
        return null;
    }

    public void documentSaved() {
        if (!languageServerWrapper.isSaveSupported()) {
            // Don't send textDocument/didSave
            return;
        }

        // Send textDocument/didSave
        languageServerWrapper.sendNotification(ls -> {
            TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri);
            var saveOptions = syncOptions.getSave();
            boolean includedText = saveOptions.isRight() && saveOptions.getRight().getIncludeText() != null && saveOptions.getRight().getIncludeText();
            DidSaveTextDocumentParams params = new DidSaveTextDocumentParams(identifier, includedText ? document.getText() : null);
            ls.getTextDocumentService().didSave(params);
            return ls;
        });
    }

    public void documentClosed() {
        // When LS is shut down all documents are being disconnected. No need to send "didClose" message to the LS that is being shut down or not yet started
        boolean sendDidClose = syncOptions.getOpenClose() && languageServerWrapper.isActive();
        CompletableFuture<LanguageServer> initializedServer = null;
        if (sendDidClose) {
            // Ensure pending textDocument/didChange events are sent before the file is closed.
            //
            // This handles the specific case where a file is being renamed:
            // the original file is closed (triggering a textDocument/didClose),
            // but workspace/willRenameFiles may modify the file before that.
            // Without this call, the didChange notification could be sent
            // after didClose, breaking the expected LSP event order.
            if (!changeEvents.isEmpty()) {
                sendDidChangeEvents();
            }
            initializedServer = languageServerWrapper.getInitializedServer();
        }

        synchronized (changeEvents) {
            if (closed) {
                return;
            }
            closed = true;
            // A close notification requires a previous open notification
            if (initializedServer != null && opened) {
                // Send textDocument/didClose
                languageServerWrapper.sendNotification(initializedServer, ls -> {
                    TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri);
                    DidCloseTextDocumentParams params = new DidCloseTextDocumentParams(identifier);
                    ls.getTextDocumentService().didClose(params);
                    return ls;
                });
            }
        }
    }

    /**
     * Returns the text document sync kind capabilities of the server and {@link TextDocumentSyncKind#Full} otherwise.
     *
     * @return the text document sync kind capabilities of the server and {@link TextDocumentSyncKind#Full} otherwise.
     */
    private TextDocumentSyncKind getTextDocumentSyncKind() {
        return syncOptions.getChange();
    }

    /**
     * Returns the document.
     *
     * @return the document.
     */
    public @NotNull Document getDocument() {
        return this.document;
    }

    /**
     * Returns the current version of the LSP {@link TextDocumentItem}.
     *
     * @return the current version of the LSP {@link TextDocumentItem}.
     */
    int getVersion() {
        return version;
    }

    /**
     * Returns whether the given version describes the current buffer, with no pending changes.
     * The caller holds read or write access to keep the document stable. Checking the version,
     * stamp and event queue under the same monitor prevents mixing states from different flushes.
     * This describes the text assigned to a notification, not an acknowledgement from the server.
     */
    boolean isCurrentVersion(int expectedVersion) {
        synchronized (changeEvents) {
            return version == expectedVersion && changeEvents.isEmpty()
                    && document.getModificationStamp() == synchronizedModificationStamp;
        }
    }

    @Override
    public void dispose() {
        // Retire diagnostics before documentClosed can flush changes and schedule another pull.
        // A private parent lets disposal and scheduling share a lock; registering the Alarm under
        // this synchronizer would dispose the child before this method can acquire that lock.
        synchronized (diagnosticLifetime) {
            Disposer.dispose(diagnosticLifetime);
        }
        documentClosed();
    }

    /**
     * Refresh pull diagnostic according the given origin.
     *
     * @param origin the origin (call by 'textDocument/diagnostic' by dynamic registerCapability or by 'workspace/diagnostic/refresh')
     */
    public void refreshPullDiagnostic(@NotNull RefreshPullDiagnosticOrigin origin) {
        int currentVersion = version;
        if (origin == RefreshPullDiagnosticOrigin.ON_REGISTER_CAPABILITY) {
            // called by 'textDocument/diagnostic' by dynamic registerCapability
            // we need to consume the 'textDocument/diagnostic' if when didOpen has occurred, the pull diagnostic
            // capability was not enabled.
            if (!isDiagnosticNotPulledOnDidOpen()) {
                // the didOpen have already consumed the 'textDocument/diagnostic', do nothing
                return;
            }
        } else if (origin == RefreshPullDiagnosticOrigin.ON_WORKSPACE_REFRESH) {
            // called by 'workspace/diagnostic/refresh'
            // Set version to -1 to force the call of the pull diagnostic even if version has not changed.
            currentVersion = -1;
        }
        processPullDiagnosticIfNeeded(null, currentVersion);
    }

    /**
     * Process LSP pull diagnostic if the language server supports it.
     *
     * @param didFuture the didOpen, didChange future.
     * @param version   the current document version.
     */
    private void processPullDiagnosticIfNeeded(@Nullable CompletableFuture<LanguageServer> didFuture,
                                               int version) {
        if (debouncePullDiagnosticsAlarm.isDisposed()) {
            // Sending a notification can restart the connection and dispose this synchronizer.
            return;
        }
        boolean debounceValidation = didFuture != null && !ApplicationManager.getApplication().isUnitTestMode();
        if (!isPullDiagnosticsSupported()) {
            // The language server doesn't support pull diagnostics.
            diagnosticNotPulledOnDidOpen = didFuture != null && didFuture == didOpenFuture;
            return;
        }
        if (!debounceValidation) {
            // Refresh pull diagnostic without debounce
            if (version != -1 && version != this.version) {
                // The document has changed, do nothing
                return;
            }
            if (didOpenFuture == null) {
                return;
            }
            var ls = didOpenFuture.getNow(null);
            if (ls == null) {
                // The didOpen is not finished, refresh the pull diagnostic when server is ready
                didOpenFuture.
                        thenAccept(readyLs -> refreshPullDiagnostic(version, readyLs));
            } else {
                // The server is ready, refresh the pull diagnostics
                refreshPullDiagnostic(version, ls);
            }
            return;
        }
        // Refresh pull diagnostic with debounce (after a didOpen , didChange).
        synchronized (diagnosticLifetime) {
            if (debouncePullDiagnosticsAlarm.isDisposed()) {
                return;
            }
            debouncePullDiagnosticsAlarm.cancelAllRequests();
        }
        didFuture
                .thenApply(ls -> {
                    // didOpen, didChange notification has been sent, consume 'textDocument/diagnostic' with debounce mode.
                    synchronized (diagnosticLifetime) {
                        if (!debouncePullDiagnosticsAlarm.isDisposed()) {
                            debouncePullDiagnosticsAlarm.addRequest(() -> refreshPullDiagnostic(version, ls), 500);
                        }
                    }
                    return ls;
                });
    }

    private void refreshPullDiagnostic(int version, @NotNull LanguageServer ls) {
        if (debouncePullDiagnosticsAlarm.isDisposed() || (version != -1 && version != this.version)) {
            return;
        }
        // Consume 'textDocument/diagnostic'
        DocumentDiagnosticParams params = new DocumentDiagnosticParams();
        params.setIdentifier(getPullIdentifier());
        params.setTextDocument(new TextDocumentIdentifier(fileUri));
        ls.getTextDocumentService()
                .diagnostic(params)
                .thenAcceptAsync(diagnosticReport -> {
                    // Disposal must wait for publication, or prevent the entire reply from being applied.
                    synchronized (diagnosticLifetime) {
                        if (debouncePullDiagnosticsAlarm.isDisposed() || diagnosticReport == null ||
                                (version != -1 && version != this.version)) {
                            // The document changed or its synchronizer was retired while the request was pending.
                            return;
                        }
                        var clientFeatures = languageServerWrapper.getClientFeatures();
                        // Update the diagnostics cache from the opened file and refresh UI to process LSPDiagnosticsApplier.
                        if (diagnosticReport.isLeft()) {
                            RelatedFullDocumentDiagnosticReport fileReport = diagnosticReport.getLeft();
                            // Update the diagnostics cache from the opened file
                            updatePullDiagnostics(file, fileReport);
                            updateRelatedPullDiagnostics(fileReport.getRelatedDocuments(), clientFeatures);
                        } else if (diagnosticReport.isRight()) {
                            // The opened file's diagnostics are unchanged, but those of related documents can change.
                            updateRelatedPullDiagnostics(diagnosticReport.getRight().getRelatedDocuments(), clientFeatures);
                        }
                    }
                });
    }

    /**
     * Updates the diagnostics of the documents related to a pulled report. A report of either kind can
     * carry them: code in one file can produce diagnostics in another file it depends on.
     */
    private void updateRelatedPullDiagnostics(@Nullable Map<String, Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>> relatedDocuments,
                                              @NotNull LSPClientFeatures clientFeatures) {
        if (relatedDocuments == null) {
            return;
        }
        for (var relatedDocument : relatedDocuments.entrySet()) {
            String documentUri = relatedDocument.getKey();
            Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport> fileReport = relatedDocument.getValue();
            if (fileReport != null) {
                if (fileReport.isLeft()) {
                    VirtualFile file = FileUriSupport.findFileByUri(documentUri, clientFeatures);
                    if (file != null) {
                        updatePullDiagnostics(file, fileReport.getLeft());
                    }
                } else if (fileReport.isRight()) {
                    // TODO: implement UnchangedDocumentDiagnosticReport
                }
            }
        }
    }

    private void updatePullDiagnostics(@NotNull VirtualFile file,
                                       @NotNull FullDocumentDiagnosticReport diagnosticReport) {
        // Update diagnostics for the opened/closed document
        List<Diagnostic> diagnostics = diagnosticReport.getItems() != null ? diagnosticReport.getItems() : Collections.emptyList();
        String identifier = getPullIdentifier();
        if (identifier == null) {
            identifier = LSPDocumentBase.PULL_DIAGNOSTIC_IDENTIFIER;
        }
        languageServerWrapper.updateDiagnostics(file, identifier, diagnostics, null);
    }

    @Nullable
    private String getPullIdentifier() {
        var clientFeatures = languageServerWrapper.getClientFeatures();
        return clientFeatures.getDiagnosticFeature().getDiagnosticIdentifier();
    }

    public boolean isPullDiagnosticsSupported() {
        return languageServerWrapper.getClientFeatures().getDiagnosticFeature().isDiagnosticSupported(file);
    }

    private boolean isDiagnosticNotPulledOnDidOpen() {
        return diagnosticNotPulledOnDidOpen;
    }
}

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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Synchronize IntelliJ document (open, content changed, close, save)
 * with LSP notifications (didOpen, didChanged, didClose, didSave).
 */
public class DocumentContentSynchronizer implements DocumentListener {

    private static final long WAIT_AFTER_SENDING_DID_OPEN = 500L;

    private final @NotNull LanguageServerWrapper languageServerWrapper;
    private final @NotNull Document document;
    private final @NotNull String fileUri;
    private final TextDocumentSyncKind syncKind;
    private final @NotNull VirtualFile file;
    private final @Nullable String documentText;
    private final @Nullable String languageId;

    private int version = 0;
    private final List<TextDocumentContentChangeEvent> changeEvents;
    @NotNull private CompletableFuture<Void> didOpenFuture;

    public DocumentContentSynchronizer(@NotNull LanguageServerWrapper languageServerWrapper,
                                       @NotNull String fileUri,
                                       @NotNull VirtualFile file,
                                       @NotNull Document document,
                                       @Nullable String documentText,
                                       @Nullable String languageId,
                                       @Nullable TextDocumentSyncKind syncKind) {
        this.languageServerWrapper = languageServerWrapper;
        this.file = file;
        this.fileUri = fileUri;
        this.syncKind = syncKind != null ? syncKind : TextDocumentSyncKind.Full;
        this.document = document;
        this.documentText = documentText;
        this.languageId = languageId;

        // Initialize LSP change events
        changeEvents = new ArrayList<>();
    }

    public @NotNull CompletableFuture<Void> getDidOpenFuture() {
        if (didOpenFuture != null) {
            return didOpenFuture;
        }
        return getDidOpenFutureSync();
    }

    private synchronized @NotNull CompletableFuture<Void> getDidOpenFutureSync() {
        if (didOpenFuture != null) {
            return didOpenFuture;
        }
        // add a document buffer
        TextDocumentItem textDocument = new TextDocumentItem();
        textDocument.setUri(this.fileUri);
        textDocument.setText(documentText != null ? documentText : document.getText());

        @NotNull String languageId = this.languageId != null ? this.languageId :
                languageServerWrapper.getServerDefinition().getLanguageId(file, languageServerWrapper.getProject());
        textDocument.setLanguageId(languageId);
        textDocument.setVersion(++version);
        didOpenFuture = languageServerWrapper
                .getInitializedServer()
                .thenAcceptAsync(ls -> ls.getTextDocumentService()
                        .didOpen(new DidOpenTextDocumentParams(textDocument)))
                .thenCompose(result ->
                        CompletableFuture.runAsync(() -> {
                            if (ApplicationManager.getApplication().isUnitTestMode()) {
                                return;
                            }
                            try {
                                // Wait 500ms after sending didOpen notification
                                // to be sure that the notification has been sent before
                                // consuming other LSP request like 'textDocument/codeLens'.
                                TimeUnit.MILLISECONDS.sleep(WAIT_AFTER_SENDING_DID_OPEN);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        })
                );
        return didOpenFuture;
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        DocumentListener.super.documentChanged(event);
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

    private void sendDidChangeEvents() {
        List<TextDocumentContentChangeEvent> events;
        synchronized (changeEvents) {
            if (changeEvents.isEmpty()) {
                // Don't send didChange notification with empty contentChanges.
                return;
            }
            events = new ArrayList<>(changeEvents);
            changeEvents.clear();
        }

        DidChangeTextDocumentParams changeParamsToSend = new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(), events);
        changeParamsToSend.getTextDocument().setUri(fileUri);
        changeParamsToSend.getTextDocument().setVersion(++version);
        languageServerWrapper.sendNotification(ls -> ls.getTextDocumentService().didChange(changeParamsToSend));
    }

    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
        if (syncKind == TextDocumentSyncKind.Incremental) {
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
        ServerCapabilities serverCapabilities = languageServerWrapper.getServerCapabilities();
        if (serverCapabilities != null) {
            Either<TextDocumentSyncKind, TextDocumentSyncOptions> textDocumentSync = serverCapabilities.getTextDocumentSync();
            if (textDocumentSync.isRight() && textDocumentSync.getRight().getSave() == null) {
                return;
            }
        }
        TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri);
        DidSaveTextDocumentParams params = new DidSaveTextDocumentParams(identifier, document.getText());
        languageServerWrapper.getInitializedServer().thenAcceptAsync(ls -> ls.getTextDocumentService().didSave(params));
    }

    public void documentClosed() {
        // When LS is shut down all documents are being disconnected. No need to send "didClose" message to the LS that is being shut down or not yet started
        if (languageServerWrapper.isActive()) {
            TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri);
            DidCloseTextDocumentParams params = new DidCloseTextDocumentParams(identifier);
            languageServerWrapper.sendNotification(ls -> ls.getTextDocumentService().didClose(params));
        }
    }

    /**
     * Returns the text document sync kind capabilities of the server and {@link TextDocumentSyncKind#Full} otherwise.
     *
     * @return the text document sync kind capabilities of the server and {@link TextDocumentSyncKind#Full} otherwise.
     */
    private TextDocumentSyncKind getTextDocumentSyncKind() {
        return syncKind;
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

}

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

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Synchronize IntelliJ document (open, content changed, close, save)
 * with LSP notifications (didOpen, didChanged, didClose, didSave).
 */
public class DocumentContentSynchronizer implements DocumentListener {
    private final static Logger LOGGER = LoggerFactory.getLogger(DocumentContentSynchronizer.class);

    private final @NotNull LanguageServerWrapper languageServerWrapper;
    private final @NotNull Document document;
    private final @NotNull String fileUri;
    private final TextDocumentSyncKind syncKind;

    private int version = 0;
    private final List<TextDocumentContentChangeEvent> changeEvents;
    private long modificationStamp;
    final @NotNull
    CompletableFuture<Void> didOpenFuture;

    public DocumentContentSynchronizer(@NotNull LanguageServerWrapper languageServerWrapper,
                                       @NotNull URI fileUri,
                                       @NotNull Document document,
                                       TextDocumentSyncKind syncKind) {
        this.languageServerWrapper = languageServerWrapper;
        this.fileUri = fileUri.toASCIIString();
        this.modificationStamp = -1;
        this.syncKind = syncKind != null ? syncKind : TextDocumentSyncKind.Full;

        this.document = document;
        // add a document buffer
        TextDocumentItem textDocument = new TextDocumentItem();
        textDocument.setUri(this.fileUri);
        textDocument.setText(document.getText());

        Language language = LSPIJUtils.getDocumentLanguage(document, languageServerWrapper.getProject());
        String languageId = languageServerWrapper.getLanguageId(language);

        //TODO: determine languageId more precisely
        /*IPath fromPortableString = Path.fromPortableString(this.fileUri.getPath());
        if (languageId == null) {
            languageId = fromPortableString.getFileExtension();
            if (languageId == null) {
                languageId = fromPortableString.lastSegment();
            }
        }*/

        textDocument.setLanguageId(languageId);
        textDocument.setVersion(++version);
        didOpenFuture = languageServerWrapper.getInitializedServer()
                .thenAcceptAsync(ls -> ls.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(textDocument)));

        // Initialize LSP change events
        changeEvents = new ArrayList<>();
    }

    @Override
    public void documentChanged(DocumentEvent event) {
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
            PsiDocumentManager.getInstance(languageServerWrapper.getProject()).performForCommittedDocument(event.getDocument(), this::sendDidChangeEvents);
        }
    }

    private void sendDidChangeEvents() {
        List<TextDocumentContentChangeEvent> events = null;
        synchronized (changeEvents) {
            events = new ArrayList<>(changeEvents);
            changeEvents.clear();
        }

        DidChangeTextDocumentParams changeParamsToSend = new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(), events);
        changeParamsToSend.getTextDocument().setUri(fileUri.toString());
        changeParamsToSend.getTextDocument().setVersion(++version);
        languageServerWrapper.sendNotification(ls -> ls.getTextDocumentService().didChange(changeParamsToSend));
    }

    @Override
    public void beforeDocumentChange(DocumentEvent event) {
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
        TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri.toString());
        DidSaveTextDocumentParams params = new DidSaveTextDocumentParams(identifier, document.getText());
        languageServerWrapper.getInitializedServer().thenAcceptAsync(ls -> ls.getTextDocumentService().didSave(params));
    }

    public void documentClosed() {
        // When LS is shut down all documents are being disconnected. No need to send "didClose" message to the LS that is being shut down or not yet started
        if (languageServerWrapper.isActive()) {
            TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri.toString());
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

    protected long getModificationStamp() {
        return modificationStamp;
    }

    public Document getDocument() {
        return this.document;
    }

    int getVersion() {
        return version;
    }

    private void logDocument(String header, Document document) {
        LOGGER.warn(header + " text='" + document.getText());
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file != null) {
            LOGGER.warn(header + " file=" + file);
        }
    }

}

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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Synchronize IntelliJ document (open, content changed, close, save)
 * with LSP notifications (didOpen, didChanged, didClose, didSave).
 */
public class DocumentContentSynchronizer implements DocumentListener {

    private final @NotNull LanguageServerWrapper languageServerWrapper;
    private final @NotNull Document document;
    private final @NotNull String fileUri;
    private final TextDocumentSyncKind syncKind;

    private int version = 0;
    private final List<TextDocumentContentChangeEvent> changeEvents;
    final @NotNull
    CompletableFuture<Void> didOpenFuture;

    public DocumentContentSynchronizer(@NotNull LanguageServerWrapper languageServerWrapper,
                                       @NotNull URI fileUri,
                                       @NotNull Document document,
                                       TextDocumentSyncKind syncKind) {
        this.languageServerWrapper = languageServerWrapper;
        this.fileUri = fileUri.toASCIIString();
        this.syncKind = syncKind != null ? syncKind : TextDocumentSyncKind.Full;

        this.document = document;
        // add a document buffer
        TextDocumentItem textDocument = new TextDocumentItem();
        textDocument.setUri(this.fileUri);
        textDocument.setText(document.getText());

        @NotNull String languageId = getLanguageId(document, languageServerWrapper);
        textDocument.setLanguageId(languageId);
        textDocument.setVersion(++version);
        didOpenFuture = languageServerWrapper
                .getInitializedServer()
                .thenAcceptAsync(ls -> ls.getTextDocumentService()
                        .didOpen(new DidOpenTextDocumentParams(textDocument)));

        // Initialize LSP change events
        changeEvents = new ArrayList<>();
    }

    /**
     * Returns the LSP language id defined in mapping otherwise the {@link Language#getID()} otherwise the {@link FileType#getName()} otherwise 'unknown'.
     *
     * @param document       the document.
     * @param languageServer the language server.
     * @return the LSP language id.
     */
    private static @NotNull String getLanguageId(@NotNull Document document, @NotNull LanguageServerWrapper languageServer) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file == null) {
            return FileTypes.UNKNOWN.getName().toLowerCase();
        }

        Project project = languageServer.getProject();

        // 1. Try to get the LSP languageId by using language mapping
        Language language = project != null ? LSPIJUtils.getFileLanguage(file, project) : null;
        String languageId = languageServer.getLanguageId(language);
        if (languageId != null) {
            return languageId;
        }

        // 2. Try to get the LSP languageId by using the fileType mapping
        FileType fileType = file.getFileType();
        languageId = languageServer.getLanguageId(fileType);
        if (languageId != null) {
            return languageId;
        }

        // 3. Try to get the LSP languageId by using the file name pattern mapping
        languageId = languageServer.getLanguageId(file.getName());
        if (languageId != null) {
            return languageId;
        }

        // At this step there is no mapping

        // We return the language Id if it exists or file type name
        // with 'lower case' to try to map the recommended languageId specified at
        // https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocumentItem
        if (language != null) {
            // The language exists, use its ID with lower case
            return language.getID().toLowerCase();
        }
        // Returns the existing file type or 'unknown' with lower case
        return file.getName().toLowerCase();
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
            if (project != null) {
                PsiDocumentManager.getInstance(project)
                        .performForCommittedDocument(event.getDocument(), this::sendDidChangeEvents);
            }
        }
    }

    private void sendDidChangeEvents() {
        List<TextDocumentContentChangeEvent> events;
        synchronized (changeEvents) {
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

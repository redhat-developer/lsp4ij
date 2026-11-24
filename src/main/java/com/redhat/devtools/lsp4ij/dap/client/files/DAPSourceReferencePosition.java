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
package com.redhat.devtools.lsp4ij.dap.client.files;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import org.eclipse.lsp4j.debug.SourceArguments;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a deferred source position obtained from a DAP {@code sourceReference}.
 * <p>
 * The source content is fetched asynchronously from the debug adapter using the provided
 * sourceReference. Once the content is loaded, the associated editor document is updated
 * and the requested line number is returned.
 */
public class DAPSourceReferencePosition extends DeferredSourcePosition<DAPSourceReferencePosition.SourceParams, DAPFile> {

    /**
     * Encapsulates the parameters required to resolve a DAP source reference.
     *
     * @param sourceReference the DAP sourceReference identifier
     * @param line            the initial line number to navigate to after loading
     */
    public record SourceParams(@NotNull int sourceReference, int line) {}

    // The number of texts written to the document of the file when the last load left the text which the debug adapter
    // gave for the source in it, or -1 if it left none
    private volatile long loadedText = -1;

    /**
     * Creates a deferred source position based on a DAP sourceReference.
     *
     * @param file            the file associated with this position
     * @param sourceReference the DAP sourceReference to fetch content
     * @param line            the target line number in the source
     * @param client          the DAP client used to communicate with the debug adapter
     */
    public DAPSourceReferencePosition(@NotNull DAPFile file,
                                      int sourceReference,
                                      int line,
                                      @NotNull DAPClient client) {
        this(file, sourceReference, line, 0, client);
    }

    /**
     * Creates a deferred source position at a column of a line, based on a DAP sourceReference.
     *
     * @param file            the file associated with this position
     * @param sourceReference the DAP sourceReference to fetch content
     * @param line            the target line number in the source
     * @param column          the 0-based target column in the line, in characters
     * @param client          the DAP client used to communicate with the debug adapter
     */
    public DAPSourceReferencePosition(@NotNull DAPFile file,
                                      int sourceReference,
                                      int line,
                                      int column,
                                      @NotNull DAPClient client) {
        super(new SourceParams(sourceReference, line), file, column, client);
    }

    /**
     * Returns whether the document of the file has the text which the debug adapter gave for the source when the
     * position was last resolved: the load wrote it, or an earlier load of the same source did, and no other load,
     * which got another text, or none, for the same source reference, in this session or another, replaced it since.
     * Without it, as when the debug adapter gave no text, the position is resolved all the same, at the requested line,
     * in a document whose text isn't that of the source.
     *
     * @return true if the document of the file has the text of the source, and false otherwise.
     */
    public boolean isTextLoaded() {
        long text = loadedText;
        return text >= 0 && ((DAPFile) getFile()).getWrittenTexts() == text;
    }

    /**
     * Asynchronously loads the source content and resolves the line number.
     * <p>
     * If the server returns content, the editor document is updated in a write command action:
     * <ul>
     *     <li>Line endings are normalized to LF.</li>
     *     <li>The text replaces the one of the document if it differs: the file of a source reference outlives its
     *     session, and its document may have the text which an earlier session gave for the same source
     *     reference.</li>
     *     <li>The document is set to read-only.</li>
     *     <li>The requested line number is returned.</li>
     * </ul>
     * If no content is returned, the provided line number is returned as is.
     *
     * @param params the source reference parameters including sourceReference and line
     * @param file   the file to update with fetched content
     * @param client the DAP client used to fetch the content
     * @return a CompletableFuture completing with the resolved line number
     */
    @Override
    protected CompletableFuture<Integer> loadAndResolveLineAsync(@NotNull SourceParams params,
                                                                 @NotNull DAPFile file,
                                                                 @NotNull DAPClient client) {
        var args = new SourceArguments();
        args.setSourceReference(params.sourceReference);

        // Fetch source content from the debug adapter server
        return client.getDebugProtocolServer()
                .source(args)
                .thenCompose(sourceResponse -> {
                    loadedText = -1;
                    if (sourceResponse != null) {
                        String content = sourceResponse.getContent() != null ? sourceResponse.getContent() : "";

                        // CompletableFuture that completes once the document is updated
                        CompletableFuture<Integer> updatedDoc = new CompletableFuture<>();

                        // Update the editor document inside a write command action
                        WriteCommandAction.runWriteCommandAction(client.getProject(), () -> {
                            var doc = FileDocumentManager.getInstance().getDocument(getFile());
                            // Normalize line endings to LF
                            String normalized = content.replace("\r\n", "\n");
                            if (doc != null) {
                                try {
                                    // The text which another session gave for the same source reference, or none yet,
                                    // is replaced: the read-only document is made writable for the write
                                    if (!StringUtil.equals(doc.getImmutableCharSequence(), normalized)) {
                                        doc.setReadOnly(false);
                                        doc.setText(normalized);
                                        file.textWritten();
                                    }
                                } finally {
                                    doc.setReadOnly(true); // Make the document read-only
                                }
                            }
                            // The document has the text of this answer: it was just written, or an earlier load of the
                            // same source wrote it
                            boolean hasText = doc != null && StringUtil.equals(doc.getImmutableCharSequence(), normalized);
                            if (hasText) {
                                file.setLoadedSource(client, params.sourceReference());
                            }
                            // The document keeps the text as long as no other load writes another one
                            loadedText = hasText && !normalized.isEmpty() ? file.getWrittenTexts() : -1;
                            // Complete the future with the requested line number
                            updatedDoc.complete(params.line());
                        });

                        return updatedDoc;
                    }

                    // If no content returned, just return the provided line
                    return CompletableFuture.completedFuture(params.line());
                });
    }
}

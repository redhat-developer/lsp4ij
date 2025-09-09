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
        super(new SourceParams(sourceReference, line), file, client);
    }

    /**
     * Asynchronously loads the source content and resolves the line number.
     * <p>
     * If the server returns content, the editor document is updated in a write command action:
     * <ul>
     *     <li>Line endings are normalized to LF.</li>
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
                    if (sourceResponse != null) {
                        String content = sourceResponse.getContent() != null ? sourceResponse.getContent() : "";

                        // CompletableFuture that completes once the document is updated
                        CompletableFuture<Integer> updatedDoc = new CompletableFuture<>();

                        // Update the editor document inside a write command action
                        WriteCommandAction.runWriteCommandAction(client.getProject(), () -> {
                            var doc = FileDocumentManager.getInstance().getDocument(getFile());
                            if (doc != null && doc.isWritable()) {
                                // Normalize line endings to LF
                                String normalized = content.replace("\r\n", "\n");
                                doc.setText(normalized);
                                doc.setReadOnly(true); // Make the document read-only
                            }
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

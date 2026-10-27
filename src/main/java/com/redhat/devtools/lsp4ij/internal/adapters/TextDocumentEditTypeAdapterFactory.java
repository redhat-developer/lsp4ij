/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.adapters;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.eclipse.lsp4j.SnippetTextEdit;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * TypeAdapterFactory for TextDocumentEdit that handles compatibility between lsp4j versions.
 * <p>
 * In lsp4j &lt; 1.0, the edits field is {@code List<TextEdit>}.
 * In lsp4j 1.0.0+, the edits field is {@code List<Either<TextEdit, SnippetTextEdit>>}.
 * </p>
 * <p>
 * This adapter ensures correct serialization when plugins with different lsp4j versions coexist
 * (e.g., lsp4ij with lsp4j 1.0.0 and IJ Quarkus with lsp4j &lt; 1.0).
 * When IJ Quarkus creates a {@code TextDocumentEdit} with {@code List<TextEdit>} and lsp4ij tries to
 * serialize it, this adapter unwraps the TextEdit from the old format to avoid ClassCastException.
 * </p>
 */
public class TextDocumentEditTypeAdapterFactory implements TypeAdapterFactory {

    /**
     * The original TypeAdapter from lsp4j 1.0.0 that uses the @JsonAdapter annotation.
     * We delegate to it for deserialization to avoid duplicating lsp4j's logic.
     */
    private final TypeAdapter<TextDocumentEdit> originalTextDocumentEditAdapter;

    /**
     * Creates a new TextDocumentEditTypeAdapterFactory.
     *
     * @param originalTextDocumentEditAdapter the original adapter from lsp4j 1.0.0 (with @JsonAdapter)
     */
    public TextDocumentEditTypeAdapterFactory(TypeAdapter<TextDocumentEdit> originalTextDocumentEditAdapter) {
        this.originalTextDocumentEditAdapter = originalTextDocumentEditAdapter;
    }

    /**
     * Checks if the edits list is in the old format (lsp4j &lt; 1.0).
     * Old format: List&lt;TextEdit&gt;
     * New format: List&lt;Either&lt;TextEdit, SnippetTextEdit&gt;&gt;
     *
     * @param edits the list of edits to check
     * @return true if the list is in old format (contains plain TextEdit, not Either)
     */
    private static boolean isOldFormat(List<?> edits) {
        if (edits.isEmpty()) {
            return false;
        }
        // Check the first element - if it's not an Either, the whole list is old format
        Object firstEdit = edits.get(0);
        String className = firstEdit.getClass().getName();
        // Use class name check to handle different classloaders
        return !className.startsWith("org.eclipse.lsp4j.jsonrpc.messages.Either");
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != TextDocumentEdit.class) {
            return null;
        }

        return (TypeAdapter<T>) new TypeAdapter<TextDocumentEdit>() {

            @Override
            public void write(JsonWriter out, TextDocumentEdit value) throws IOException {
                if (value == null) {
                    out.nullValue();
                    return;
                }

                // Check if this is old format from lsp4j < 1.0 (List<TextEdit>)
                if (isOldFormat(value.getEdits())) {
                    // Transform List<TextEdit> to List<Either<TextEdit, SnippetTextEdit>>
                    List<Either<TextEdit, SnippetTextEdit>> transformedEdits = new ArrayList<>();
                    for (Object edit : value.getEdits()) {
                        // All elements are TextEdit in old format
                        transformedEdits.add(Either.forLeft((TextEdit) edit));
                    }
                    // Create new TextDocumentEdit with transformed edits and delegate
                    TextDocumentEdit transformed = new TextDocumentEdit(value.getTextDocument(), transformedEdits);
                    originalTextDocumentEditAdapter.write(out, transformed);
                } else {
                    // Already in new format (List<Either<...>>), delegate directly
                    originalTextDocumentEditAdapter.write(out, value);
                }
            }

            @Override
            public TextDocumentEdit read(JsonReader in) throws IOException {
                // Delegate to the original lsp4j 1.0.0 adapter for deserialization.
                // We only need custom write() logic - the ClassCastException occurs during serialization.
                return originalTextDocumentEditAdapter.read(in);
            }
        };
    }
}

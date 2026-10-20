/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.adapters;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.eclipse.lsp4j.FullDocumentDiagnosticReport;
import org.eclipse.lsp4j.UnchangedDocumentDiagnosticReport;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the {@code relatedDocuments} of a document diagnostic report. LSP4J 1.0.0 cannot tell a full
 * from an unchanged related report, since both are JSON objects, and fails to parse the response
 * (eclipse-lsp4j/lsp4j#990). Each value is identified by its {@code kind}, as LSP4J does for the report
 * itself. Remove this once LSP4IJ uses an LSP4J release with eclipse-lsp4j/lsp4j#991: a field-level
 * adapter there takes precedence over this factory.
 */
public class RelatedDocumentsTypeAdapterFactory implements TypeAdapterFactory {

    private static final TypeToken<Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>> VALUE_TYPE =
            new TypeToken<>() {
            };

    private static final TypeToken<Map<String, Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>>> MAP_TYPE =
            new TypeToken<>() {
            };

    @SuppressWarnings("unchecked")
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!MAP_TYPE.equals(type)) {
            return null;
        }
        var valueAdapter = new EitherTypeAdapter<>(gson, VALUE_TYPE,
                new EitherTypeAdapter.PropertyChecker("kind", "full"),
                new EitherTypeAdapter.PropertyChecker("kind", "unchanged"));
        return (TypeAdapter<T>) new TypeAdapter<Map<String, Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>>>() {
            @Override
            public void write(JsonWriter output,
                              Map<String, Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>> value) throws IOException {
                if (value == null) {
                    output.nullValue();
                    return;
                }
                output.beginObject();
                for (var entry : value.entrySet()) {
                    output.name(entry.getKey());
                    valueAdapter.write(output, entry.getValue());
                }
                output.endObject();
            }

            @Override
            public Map<String, Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>> read(JsonReader input) throws IOException {
                if (input.peek() == JsonToken.NULL) {
                    input.nextNull();
                    return null;
                }
                var result = new LinkedHashMap<String, Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>>();
                input.beginObject();
                while (input.hasNext()) {
                    result.put(input.nextName(), valueAdapter.read(input));
                }
                input.endObject();
                return result;
            }
        };
    }
}

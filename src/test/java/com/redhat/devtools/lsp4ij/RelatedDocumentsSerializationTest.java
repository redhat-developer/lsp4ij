/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.FullDocumentDiagnosticReport;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.UnchangedDocumentDiagnosticReport;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * LSP4J 1.0.0 cannot parse the relatedDocuments of a pull diagnostic report (eclipse-lsp4j/lsp4j#990).
 * LSP4IJ's compatibility adapters must.
 */
public class RelatedDocumentsSerializationTest {

    @Test
    public void diagnosticResponseWithRelatedDocumentsSurvivesTheWireRoundTrip() {
        var handler = new MessageJsonHandler(ServiceEndpoints.getSupportedMethods(LanguageServer.class),
                JSONUtils::configureCompatibilityAdapters);
        handler.setMethodProvider(id -> "textDocument/diagnostic");
        var relatedDocuments = new LinkedHashMap<String, Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>>();
        relatedDocuments.put("file:///full.hpp", Either.forLeft(new FullDocumentDiagnosticReport(List.of())));
        relatedDocuments.put("file:///unchanged.hpp", Either.forRight(new UnchangedDocumentDiagnosticReport("1")));
        var report = new RelatedFullDocumentDiagnosticReport(List.of());
        report.setRelatedDocuments(relatedDocuments);
        var response = new ResponseMessage();
        response.setId("1");
        response.setResult(new DocumentDiagnosticReport(report));

        var parsed = (ResponseMessage) handler.parseMessage(handler.serialize(response));

        assertEquals(response.getResult(), parsed.getResult());
    }
}

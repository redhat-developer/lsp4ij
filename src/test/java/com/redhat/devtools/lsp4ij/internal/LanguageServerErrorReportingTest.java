/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.settings.ErrorReportingKind;
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * The error that a language server answers to a request is reported as its *Error reporting* setting says. *In log*
 * writes it to the log as a warning. IntelliJ shows each record of level SEVERE in the IDE as an error, with
 * `DialogAppender`, so an error in the log would be an IDE error, which comes back each time the server answers with
 * it (#1384). LSP4IJ logs with SLF4J, which IntelliJ binds to java.util.logging.
 */
public class LanguageServerErrorReportingTest extends LSPCodeInsightFixtureTestCase {

    public LanguageServerErrorReportingTest() {
        super("*.foo");
    }

    public void testAnErrorReportedInTheLogIsAWarningAndNoIdeError() throws Exception {
        var languageServer = languageServerOf("main.foo");
        var settings = new ProjectLanguageServerSettings.LanguageServerDefinitionSettings();
        settings.setErrorReportingKind(ErrorReportingKind.in_log);
        var projectSettings = ProjectLanguageServerSettings.getInstance(myFixture.getProject());
        String id = languageServer.getServerDefinition().getId();
        projectSettings.updateSettings(id, settings);
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        var logger = Logger.getLogger(CancellationSupport.class.getName());
        logger.addHandler(handler);
        try {
            CompletableFuture<Object> answer = CompletableFuture.failedFuture(new ResponseErrorException(
                    new ResponseError(ResponseErrorCode.InternalError, "The server failed", null)));

            var response = new CancellationSupport().execute(answer, languageServer, "textDocument/hover");

            assertNull("The request must give no response, and no error", response.get(1, TimeUnit.HOURS));
            assertEquals("No record must be an IDE error", List.of(),
                    records.stream().filter(record -> record.getLevel().intValue() >= Level.SEVERE.intValue()).toList());
            assertEquals("The error must be in the log as a warning", List.of(Level.WARNING),
                    records.stream().map(LogRecord::getLevel).toList());
            assertTrue(records.get(0).getMessage(), records.get(0).getMessage().contains("textDocument/hover"));
        } finally {
            logger.removeHandler(handler);
            projectSettings.getLanguageServerSettings(id).setErrorReportingKind(null);
        }
    }

    private @NotNull LanguageServerItem languageServerOf(@NotNull String fileName) throws Exception {
        var file = myFixture.configureByText(fileName, "main");
        // The bound only ends a hang
        return LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(1, TimeUnit.HOURS).get(0);
    }
}

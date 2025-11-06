/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction;

import com.redhat.devtools.lsp4ij.fixtures.LSPCodeActionFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Test case that emulates the language server cancelling a 'textDocument/codeAction' request.
 */
public class JakartaCodeActionCancellationTest extends LSPCodeActionFixtureTestCase {

    private static final String NOT_JAVA_EXTENSION = "javax";
    private static final String TEST_FILE_NAME = "InvalidWebFilter." + NOT_JAVA_EXTENSION;

    public JakartaCodeActionCancellationTest() {
        super("*." + NOT_JAVA_EXTENSION);
    }

    /**
     * Simulates the language server cancelling a jakarta/java/codeAction request (LSP error -32800)
     * and verifies that LSP4IJ handles the cancellation gracefully.
     */
    public void testJakartaCodeActionCancelledRequest() {
        // Configures mock language server to cancel the codeAction request
        MockLanguageServer.INSTANCE.setCodeActionHandler((CodeActionParams params) -> {
            CompletableFuture<List<Either<Command, CodeAction>>> cancelledFuture = new CompletableFuture<>();
            cancelledFuture.cancel(true);
            return cancelledFuture;
        });

        // Configure the Jakarta sample file
        myFixture.configureByText(TEST_FILE_NAME,
                // language=JAVA
                """
package io.openliberty.sample.jakarta.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.annotation.WebFilter;

@WebFilter(<caret>)
public abstract class InvalidWebFilter implements Filter {

}""");

        // Attempt to retrieve quick fixes (should trigger textDocument/codeAction)
        var allQuickFixes = myFixture.getAvailableIntentions();

        // Verify that the cancellation results in no quick fixes
        assertEmpty("No quick fixes should be returned when the language server cancels the request", allQuickFixes);
    }
}

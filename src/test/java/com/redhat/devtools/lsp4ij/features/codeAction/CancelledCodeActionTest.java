/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction;

import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Test class for simulating the cancellation of a 'jakarta/java/codeAction' request.
 * Includes a test case where the language server cancels the request
 */
public final class CancelledCodeActionTest extends LSPCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockLanguageServer.reset();
    }

    public void testCodeActionCancellation() {
        Function<List<Either<Command, CodeAction>>, CompletableFuture<List<Either<Command, CodeAction>>>> futureFactory = (input) -> {
            if (input == null) {
                CompletableFuture<List<Either<Command, CodeAction>>> future = new CompletableFuture<>();
                future.cancel(true);
                return future;
            }
            return CompletableFuture.completedFuture(Collections.emptyList());
        };

        MockTextDocumentService service = new MockTextDocumentService(futureFactory);

        CompletableFuture<List<Either<Command, CodeAction>>> firstRequest = service.codeAction(new CodeActionParams());
        CompletableFuture<List<Either<Command, CodeAction>>> secondRequest = service.codeAction(new CodeActionParams());

        assertTrue("First request should be cancelled", firstRequest.isCancelled());

        try {
            List<Either<Command, CodeAction>> result = secondRequest.get(6, TimeUnit.SECONDS);
            assertNotNull("Second request should return a result", result);
        } catch (Exception e) {
            fail("Second request should not fail: " + e.getMessage());
        }
    }
}

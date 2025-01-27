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
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

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

    /**
     * Test case that simulates the cancellation of the 'jakarta/java/codeAction' request
     * and checks that no quick fix is available as a result.
     */
    public void testQuickFixCancellation() {
        MockLanguageServer.INSTANCE.getTextDocumentService().setCodeActionProvider((CodeActionParams params) -> {
            CompletableFuture<List<Either<Command, CodeAction>>> future = new CompletableFuture<>();
            future.completeExceptionally(new CancellationException("Request was canceled"));
            return future;
        });
        CompletableFuture<List<Either<Command, CodeAction>>> actions = MockLanguageServer.INSTANCE.getTextDocumentService().getCodeActions(new CodeActionParams());
        assertTrue("Code action should be cancelled", actions.isCompletedExceptionally());
    }
}

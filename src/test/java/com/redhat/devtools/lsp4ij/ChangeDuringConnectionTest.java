/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.command.WriteCommandAction;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentSyncKind;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A change made to a document while it connects to a language server, which the server must get. The document's
 * listener is added only once the server has initialized, after the text for the didOpen was captured. Waits only
 * guard against hangs.
 */
public class ChangeDuringConnectionTest extends LSPCodeInsightFixtureTestCase {

    public ChangeDuringConnectionTest() {
        super("*.foo");
    }

    public void testDidOpenSendsTheChangeMadeSinceTheCapture() throws Exception {
        // An incremental change applies to the text the server has, so a change the server misses stays missed
        MockLanguageServer.INSTANCE.getInitializeResult().getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Incremental);
        var file = myFixture.configureByText("test.foo", "before");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS).get(0).getServerWrapper();
        // Use a file without an editor. Selecting an editor schedules its own connection, which
        // can connect the file with the typed text first, so connect() would ignore this capture.
        var other = myFixture.addFileToProject("other.foo", "before").getVirtualFile();
        var document = LSPIJUtils.getDocument(other);
        // The capture of a file that waits for its language server to start, and a change made meanwhile
        var connectionInfo = wrapper.createFileConnectionInfo(other, document, true);
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> document.setText("new typing"));
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened,
                params -> params.getTextDocument().getUri().endsWith("/other.foo"));
        wrapper.connect(other, connectionInfo).get(30, TimeUnit.SECONDS);

        assertEquals("new typing", opened.get(30, TimeUnit.SECONDS).getTextDocument().getText());
    }
}

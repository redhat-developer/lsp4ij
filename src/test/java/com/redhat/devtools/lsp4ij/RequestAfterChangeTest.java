/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * A request for a document is sent after the changes made to it before the request, so that the server
 * answers it for the current text.
 */
public class RequestAfterChangeTest extends LSPCodeInsightFixtureTestCase {

    public RequestAfterChangeTest() {
        super("*.foo");
    }

    public void testARequestWaitsForTheChangeMadeBeforeIt() throws Exception {
        var file = myFixture.configureByText("main.foo", "");
        var accessor = LanguageServiceAccessor.getInstance(myFixture.getProject());
        assertHoverFollowsChange(file, () -> accessor.getLanguageServers(file, null, null));
    }

    public void testARequestFromAWriteActionWaitsForTheChangeMadeBeforeIt() throws Exception {
        var file = myFixture.configureByText("main.foo", "");
        var accessor = LanguageServiceAccessor.getInstance(myFixture.getProject());
        // as when a character is typed: the request does not wait for didOpen, but still for the change
        assertHoverFollowsChange(file, () -> WriteAction.compute(() -> accessor.getLanguageServers(file, null, null)));
    }

    /**
     * Types a character while the wrapper still dispatches an earlier notification, then requests a hover
     * for the file, and checks that the hover reaches the server after the didChange.
     */
    private void assertHoverFollowsChange(PsiFile file, Supplier<CompletableFuture<List<LanguageServerItem>>> request) throws Exception {
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS).get(0).getServerWrapper();
        var dispatcherFree = new CompletableFuture<Void>();
        wrapper.sendNotification(ls -> {
            dispatcherFree.join();
            return ls;
        });
        try {
            type("a");
            var servers = request.get();
            var hover = servers.thenCompose(items -> items.get(0).getServer().getTextDocumentService().hover(
                    new HoverParams(new TextDocumentIdentifier(LSPIJUtils.toUriAsString(file)), new Position(0, 1))));
            try {
                servers.get(1, TimeUnit.SECONDS);
                fail("the request must wait for the didChange of the text it is about");
            } catch (TimeoutException expected) {
                // the didChange is still held
            }

            dispatcherFree.complete(null);
            hover.get(30, TimeUnit.SECONDS);
        } finally {
            dispatcherFree.complete(null);
        }
        assertEquals(List.of("didChange", "hover"), received("didChange", "hover"));
    }

    private void type(String text) {
        var document = myFixture.getEditor().getDocument();
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                () -> document.insertString(document.getTextLength(), text));
    }

    private static List<String> received(String... kinds) {
        var wanted = List.of(kinds);
        return MockLanguageServer.INSTANCE.getNotifications().stream().filter(wanted::contains).toList();
    }
}

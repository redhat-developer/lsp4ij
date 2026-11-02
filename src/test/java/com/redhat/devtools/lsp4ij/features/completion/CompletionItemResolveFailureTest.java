/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionProposal;
import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A language server can fail 'completionItem/resolve'. The error is reported like any other LSP error, and the
 * completion item is used as the server gave it in 'textDocument/completion': it's inserted and rendered unresolved.
 */
public class CompletionItemResolveFailureTest extends LSPCompletionFixtureTestCase {

    // The error of typescript-language-server in #784
    private static final String MESSAGE = "Cannot read properties of undefined (reading 'file')";

    private final AtomicInteger resolveRequests = new AtomicInteger();

    public CompletionItemResolveFailureTest() {
        super("*.ts");
    }

    public void testAnItemWhoseResolveFailsIsInserted() {
        complete(CompletableFuture.failedFuture(error()));

        myFixture.selectItem(lookupElement("foo"));

        assertEquals("foo", myFixture.getEditor().getDocument().getText());
        assertEquals(3, myFixture.getEditor().getCaretModel().getOffset());
        assertEquals(1, resolveRequests.get());
    }

    public void testAFailedResolveIsReportedLikeOtherLanguageServerErrors() {
        // By default, an error of a language server is reported in a notification
        List<Notification> notifications = new CopyOnWriteArrayList<>();
        myFixture.getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                if ("completionItem/resolve".equals(notification.getSubtitle())) {
                    notifications.add(notification);
                }
            }
        });
        complete(CompletableFuture.failedFuture(error()));

        myFixture.selectItem(lookupElement("foo"));

        assertEquals(1, notifications.size());
        assertEquals(MESSAGE, notifications.get(0).getContent());
    }

    public void testTheExpensiveRendererOfAnItemWhoseResolveFailsRendersItUnresolved() throws Exception {
        // IntelliJ can render the selected item in the background as soon as the lookup shows, so the resolve
        // fails only once the test has the renderer, which is asked for while the 'detail' isn't resolved
        var resolve = new CompletableFuture<CompletionItem>();
        complete(resolve);
        var proposal = (LSPCompletionProposal) myFixture.getLookup().getCurrentItem().getObject();
        @SuppressWarnings("unchecked")
        var renderer = (LookupElementRenderer<LookupElement>) proposal.getExpensiveRenderer();
        assertNotNull(renderer);
        resolve.completeExceptionally(error());

        // IntelliJ runs the renderer off the EDT, in a read action
        var presentation = new LookupElementPresentation();
        ApplicationManager.getApplication()
                .executeOnPooledThread(() -> ReadAction.run(() -> renderer.renderElement(proposal, presentation)))
                .get(10, TimeUnit.SECONDS);

        assertEquals(proposal.getLookupString(), presentation.getItemText());
        assertEquals("the detail isn't resolved again", "", proposal.getItem().getDetail());
        assertNull("the item has no documentation", proposal.computeDocumentation());
        assertEquals(1, resolveRequests.get());
    }

    private void complete(@NotNull CompletableFuture<CompletionItem> resolve) {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setCompletionProvider(new CompletionOptions(true, null));
            return capabilities;
        });
        MockLanguageServer.INSTANCE.getTextDocumentService().setResolveCompletionItemProcessor(unresolved -> {
            resolveRequests.incrementAndGet();
            return resolve;
        });
        assertCompletion("test.ts", "f<caret>", "[{\"label\": \"foo\"}, {\"label\": \"foobar\"}]", "foo", "foobar");
    }

    private LookupElement lookupElement(@NotNull String lookupString) {
        return Stream.of(myFixture.getLookupElements())
                .filter(element -> lookupString.equals(element.getLookupString()))
                .findFirst()
                .orElseThrow();
    }

    private static ResponseErrorException error() {
        return new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError, MESSAGE, null));
    }
}

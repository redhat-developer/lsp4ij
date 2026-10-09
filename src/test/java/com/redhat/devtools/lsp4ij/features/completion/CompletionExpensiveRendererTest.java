/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionProposal;
import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.ServerCapabilities;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests that the 'detail' of the selected completion item is resolved by its expensive renderer,
 * off the EDT, and never by {@link LSPCompletionProposal#getExpensiveRenderer()} itself.
 */
public class CompletionExpensiveRendererTest extends LSPCompletionFixtureTestCase {

    public CompletionExpensiveRendererTest() {
        super("*.ts");
    }

    public void testExpensiveRendererDoesNotWaitForResolve() throws Exception {
        ServerCapabilities capabilities = MockLanguageServer.defaultServerCapabilities();
        capabilities.setCompletionProvider(new CompletionOptions(true, null));
        MockLanguageServer.reset(() -> capabilities);

        CompletionItem resolvedItem = new CompletionItem("foo");
        resolvedItem.setDetail("resolved detail");
        CompletableFuture<CompletionItem> resolve = new CompletableFuture<>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setResolveCompletionItemProcessor(unresolved -> resolve);
        // Only fires if the EDT waits for completionItem/resolve, which would otherwise never complete.
        AtomicBoolean edtWaitedForResolve = new AtomicBoolean();
        ScheduledFuture<?> watchdog = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            edtWaitedForResolve.set(true);
            resolve.complete(resolvedItem);
        }, 10, TimeUnit.SECONDS);

        assertCompletion("test.ts", "f<caret>", "[{\"label\": \"foo\"}, {\"label\": \"foobar\"}]", "foo", "foobar");
        var proposal = (LSPCompletionProposal) myFixture.getLookup().getCurrentItem().getObject();

        // IntelliJ asks for the expensive renderer of the selected item on the EDT, inside the write action
        // of a typed character, while the response may be stuck behind a message waiting for that write action.
        AtomicReference<LookupElementRenderer<? extends LookupElement>> renderer = new AtomicReference<>();
        WriteAction.run(() -> renderer.set(proposal.getExpensiveRenderer()));
        watchdog.cancel(false);
        assertFalse("The EDT waited for completionItem/resolve", edtWaitedForResolve.get());
        assertNotNull(renderer.get());

        // The renderer resolves the detail, off the EDT as IntelliJ runs it.
        resolve.complete(resolvedItem);
        @SuppressWarnings("unchecked")
        var lookupRenderer = (LookupElementRenderer<LookupElement>) renderer.get();
        ApplicationManager.getApplication()
                .executeOnPooledThread(() -> ReadAction.run(() -> lookupRenderer.renderElement(proposal, new LookupElementPresentation())))
                .get(10, TimeUnit.SECONDS);
        assertEquals("resolved detail", proposal.getItem().getDetail());
    }
}

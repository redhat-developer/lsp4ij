/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.navigation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests that Go to Declaration gives way to a write action that waits for the read lock, instead of keeping it, and
 * the UI with it, waiting for the language server: IntelliJ asks a {@code GotoDeclarationHandler} for its targets in a
 * read action that a write action doesn't cancel (#2015).
 */
public class GotoDeclarationWriteActionTest extends LSPCodeInsightFixtureTestCase {

    private static final String TEXT = "class Foo\nnew Foo()";
    // In "Foo" of "new Foo()"
    private static final int OFFSET = TEXT.lastIndexOf("Foo") + 1;
    // How long the server holds its reply when Go to Declaration doesn't give way, which the write action then waits for
    private static final long HELD_REPLY_SECONDS = 20;

    public GotoDeclarationWriteActionTest() {
        super("*.foo");
    }

    public void testGoToDeclarationGivesWayToAWriteAction() throws Exception {
        var definitionRequests = new AtomicInteger();
        var definitionRequested = new CountDownLatch(1);
        var reply = new CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>();
        MockLanguageServer.reset();
        MockLanguageServer.INSTANCE.setTextDocumentService(new MockTextDocumentService(CompletableFuture::completedFuture) {
            @Override
            public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
                definitionRequests.incrementAndGet();
                definitionRequested.countDown();
                return reply;
            }
        });
        PsiFile file = myFixture.configureByText("main.foo", TEXT);
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
        PsiElement element = file.findElementAt(OFFSET);
        var declaration = new Location(LSPIJUtils.toUriAsString(file), new Range(new Position(0, 6), new Position(0, 9)));
        // Without the fix, the write action below waits for the reply: release it in time for the test to fail, not hang
        var release = AppExecutorUtil.getAppScheduledExecutorService().schedule(
                () -> reply.complete(Either.forLeft(List.of(declaration))), HELD_REPLY_SECONDS, TimeUnit.SECONDS);
        try {
            Future<PsiElement[]> targets = ApplicationManager.getApplication().executeOnPooledThread(
                    () -> ReadAction.compute(() -> LSPGotoDeclarationHandler.getGotoDeclarationTargets(element, OFFSET)));
            assertTrue("the definition is requested", definitionRequested.await(30, TimeUnit.SECONDS));

            long start = System.nanoTime();
            WriteAction.run(() -> {
            });
            long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertTrue("the write action waited " + waitedMs + " ms for the server's reply", waitedMs < 5_000);
            assertFalse("the server's reply came before the write action ran", reply.isDone());
            assertEquals("Go to Declaration gave way without targets", 0, targets.get(30, TimeUnit.SECONDS).length);
        } finally {
            release.cancel(false);
        }

        // The request went on: once the server replies, Go to Declaration at the same offset uses its reply
        reply.complete(Either.forLeft(List.of(declaration)));
        PsiElement[] targets = ReadAction.compute(() -> LSPGotoDeclarationHandler.getGotoDeclarationTargets(element, OFFSET));
        assertSize(1, targets);
        assertEquals("the reply of the first request is used", 1, definitionRequests.get());
    }
}

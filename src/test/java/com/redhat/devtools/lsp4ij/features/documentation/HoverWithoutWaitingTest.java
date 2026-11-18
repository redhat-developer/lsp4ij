/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.platform.backend.documentation.DocumentationData;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.platform.backend.documentation.impl.ImplKt.computeDocumentationBlocking;

/**
 * Tests that the documentation targets of a hover don't keep their caller waiting for the language server: IntelliJ
 * also asks for them when it updates Quick Documentation, while the UI thread waits for the update (#2016).
 */
public class HoverWithoutWaitingTest extends LSPCodeInsightFixtureTestCase {

    private static final String DOCUMENTATION = "The documentation of the word";
    // How long the server holds its reply when the targets wait for it, which the test would then wait for
    private static final long HELD_REPLY_SECONDS = 20;

    private final AtomicInteger hoverRequests = new AtomicInteger();
    private final CountDownLatch hoverRequested = new CountDownLatch(1);
    private final CompletableFuture<Hover> reply = new CompletableFuture<>();

    public HoverWithoutWaitingTest() {
        super("*.foo", "*.properties");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockLanguageServer.reset();
        MockLanguageServer.INSTANCE.setTextDocumentService(new MockTextDocumentService(CompletableFuture::completedFuture) {
            @Override
            public CompletableFuture<Hover> hover(HoverParams params) {
                hoverRequests.incrementAndGet();
                hoverRequested.countDown();
                return reply;
            }
        });
    }

    public void testAFileWithoutDocumentationOfItsOwnGetsATargetWhichWaitsInTheBackground() throws Exception {
        PsiFile file = open("main.foo", "a word");
        assertTrue("a plain text file", LSPIJEditorUtils.isPlainTextFile(file));

        List<? extends DocumentationTarget> targets = targetsWhileTheReplyIsHeld(file);

        assertSize(1, targets);
        var target = assertInstanceOf(targets.get(0), LSPPendingHoverDocumentationTarget.class);
        DocumentationResult result = ReadAction.compute(target::computeDocumentation);
        assertNotNull("a documentation result", result);
        assertFalse("the documentation is computed in the background", result instanceof DocumentationData);
        reply.complete(hover());
        // As IntelliJ computes it, once the reply is there
        DocumentationData documentation = computeDocumentationBlocking(target.createPointer());
        assertNotNull("the documentation of the reply", documentation);
        assertTrue(documentation.getHtml(), documentation.getHtml().contains(DOCUMENTATION));
        assertEquals(1, hoverRequests.get());
    }

    public void testAReplyIsNotShownOnceTheDocumentHasChanged() throws Exception {
        PsiFile file = open("edited.foo", "a word");

        List<? extends DocumentationTarget> targets = targetsWhileTheReplyIsHeld(file);

        var target = assertInstanceOf(ContainerUtil.getOnlyItem(targets), LSPPendingHoverDocumentationTarget.class);
        reply.complete(hover());
        // The document changes after the reply, and before IntelliJ computes the documentation
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                () -> myFixture.getEditor().getDocument().insertString(0, "an edit, "));
        assertNull("no documentation of a reply to the earlier text", computeDocumentationBlocking(target.createPointer()));
    }

    public void testAFileWithDocumentationOfItsOwnWaitsBrieflyAndUsesTheReplyNextTime() throws Exception {
        PsiFile file = open("main.properties", "key=word");
        assertFalse("not a plain text file", LSPIJEditorUtils.isPlainTextFile(file));

        List<? extends DocumentationTarget> targets = targetsWhileTheReplyIsHeld(file);

        assertEmpty("no target, so that IntelliJ shows the file's own documentation", targets);
        reply.complete(hover());
        // The request went on: the next time at the same offset, its reply is used
        List<? extends DocumentationTarget> next = targets(file);
        assertSize(1, next);
        var target = assertInstanceOf(next.get(0), LSPDocumentationTarget.class);
        assertTrue(target.getHtml(), target.getHtml().contains(DOCUMENTATION));
        assertEquals(1, hoverRequests.get());
    }

    private PsiFile open(String fileName, String text) throws Exception {
        PsiFile file = myFixture.configureByText(fileName, text);
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS);
        return file;
    }

    /**
     * Asks for the documentation targets of the last word while the server holds its reply, and checks that they
     * didn't wait for it.
     */
    private List<? extends DocumentationTarget> targetsWhileTheReplyIsHeld(PsiFile file) throws Exception {
        // Without the fix, the targets wait for the reply: release it in time for the test to fail, not hang
        ScheduledFuture<?> release = AppExecutorUtil.getAppScheduledExecutorService().schedule(
                () -> reply.complete(hover()), HELD_REPLY_SECONDS, TimeUnit.SECONDS);
        try {
            long start = System.nanoTime();
            List<? extends DocumentationTarget> targets = targets(file);
            long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertTrue("the hover is requested", hoverRequested.await(30, TimeUnit.SECONDS));
            assertTrue("the targets waited " + waitedMs + " ms for the server's reply", waitedMs < 5_000);
            assertFalse("the server's reply came before the targets", reply.isDone());
            return targets;
        } finally {
            release.cancel(false);
        }
    }

    /**
     * Returns the documentation targets of the last word, asked for on a pooled thread in a read action, as IntelliJ
     * does.
     */
    private List<? extends DocumentationTarget> targets(PsiFile file) throws Exception {
        int offset = file.getTextLength() - 1;
        return ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(
                        () -> new LSPDocumentationTargetProvider().documentationTargets(file, offset)))
                .get(30, TimeUnit.SECONDS);
    }

    private static Hover hover() {
        return new Hover(new MarkupContent(MarkupKind.PLAINTEXT, DOCUMENTATION));
    }
}

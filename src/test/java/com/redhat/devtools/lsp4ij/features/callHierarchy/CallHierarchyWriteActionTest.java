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
package com.redhat.devtools.lsp4ij.features.callHierarchy;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Tests that the Call Hierarchy computes the callers, the callees and the prepared call hierarchy items of a node again
 * when a write action cancels their computation while the language server hasn't answered yet, instead of keeping the
 * node without children (#2278).
 * <p>
 * IntelliJ's hierarchy tree computes the children of a node in a read action that a write action cancels, and computes
 * them again only when the computation ends with the {@link ProcessCanceledException}: children computed normally are
 * kept by {@link HierarchyTreeStructure#getChildElements(Object)}.
 */
public class CallHierarchyWriteActionTest extends LSPCodeInsightFixtureTestCase {

    private static final Range RANGE = new Range(new Position(0, 0), new Position(0, 4));

    private final HeldRequests<List<CallHierarchyItem>> prepareRequests =
            new HeldRequests<>("textDocument/prepareCallHierarchy");
    private final HeldRequests<List<CallHierarchyIncomingCall>> incomingCallsRequests =
            new HeldRequests<>("callHierarchy/incomingCalls");
    private final HeldRequests<List<CallHierarchyOutgoingCall>> outgoingCallsRequests =
            new HeldRequests<>("callHierarchy/outgoingCalls");

    private PsiFile file;

    public CallHierarchyWriteActionTest() {
        super("*.foo");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setCallHierarchyProvider(true);
            return capabilities;
        });
        MockLanguageServer.INSTANCE.setTextDocumentService(new MockTextDocumentService(CompletableFuture::completedFuture) {
            @Override
            public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
                return prepareRequests.request();
            }

            @Override
            public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(CallHierarchyIncomingCallsParams params) {
                return incomingCallsRequests.request();
            }

            @Override
            public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(CallHierarchyOutgoingCallsParams params) {
                return outgoingCallsRequests.request();
            }
        });
        file = myFixture.configureByText("main.foo", "main");
        awaitLanguageServers(file);
    }

    public void testCallersAreComputedAgainWhenAWriteActionCancelsTheirComputation() throws Exception {
        String callerUri = uriOf(myFixture.addFileToProject("caller.foo", "caller"));
        var structure = ReadAction.compute(() -> new LSPCallHierarchyIncomingCallsTreeStructure(myFixture.getProject(), method()));
        HierarchyNodeDescriptor main = preparedItem(structure);

        assertChildrenComputedAgainAfterAWriteAction(structure, main, incomingCallsRequests,
                List.of(new CallHierarchyIncomingCall(item("caller", callerUri), List.of(RANGE))), List.of("caller"));
    }

    public void testCalleesAreComputedAgainWhenAWriteActionCancelsTheirComputation() throws Exception {
        String calleeUri = uriOf(myFixture.addFileToProject("callee.foo", "callee"));
        var structure = ReadAction.compute(() -> new LSPCallHierarchyOutgoingCallsTreeStructure(myFixture.getProject(), method()));
        HierarchyNodeDescriptor main = preparedItem(structure);

        assertChildrenComputedAgainAfterAWriteAction(structure, main, outgoingCallsRequests,
                List.of(new CallHierarchyOutgoingCall(item("callee", calleeUri), List.of(RANGE))), List.of("callee"));
    }

    public void testPreparedItemsAreComputedAgainWhenAWriteActionCancelsTheirComputation() throws Exception {
        var structure = ReadAction.compute(() -> new LSPCallHierarchyIncomingCallsTreeStructure(myFixture.getProject(), method()));

        assertChildrenComputedAgainAfterAWriteAction(structure, structure.getBaseDescriptor(), prepareRequests,
                List.of(item("main", uriOf(file))), List.of("main"));
    }

    /**
     * Computes the children of the given node once, as IntelliJ's hierarchy tree does, while the language server holds
     * its answer to the given requests, and runs a write action while the request is pending. Checks that the
     * computation ends with a {@link ProcessCanceledException}, that the node keeps no children, that the pending
     * request is cancelled, and that the children computed again, once the language server answers, are the given ones.
     *
     * @param structure        the call hierarchy tree structure of the node.
     * @param node             the node whose children are computed.
     * @param requests         the requests of the children, whose answer the language server holds.
     * @param answer           the answer of the language server to the requests.
     * @param expectedChildren the names of the children computed from the answer.
     * @param <T>              the answer of the requests.
     */
    private static <T> void assertChildrenComputedAgainAfterAWriteAction(@NotNull HierarchyTreeStructure structure,
                                                                         @NotNull HierarchyNodeDescriptor node,
                                                                         @NotNull HeldRequests<T> requests,
                                                                         @NotNull T answer,
                                                                         @NotNull List<String> expectedChildren) throws Exception {
        CompletableFuture<Outcome> computation = computeChildrenOnce(structure, node);
        try {
            CompletableFuture.anyOf(requests.requested, computation).get(IN_MEMORY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail("the computation of the children must send " + requests.method + " within "
                    + IN_MEMORY_TIMEOUT_SECONDS + " s");
        }
        assertTrue("the computation of the children must send " + requests.method + " before it ends, but it ended "
                + computation.getNow(null), requests.requested.isDone());

        // IntelliJ cancels the read action of the computation, and the write action waits until it ends. Should the
        // computation not end when it is cancelled, but wait for the language server's answer, the write action would
        // wait for it forever: a watchdog then answers the held request after the bound, so that the computation and
        // the write action end, and the test fails instead of hanging
        var watchdogAnswered = new AtomicBoolean();
        var watchdog = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            if (requests.answer.complete(answer)) {
                watchdogAnswered.set(true);
            }
        }, IN_MEMORY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        try {
            WriteAction.run(() -> {
            });
        } finally {
            watchdog.cancel(false);
        }
        assertFalse("the computation of the children must end when a write action cancels it, not wait for the "
                + "answer of " + requests.method, watchdogAnswered.get());

        Outcome outcome = await(computation, "the computation of the children must end once a write action cancels it");
        assertTrue("the computation of the children must end with a ProcessCanceledException, for IntelliJ to "
                + "compute them again, but it ended " + outcome, outcome.thrown() instanceof ProcessCanceledException);
        assertNull("the node must keep no children, which IntelliJ would show instead of computing them again",
                node.getCachedChildren());
        var pending = requests.received.get(0);
        PlatformTestUtil.waitWithEventsDispatching("the language server must get $/cancelRequest for the pending "
                + requests.method, pending::isCancelled, IN_MEMORY_TIMEOUT_SECONDS);

        requests.answer.complete(answer);
        assertEquals("once the language server answers, the children computed again must be its answer",
                expectedChildren, namesOf(computeChildren(structure, node)));
    }

    /**
     * Returns the node of the call hierarchy item that the language server prepares for the method, which it answers
     * at once.
     */
    private @NotNull HierarchyNodeDescriptor preparedItem(@NotNull HierarchyTreeStructure structure) throws Exception {
        prepareRequests.answer.complete(List.of(item("main", uriOf(file))));
        Object[] prepared = computeChildren(structure, structure.getBaseDescriptor());
        assertEquals("the call hierarchy item of the method must be prepared", List.of("main"), namesOf(prepared));
        return (HierarchyNodeDescriptor) prepared[0];
    }

    /**
     * Computes the children of the given node once, as IntelliJ's hierarchy tree does: on a pooled thread, in a read
     * action that a write action cancels.
     *
     * @return how the computation ended.
     */
    private static @NotNull CompletableFuture<Outcome> computeChildrenOnce(@NotNull HierarchyTreeStructure structure,
                                                                           @NotNull HierarchyNodeDescriptor node) {
        var computation = new CompletableFuture<Outcome>();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            boolean ran = ProgressManager.getInstance().runInReadActionWithWriteActionPriority(() -> {
                try {
                    computation.complete(new Outcome(structure.getChildElements(node), null));
                } catch (Throwable e) {
                    computation.complete(new Outcome(null, e));
                    throw e;
                }
            }, new EmptyProgressIndicator());
            if (!ran) {
                // Cancelled: the computation ended above with what it threw, or a pending write action kept the read
                // action from starting, which this records
                computation.complete(new Outcome(null, null));
            }
        });
        return computation;
    }

    /**
     * Computes the children of the given node as IntelliJ's hierarchy tree does, computing them again while a write
     * action cancels their computation.
     *
     * @return the children.
     */
    private static Object @NotNull [] computeChildren(@NotNull HierarchyTreeStructure structure,
                                                      @NotNull HierarchyNodeDescriptor node) throws Exception {
        var children = ReadAction.nonBlocking(() -> structure.getChildElements(node))
                .submit(AppExecutorUtil.getAppExecutorService());
        PlatformTestUtil.waitWithEventsDispatching("the children must be computed", children::isDone,
                IN_MEMORY_TIMEOUT_SECONDS);
        return children.get();
    }

    private static @NotNull List<String> namesOf(Object @NotNull [] children) {
        return Arrays.stream(children)
                .map(child -> ((LSPCallHierarchyNodeDescriptor) child).getHierarchyItem().getName())
                .toList();
    }

    private static <T> @NotNull T await(@NotNull CompletableFuture<T> future, @NotNull String message) throws Exception {
        try {
            return future.get(IN_MEMORY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError(message + " within " + IN_MEMORY_TIMEOUT_SECONDS + " s", e);
        }
    }

    private @NotNull LSPPsiElement method() {
        return new LSPPsiElement(file, new TextRange(0, "main".length()));
    }

    private static @NotNull CallHierarchyItem item(@NotNull String name, @NotNull String uri) {
        return new CallHierarchyItem(name, SymbolKind.Function, uri, RANGE, RANGE);
    }

    private static @NotNull String uriOf(@NotNull PsiFile file) {
        return LSPIJUtils.toUriAsString(file.getVirtualFile());
    }

    /**
     * How a computation of the children of a node ended: with the children, with what it threw, or with neither when
     * its read action didn't start.
     */
    private record Outcome(Object @Nullable [] children, @Nullable Throwable thrown) {

        @Override
        public String toString() {
            if (children != null) {
                return "normally with " + children.length + " children";
            }
            return thrown != null ? "with " + thrown : "without starting, as a write action was pending";
        }
    }

    /**
     * The requests of an LSP method that the language server gets, whose answer it holds until the test completes it.
     *
     * @param <T> the answer of the requests.
     */
    private static final class HeldRequests<T> {

        private final String method;
        // Completed by the test with the answer of the held and later requests
        private final CompletableFuture<T> answer = new CompletableFuture<>();
        // The futures with which the language server answers the requests it got, which LSP4J cancels on $/cancelRequest
        private final List<CompletableFuture<T>> received = new CopyOnWriteArrayList<>();
        // Completed when the language server gets the first request
        private final CompletableFuture<Void> requested = new CompletableFuture<>();

        private HeldRequests(@NotNull String method) {
            this.method = method;
        }

        private @NotNull CompletableFuture<T> request() {
            // A future of its own, which a cancel of the request doesn't propagate to the answer of the others
            CompletableFuture<T> request = answer.thenApply(Function.identity());
            received.add(request);
            requested.complete(null);
            return request;
        }
    }
}

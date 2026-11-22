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
package com.redhat.devtools.lsp4ij.features;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import com.redhat.devtools.lsp4ij.usages.LSPUsageType;
import com.redhat.devtools.lsp4ij.usages.LSPUsagesManager;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Tests that LSP Go to Implementation says that it found nothing only for a request that the language server answered,
 * in the editor of the request, and never in a modal dialog: a cancelled or failed request, or a closed editor, shows
 * nothing (#2093).
 */
public class GoToActionReportTest extends LSPCodeInsightFixtureTestCase {

    private static final String TEXT = "class Foo\nnew F<caret>oo()";
    private static final String NOT_FOUND = "No Implementation found.";
    // How long the server holds a reply that the test cancels, so that the test fails instead of hanging if it isn't
    private static final long HELD_REPLY_SECONDS = 20;

    private final List<String> dialogs = new CopyOnWriteArrayList<>();
    private final AtomicInteger implementationRequests = new AtomicInteger();
    private TestDialog previousDialog;
    private EditorHintFixture hints;
    private volatile PsiFile file;
    private volatile String fileUri;

    public GoToActionReportTest() {
        super("*.foo");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        hints = new EditorHintFixture(getTestRootDisposable());
        previousDialog = TestDialogManager.setTestDialog(message -> {
            dialogs.add(message);
            return Messages.OK;
        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            TestDialogManager.setTestDialog(previousDialog);
        } finally {
            super.tearDown();
        }
    }

    public void testARequestAnsweredWithoutImplementationsShowsAHint() throws Exception {
        open(params -> CompletableFuture.completedFuture(Either.forLeft(List.of())));

        goToImplementation();

        assertEquals(List.of(), dialogs);
        String hint = hints.getCurrentHintText();
        assertNotNull("a hint is shown", hint);
        assertTrue(hint, hint.contains(NOT_FOUND));
    }

    public void testARequestCancelledAsWhenItsFileClosesShowsNothing() throws Exception {
        var reply = new CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>();
        open(params -> {
            // When the file closes, its LSP support cancels its requests
            AppExecutorUtil.getAppExecutorService()
                    .execute(() -> LSPFileSupport.getSupport(file).getImplementationSupport().cancel());
            return reply;
        });

        goToImplementationHolding(reply);

        assertNothingShown();
    }

    public void testARequestWhoseProgressIsCancelledShowsNothing() throws Exception {
        var progresses = new CopyOnWriteArrayList<ProgressWindow>();
        var reply = new CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>();
        open(params -> {
            // As the Cancel of the progress "Find implementation(s) of ..." does
            progresses.forEach(ProgressWindow::cancel);
            return reply;
        });
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
                .subscribe(ProgressWindow.TOPIC, progresses::add);

        goToImplementationHolding(reply);

        assertFalse("the request runs in a progress", progresses.isEmpty());
        assertNothingShown();
    }

    public void testAFailedRequestShowsNothing() throws Exception {
        open(params -> {
            // A link without a range, which LSP4IJ fails to read
            var link = new LocationLink();
            link.setTargetUri(fileUri);
            return CompletableFuture.completedFuture(Either.forRight(List.of(link)));
        });

        goToImplementation();

        assertNothingShown();
    }

    public void testNothingFoundWithoutAnEditorOpensNoDialog() {
        Project project = myFixture.getProject();

        showNoImplementation(SimpleDataContext.getProjectContext(project));

        assertNothingShown();
    }

    public void testNothingFoundInAClosedEditorShowsNothing() {
        Project project = myFixture.getProject();
        EditorFactory editorFactory = EditorFactory.getInstance();
        Editor editor = editorFactory.createEditor(editorFactory.createDocument("class Foo"), project);
        editorFactory.releaseEditor(editor);
        DataContext dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.EDITOR, editor)
                .build();

        showNoImplementation(dataContext);

        assertNothingShown();
    }

    private void open(@NotNull Function<ImplementationParams,
            CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>> implementation) throws Exception {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setImplementationProvider(true);
            return capabilities;
        });
        MockLanguageServer.INSTANCE.setTextDocumentService(new MockTextDocumentService(CompletableFuture::completedFuture) {
            @Override
            public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(ImplementationParams params) {
                implementationRequests.incrementAndGet();
                return implementation.apply(params);
            }
        });
        file = myFixture.configureByText("main.foo", TEXT);
        fileUri = LSPIJUtils.toUriAsString(file);
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
    }

    private void goToImplementation() {
        myFixture.performEditorAction("LSP.GotoImplementation");
        // The action shows what it found in a later event
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertEquals("textDocument/implementation is requested", 1, implementationRequests.get());
    }

    private void goToImplementationHolding(@NotNull CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> reply) {
        // If the request isn't cancelled, answer it in time for the test to fail, not hang
        var release = AppExecutorUtil.getAppScheduledExecutorService().schedule(
                () -> reply.complete(Either.forLeft(List.of())), HELD_REPLY_SECONDS, TimeUnit.SECONDS);
        try {
            goToImplementation();
        } finally {
            release.cancel(false);
        }
    }

    private void showNoImplementation(@NotNull DataContext dataContext) {
        LSPUsagesManager.getInstance(myFixture.getProject())
                .findShowUsagesInPopup(List.of(), LSPUsageType.Implementations, dataContext, null);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    private void assertNothingShown() {
        assertEquals("dialogs", List.of(), dialogs);
        assertNull("the hint", hints.getCurrentHintText());
    }
}

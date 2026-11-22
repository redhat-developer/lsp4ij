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

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.declaration.LSPDeclarationParams;
import com.redhat.devtools.lsp4ij.features.implementation.LSPImplementationParams;
import com.redhat.devtools.lsp4ij.features.references.LSPReferenceParams;
import com.redhat.devtools.lsp4ij.features.typeDefinition.LSPTypeDefinitionParams;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.usages.LSPUsageType;
import com.redhat.devtools.lsp4ij.usages.LSPUsagesManager;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.eclipse.lsp4j.DeclarationParams;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Tests that LSP Go to Implementation says that it found nothing only for a request that the language server answered,
 * in the editor of the request, and never in a modal dialog: a cancelled or failed request, or a closed editor, shows
 * nothing (#2093). A language server which answers with an error is still reported as before. Of several language
 * servers, those which answered decide what is shown: nothing is shown only when none of them answered. The public
 * methods of the supports of Go to Implementation, Declaration, Type Definition and References still give an empty
 * list when no language server answered.
 */
public class GoToActionReportTest extends LSPCodeInsightFixtureTestCase {

    private static final String TEXT = "class Foo\nnew F<caret>oo()";
    private static final String NOT_FOUND = "No Implementation found.";
    private static final String ERROR = "Cannot compute the implementations";
    // The class Foo, which the implementation of the test is
    private static final Range FOO = new Range(new Position(0, 6), new Position(0, 9));
    private static final int FOO_OFFSET = 6;
    // How long the server holds a reply that the test cancels, so that the test fails instead of hanging if it isn't
    private static final long HELD_REPLY_SECONDS = 20;

    private final List<String> dialogs = new CopyOnWriteArrayList<>();
    // The errors of textDocument/implementation that LSP4IJ reports, by default in a notification
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final AtomicInteger implementationRequests = new AtomicInteger();
    // The requests of textDocument/implementation, declaration, typeDefinition and references of answeringEveryRequest
    private final AtomicInteger locationRequests = new AtomicInteger();
    private TestDialog previousDialog;
    private EditorHintFixture hints;
    // The hints shown in an editor, also the ones hidden since
    private final List<String> shownHints = new CopyOnWriteArrayList<>();
    private OtherServerDefinition otherServer;
    private volatile int languageServers;
    private volatile PsiFile file;
    private volatile String fileUri;

    public GoToActionReportTest() {
        super("*.foo");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        hints = new EditorHintFixture(getTestRootDisposable());
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
            @Override
            public void hintShown(@NotNull Editor editor, @NotNull LightweightHint hint, int flags, @NotNull HintHint hintInfo) {
                shownHints.add(String.valueOf(hint.getComponent()));
            }
        });
        previousDialog = TestDialogManager.setTestDialog(message -> {
            dialogs.add(message);
            return Messages.OK;
        });
        myFixture.getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                if (LSPRequestConstants.TEXT_DOCUMENT_IMPLEMENTATION.equals(notification.getSubtitle())) {
                    errors.add(notification.getContent());
                }
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            TestDialogManager.setTestDialog(previousDialog);
            if (otherServer != null) {
                otherServer.getServer().waitBeforeTearDown();
                LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), otherServer);
            }
        } finally {
            super.tearDown();
        }
    }

    public void testARequestAnsweredWithoutImplementationsShowsAHint() throws Exception {
        open(params -> CompletableFuture.completedFuture(Either.forLeft(List.of())));

        goToImplementation();

        assertHint(NOT_FOUND);
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

    public void testARequestAnsweredWithAnErrorShowsOnlyTheError() throws Exception {
        open(params -> CompletableFuture.failedFuture(error(ResponseErrorCode.InternalError, ERROR)));

        goToImplementation();

        assertNothingShown();
        assertEquals("the error is reported", List.of(ERROR), errors);
    }

    public void testARequestThatTheLanguageServerCancelsShowsNothing() throws Exception {
        open(params -> CompletableFuture.failedFuture(error(ResponseErrorCode.ContentModified, "Content modified")));

        goToImplementation();

        assertNothingShown();
        assertEquals("a cancellation isn't reported", List.of(), errors);
    }

    public void testARequestAnsweredWithAnErrorIsSentAgainByTheNextGoTo() throws Exception {
        open(params -> implementationRequests.get() == 1
                ? CompletableFuture.failedFuture(error(ResponseErrorCode.InternalError, ERROR))
                : CompletableFuture.completedFuture(Either.forLeft(List.of())));
        goToImplementation();
        assertNothingShown();

        goToImplementation();

        assertHint(NOT_FOUND);
    }

    public void testTheImplementationsOfALanguageServerAreShownWhenAnotherOneFails() throws Exception {
        addOtherServer(params -> CompletableFuture.completedFuture(Either.forLeft(List.of(new Location(fileUri, FOO)))));
        open(params -> CompletableFuture.failedFuture(error(ResponseErrorCode.InternalError, ERROR)));

        goToImplementation();

        assertNothingShown();
        var editor = FileEditorManager.getInstance(myFixture.getProject()).getSelectedTextEditor();
        assertNotNull(editor);
        assertEquals("the caret is at the implementation", FOO_OFFSET, editor.getCaretModel().getOffset());
        assertEquals("the error is reported", List.of(ERROR), errors);
    }

    public void testNothingFoundIsShownWhenALanguageServerAnswersSoAndAnotherOneFails() throws Exception {
        addOtherServer(params -> CompletableFuture.completedFuture(Either.forLeft(List.of())));
        open(params -> CompletableFuture.failedFuture(error(ResponseErrorCode.InternalError, ERROR)));

        goToImplementation();

        assertHint(NOT_FOUND);
        assertEquals("the error is reported", List.of(ERROR), errors);
    }

    public void testTheGoToActionUsesTheRequestOfTheSupport() throws Exception {
        open(params -> CompletableFuture.completedFuture(Either.forLeft(List.of())));
        Document document = myFixture.getEditor().getDocument();
        int offset = TargetElementUtil.adjustOffset(file, document, myFixture.getCaretOffset());
        var params = new LSPImplementationParams(new TextDocumentIdentifier(), LSPIJUtils.toPosition(offset, document), offset);
        assertEquals(List.of(), LSPFileSupport.getSupport(file).getImplementationSupport()
                .getImplementations(params)
                .get(30, TimeUnit.SECONDS));

        myFixture.performEditorAction("LSP.GotoImplementation");
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals("textDocument/implementation isn't requested again", 1, implementationRequests.get());
        assertHint(NOT_FOUND);
    }

    public void testTheSupportsStillGiveAnEmptyListWhenEveryLanguageServerFails() throws Exception {
        open(answeringEveryRequest(true));

        assertEquals(List.of(List.of(), List.of(), List.of(), List.of()), locationsOfEverySupport());
        int requests = locationRequests.get();
        assertEquals("the empty lists are kept", List.of(List.of(), List.of(), List.of(), List.of()), locationsOfEverySupport());
        assertEquals("the requests aren't sent again", requests, locationRequests.get());
    }

    public void testTheSupportsGiveTheLocationsOfALanguageServerWhenAnotherOneFails() throws Exception {
        addOtherServer(answeringEveryRequest(false));
        open(answeringEveryRequest(true));

        var foo = List.of(new Location(fileUri, FOO));
        assertEquals(List.of(foo, foo, foo, foo), locationsOfEverySupport());
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
        open(answering(implementation));
    }

    private void open(@NotNull MockTextDocumentService textDocumentService) throws Exception {
        MockLanguageServer.reset(GoToActionReportTest::capabilities);
        MockLanguageServer.INSTANCE.setTextDocumentService(textDocumentService);
        file = myFixture.configureByText("main.foo", TEXT);
        fileUri = LSPIJUtils.toUriAsString(file);
        languageServers = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS)
                .size();
        // Let the new editor take its size, as an editor has before the user can act in it. On 2025.3 and 2026.3, its
        // first change of visible area comes in a later event, and would hide a hint that the action showed before it:
        // HintManager.showErrorHint hides the hint on scrolling (HIDE_BY_SCROLLING)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    /**
     * Adds a second language server for the file of the test, which answers textDocument/implementation as given.
     */
    private void addOtherServer(@NotNull Function<ImplementationParams,
            CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>> implementation) {
        addOtherServer(answering(implementation));
    }

    private void addOtherServer(@NotNull MockTextDocumentService textDocumentService) {
        otherServer = new OtherServerDefinition(textDocumentService);
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), otherServer,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.foo"), null)));
    }

    private @NotNull MockTextDocumentService answering(@NotNull Function<ImplementationParams,
            CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>> implementation) {
        return new MockTextDocumentService(CompletableFuture::completedFuture) {
            @Override
            public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(ImplementationParams params) {
                implementationRequests.incrementAndGet();
                return implementation.apply(params);
            }
        };
    }

    /**
     * Answers textDocument/implementation, declaration, typeDefinition and references with the class Foo, or, if
     * {@code fails}, with an error.
     */
    private @NotNull MockTextDocumentService answeringEveryRequest(boolean fails) {
        return new MockTextDocumentService(CompletableFuture::completedFuture) {
            @Override
            public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(ImplementationParams params) {
                return reply(fails, () -> Either.forLeft(foo()));
            }

            @Override
            public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> declaration(DeclarationParams params) {
                return reply(fails, () -> Either.forLeft(foo()));
            }

            @Override
            public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TypeDefinitionParams params) {
                return reply(fails, () -> Either.forLeft(foo()));
            }

            @Override
            public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
                return reply(fails, () -> foo());
            }
        };
    }

    private <T> @NotNull CompletableFuture<T> reply(boolean fails, @NotNull Supplier<T> answer) {
        locationRequests.incrementAndGet();
        return fails
                ? CompletableFuture.failedFuture(error(ResponseErrorCode.InternalError, ERROR))
                : CompletableFuture.completedFuture(answer.get());
    }

    private @NotNull List<Location> foo() {
        return List.of(new Location(fileUri, FOO));
    }

    /**
     * Returns the locations that the public methods of the supports of Go to Implementation, Declaration, Type
     * Definition and References give for the caret, in this order.
     */
    private @NotNull List<List<Location>> locationsOfEverySupport() throws Exception {
        var support = LSPFileSupport.getSupport(file);
        Document document = myFixture.getEditor().getDocument();
        int offset = myFixture.getCaretOffset();
        Position position = LSPIJUtils.toPosition(offset, document);
        List<CompletableFuture<List<LocationData>>> futures = List.of(
                support.getImplementationSupport()
                        .getImplementations(new LSPImplementationParams(new TextDocumentIdentifier(), position, offset)),
                support.getDeclarationSupport()
                        .getDeclarations(new LSPDeclarationParams(new TextDocumentIdentifier(), position, offset)),
                support.getTypeDefinitionSupport()
                        .getTypeDefinitions(new LSPTypeDefinitionParams(new TextDocumentIdentifier(), position, offset)),
                support.getReferenceSupport()
                        .getReferences(new LSPReferenceParams(new TextDocumentIdentifier(), position, offset)));
        List<List<Location>> locations = new ArrayList<>(futures.size());
        for (var future : futures) {
            locations.add(future.get(30, TimeUnit.SECONDS).stream().map(LocationData::location).toList());
        }
        return locations;
    }

    private static @NotNull ServerCapabilities capabilities() {
        var capabilities = MockLanguageServer.defaultServerCapabilities();
        capabilities.setImplementationProvider(true);
        capabilities.setDeclarationProvider(true);
        return capabilities;
    }

    private static @NotNull ResponseErrorException error(@NotNull ResponseErrorCode code, @NotNull String message) {
        return new ResponseErrorException(new ResponseError(code, message, null));
    }

    private void goToImplementation() {
        int requests = implementationRequests.get();
        myFixture.performEditorAction("LSP.GotoImplementation");
        // The action shows what it found in a later event
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertEquals("textDocument/implementation is requested of each language server",
                requests + languageServers, implementationRequests.get());
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

    private void assertHint(@NotNull String expected) {
        assertEquals("dialogs", List.of(), dialogs);
        String hint = hints.getCurrentHintText();
        assertNotNull("a hint is shown", hint);
        assertTrue(hint, hint.contains(expected));
    }

    private void assertNothingShown() {
        assertEquals("dialogs", List.of(), dialogs);
        // No hint has been shown at all, so none can have been hidden before this check
        assertEquals("hints", List.of(), shownHints);
    }

    /**
     * A language server, other than the mock language server of the test case, with the given text document service.
     */
    private static final class OtherServerDefinition extends MockLanguageServerDefinition {

        private final @NotNull MockLanguageServer server;

        OtherServerDefinition(@NotNull MockTextDocumentService textDocumentService) {
            super("mock-server-other", true);
            server = new MockLanguageServer(GoToActionReportTest::capabilities);
            server.setTextDocumentService(textDocumentService);
        }

        @Override
        public @NotNull String getDisplayName() {
            return "other";
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            return new MockConnectionProvider(server);
        }

        @Override
        public @NotNull MockLanguageServer getServer() {
            return server;
        }
    }
}

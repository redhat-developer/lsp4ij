/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.ServerFileNamePatternMapping;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A language server definition that matches a file only through a mapping that is matched asynchronously. A matcher
 * that fails, asynchronously or synchronously, matches nothing, and takes away no other language server of the file.
 */
public class AsynchronousMatcherTest extends LSPCodeInsightFixtureTestCase {

    private static final String FAILING_DEFINITION_NAME = "Server of a failing matcher";

    private LanguageServerDefinition definition;

    public AsynchronousMatcherTest() {
        super("*.foo", "*.both");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        definition = LanguageServersRegistry.getInstance().getServerDefinition("mock-server-id");
        registerAsynchronousMapping(definition, "*.async", file -> CompletableFuture.completedFuture(true));
    }

    private static void registerAsynchronousMapping(@NotNull LanguageServerDefinition definition,
                                                    @NotNull String fileNamePattern,
                                                    @NotNull Function<VirtualFile, CompletableFuture<Boolean>> matches) {
        LanguageServersRegistry.getInstance().registerAssociation(definition,
                new ServerFileNamePatternMapping(List.of(fileNamePattern), definition.getId(), null, new DocumentMatcher() {
                    @Override
                    public boolean match(@NotNull VirtualFile file, @NotNull Project project) {
                        throw new AssertionError("The mapping is matched asynchronously");
                    }

                    @Override
                    public boolean shouldBeMatchedAsynchronously(@NotNull Project project) {
                        return true;
                    }

                    @Override
                    public @NotNull CompletableFuture<Boolean> matchAsync(@NotNull VirtualFile file, @NotNull Project project) {
                        return matches.apply(file);
                    }
                }));
    }

    /**
     * Registers another definition, with a language server of its own, which matches test.both asynchronously, and
     * which the fixture's definition matches synchronously.
     */
    private LanguageServerDefinition registerOtherDefinition() {
        var other = new MockLanguageServerDefinition("other-mock-server-id", false);
        var project = myFixture.getProject();
        LanguageServersRegistry.getInstance().addServerDefinition(project, other, List.of());
        Disposer.register(getTestRootDisposable(),
                () -> LanguageServersRegistry.getInstance().removeServerDefinition(project, other));
        registerAsynchronousMapping(other, "*.both", file -> CompletableFuture.completedFuture(file.getName().equals("test.both")));
        return other;
    }

    private static void registerSynchronousMapping(@NotNull LanguageServerDefinition definition,
                                                   @NotNull String fileNamePattern,
                                                   @NotNull DocumentMatcher matcher) {
        LanguageServersRegistry.getInstance().registerAssociation(definition,
                new ServerFileNamePatternMapping(List.of(fileNamePattern), definition.getId(), null, matcher));
    }

    /**
     * Registers a definition, with a language server of its own, whose matcher answers the files of the given pattern
     * asynchronously with the given function, which fails, throws or is cancelled.
     */
    private LanguageServerDefinition registerFailingDefinition(@NotNull String fileNamePattern,
                                                               @NotNull Function<VirtualFile, CompletableFuture<Boolean>> matches) {
        var failing = addFailingDefinition();
        registerAsynchronousMapping(failing, fileNamePattern, matches);
        return failing;
    }

    /**
     * Registers a definition, with a language server of its own, whose given matcher, which throws, matches the files
     * of the given pattern synchronously.
     */
    private LanguageServerDefinition registerThrowingDefinition(@NotNull String fileNamePattern,
                                                                @NotNull DocumentMatcher matcher) {
        var throwing = addFailingDefinition();
        registerSynchronousMapping(throwing, fileNamePattern, matcher);
        return throwing;
    }

    private LanguageServerDefinition addFailingDefinition() {
        var failing = new MockLanguageServerDefinition("failing-mock-server-id", false) {
            @Override
            public String getDisplayName() {
                return FAILING_DEFINITION_NAME;
            }
        };
        var project = myFixture.getProject();
        LanguageServersRegistry.getInstance().addServerDefinition(project, failing, List.of());
        Disposer.register(getTestRootDisposable(),
                () -> LanguageServersRegistry.getInstance().removeServerDefinition(project, failing));
        return failing;
    }

    public void testARequestForTheDefinitionStartsItsLanguageServer() throws Exception {
        // Not opened in an editor, which would connect it
        PsiFile file = myFixture.addFileToProject("test.async", "content");
        var accessor = LanguageServiceAccessor.getInstance(myFixture.getProject());
        assertFalse(accessor.getStartedServers().stream()
                .anyMatch(server -> server.getServerDefinition() == definition && server.isActive()));

        assertSize(1, accessor.getLanguageServers(file, null, null, definition).get(10, TimeUnit.SECONDS));
        // As the request without a definition
        assertSize(1, accessor.getLanguageServers(file, null, null).get(10, TimeUnit.SECONDS));
    }

    public void testARequestForTheDefinitionWaitsForAMatcherThatAnswersLater() throws Exception {
        var answer = new CompletableFuture<Boolean>();
        registerAsynchronousMapping(definition, "*.later", file -> answer);
        PsiFile file = myFixture.configureByText("test.later", "content");

        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null, definition);
        assertFalse("The request must wait for the matcher", servers.isDone());
        answer.complete(true);

        assertSize(1, servers.get(10, TimeUnit.SECONDS));
    }

    public void testARequestForTheDefinitionGetsNoOtherDefinitionThatMatchesTheFileAsynchronously() throws Exception {
        var other = registerOtherDefinition();
        PsiFile file = myFixture.configureByText("test.both", "content");
        var accessor = LanguageServiceAccessor.getInstance(myFixture.getProject());
        assertSize(2, accessor.getLanguageServers(file, null, null).get(10, TimeUnit.SECONDS));

        var servers = accessor.getLanguageServers(file, null, null, definition).get(10, TimeUnit.SECONDS);

        assertEquals(List.of(definition), servers.stream().map(server -> server.getServerWrapper().getServerDefinition()).toList());
        assertFalse(servers.stream().anyMatch(server -> server.getServerWrapper().getServerDefinition() == other));
    }

    public void testARequestForADefinitionWhoseAsynchronousMatcherRejectsTheFileGetsNone() throws Exception {
        var other = registerOtherDefinition();
        PsiFile file = myFixture.configureByText("other.both", "content");

        assertEmpty(LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null, other)
                .get(10, TimeUnit.SECONDS));
    }

    public void testAMatcherThatFailsKeepsTheLanguageServerThatMatchesTheFileSynchronously() throws Exception {
        registerFailingDefinition("*.both", file -> CompletableFuture.failedFuture(new IllegalStateException("The matcher fails")));
        // Not opened in an editor, which would match it too
        PsiFile file = myFixture.addFileToProject("test.both", "content");

        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(10, TimeUnit.SECONDS);

        assertEquals(List.of(definition), definitionsOf(servers));
    }

    public void testAMatcherThatFailsKeepsTheOtherAsynchronousMatches() throws Exception {
        // The definition of the fixture matches test.async asynchronously
        registerFailingDefinition("*.async", file -> CompletableFuture.failedFuture(new IllegalStateException("The matcher fails")));
        PsiFile file = myFixture.addFileToProject("test.async", "content");

        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(10, TimeUnit.SECONDS);

        assertEquals(List.of(definition), definitionsOf(servers));
    }

    public void testTheFailureOfAMatcherIsLoggedOnceAsAWarningWithTheNameOfItsDefinition() throws Exception {
        var failure = new IllegalStateException("The matcher fails");
        var asked = new AtomicInteger();
        registerFailingDefinition("*.both", file -> {
            asked.incrementAndGet();
            return CompletableFuture.failedFuture(failure);
        });
        PsiFile file = myFixture.addFileToProject("test.both", "content");

        var logged = logRecordsOf(() -> assertEquals(List.of(definition), definitionsOf(LanguageServiceAccessor
                .getInstance(myFixture.getProject()).getLanguageServers(file, null, null).get(10, TimeUnit.SECONDS))));

        assertEquals("the file must be matched once", 1, asked.get());
        var warnings = warnings(logged);
        assertEquals("the failure must be logged once", 1, warnings.size());
        var warning = warnings.get(0);
        assertEquals(Level.WARNING, warning.getLevel());
        assertSame(failure, warning.getThrown());
        assertTrue(warning.getMessage(), warning.getMessage().contains("'" + FAILING_DEFINITION_NAME + "'"));
        assertTrue(warning.getMessage(), warning.getMessage().contains(file.getVirtualFile().getUrl()));
    }

    public void testAMatcherWhoseMatchAsyncThrowsMatchesNothingAndIsLogged() throws Exception {
        var failure = new IllegalStateException("The matcher throws");
        registerFailingDefinition("*.both", file -> {
            throw failure;
        });
        PsiFile file = myFixture.addFileToProject("test.both", "content");

        var logged = logRecordsOf(() -> assertEquals(List.of(definition), definitionsOf(LanguageServiceAccessor
                .getInstance(myFixture.getProject()).getLanguageServers(file, null, null).get(10, TimeUnit.SECONDS))));

        var warnings = warnings(logged);
        assertEquals("the failure must be logged once", 1, warnings.size());
        assertSame(failure, warnings.get(0).getThrown());
    }

    public void testACancelledMatchStillCancelsTheRequestWithoutALog() throws Exception {
        // As an AbstractDocumentMatcher cancels its earlier match of the file when it is asked again: the feature whose
        // request is cancelled asks again, while it would keep a result without the language server of the matcher
        var answer = new CompletableFuture<Boolean>();
        registerFailingDefinition("*.both", file -> answer);
        PsiFile file = myFixture.addFileToProject("test.both", "content");

        var logged = logRecordsOf(() -> {
            var request = LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null);
            assertFalse("The request must wait for the matcher", request.isDone());
            answer.cancel(false);

            assertCancelled(request);
        });

        assertEquals("the cancellation must not be logged", List.of(), warnings(logged));
    }

    public void testACancelledMatchStillCancelsTheQueryWhetherTheFileIsMappedToTheDefinition() throws Exception {
        var answer = new CompletableFuture<Boolean>();
        var cancelledDefinition = registerFailingDefinition("*.both", file -> answer);
        var file = myFixture.addFileToProject("test.both", "content").getVirtualFile();

        var logged = logRecordsOf(() -> {
            var mapped = LanguageServiceAccessor.getInstance(myFixture.getProject()).isFileMappedToServer(file, cancelledDefinition);
            assertFalse("The query must wait for the matcher", mapped.isDone());
            answer.cancel(false);

            assertCancelled(mapped);
        });

        assertEquals("the cancellation must not be logged", List.of(), warnings(logged));
    }

    public void testAFileIsMappedToADefinitionByItsOtherMappingsWhenAMatcherFails() throws Exception {
        // The definition of the fixture matches test.async asynchronously with another mapping too
        registerAsynchronousMapping(definition, "*.async",
                file -> CompletableFuture.failedFuture(new IllegalStateException("The matcher fails")));
        var failing = registerFailingDefinition("*.both",
                file -> CompletableFuture.failedFuture(new IllegalStateException("The matcher fails")));
        var accessor = LanguageServiceAccessor.getInstance(myFixture.getProject());

        assertTrue(accessor.isFileMappedToServer(myFixture.addFileToProject("test.async", "content").getVirtualFile(),
                definition).get(10, TimeUnit.SECONDS));
        assertFalse(accessor.isFileMappedToServer(myFixture.addFileToProject("test.both", "content").getVirtualFile(),
                failing).get(10, TimeUnit.SECONDS));
    }

    public void testASynchronousMatcherThatThrowsKeepsTheLanguageServersThatOtherMappingsMatch() throws Exception {
        registerThrowingDefinition("*.both", (file, project) -> {
            throw new IllegalStateException("The matcher throws");
        });
        PsiFile file = myFixture.addFileToProject("test.both", "content");

        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(10, TimeUnit.SECONDS);

        assertEquals(List.of(definition), definitionsOf(servers));
    }

    public void testTheExceptionOfASynchronousMatcherIsLoggedOnceAsAWarningWithTheNameOfItsDefinition() throws Exception {
        var failure = new IllegalStateException("The matcher throws");
        var asked = new AtomicInteger();
        registerThrowingDefinition("*.both", (file, project) -> {
            asked.incrementAndGet();
            throw failure;
        });
        PsiFile file = myFixture.addFileToProject("test.both", "content");

        var logged = logRecordsOf(() -> assertEquals(List.of(definition), definitionsOf(LanguageServiceAccessor
                .getInstance(myFixture.getProject()).getLanguageServers(file, null, null).get(10, TimeUnit.SECONDS))));

        // A file name pattern mapping is found for the language of the file and for its file type
        assertEquals("the file must be matched once", 1, asked.get());
        var warnings = warnings(logged);
        assertEquals("the exception must be logged once", 1, warnings.size());
        var warning = warnings.get(0);
        assertEquals(Level.WARNING, warning.getLevel());
        assertSame(failure, warning.getThrown());
        assertTrue(warning.getMessage(), warning.getMessage().contains("'" + FAILING_DEFINITION_NAME + "'"));
        assertTrue(warning.getMessage(), warning.getMessage().contains(file.getVirtualFile().getUrl()));
    }

    public void testASynchronousMatcherThatThrowsMatchesNothingWithOrWithoutReadAccess() throws Exception {
        var failure = new IllegalStateException("The matcher throws");
        registerThrowingDefinition("*.both", (file, project) -> {
            throw failure;
        });
        PsiFile file = myFixture.addFileToProject("test.both", "content");
        var accessor = LanguageServiceAccessor.getInstance(myFixture.getProject());

        var logged = logRecordsOf(() -> {
            assertEquals(Set.of(definition), ReadAction.compute(() -> accessor.getMatchedLanguageServerDefinitions(file)));
            // Without read access, the matcher is asked in a read action of its own
            var withoutReadAccess = ApplicationManager.getApplication().executeOnPooledThread(() -> {
                assertFalse(ApplicationManager.getApplication().isReadAccessAllowed());
                return accessor.getMatchedLanguageServerDefinitions(file);
            });
            PlatformTestUtil.waitWithEventsDispatching("The match without read access must end", withoutReadAccess::isDone, 10);
            assertEquals(Set.of(definition), withoutReadAccess.get());
        });

        var warnings = warnings(logged);
        assertEquals("the exception must be logged once by each match", 2, warnings.size());
        for (var warning : warnings) {
            assertSame(failure, warning.getThrown());
        }
    }

    public void testAFileIsMappedToADefinitionByItsOtherMappingWhenItsSynchronousMatcherThrows() throws Exception {
        // The definition of the fixture matches test.async asynchronously too
        registerSynchronousMapping(definition, "*.async", (file, project) -> {
            throw new IllegalStateException("The matcher throws");
        });
        var throwing = registerThrowingDefinition("*.both", (file, project) -> {
            throw new IllegalStateException("The matcher throws");
        });
        var accessor = LanguageServiceAccessor.getInstance(myFixture.getProject());
        PsiFile file = myFixture.addFileToProject("test.async", "content");

        assertTrue(accessor.isFileMappedToServer(file.getVirtualFile(), definition).get(10, TimeUnit.SECONDS));
        assertEquals(List.of(definition), definitionsOf(accessor.getLanguageServers(file, null, null).get(10, TimeUnit.SECONDS)));
        assertFalse(accessor.isFileMappedToServer(myFixture.addFileToProject("test.both", "content").getVirtualFile(),
                throwing).get(10, TimeUnit.SECONDS));
    }

    public void testAProcessCanceledExceptionOfASynchronousMatcherStillPropagates() throws Exception {
        var cancellation = new ProcessCanceledException();
        registerThrowingDefinition("*.both", (file, project) -> {
            throw cancellation;
        });
        PsiFile file = myFixture.addFileToProject("test.both", "content");
        var accessor = LanguageServiceAccessor.getInstance(myFixture.getProject());

        var logged = logRecordsOf(() -> {
            try {
                ReadAction.compute(() -> accessor.getMatchedLanguageServerDefinitions(file));
                fail("The cancellation must propagate");
            } catch (ProcessCanceledException e) {
                assertSame(cancellation, e);
            }
        });

        assertEquals("the cancellation must not be logged", List.of(), warnings(logged));
    }

    public void testARestartConnectsTheOpenFileAgain() throws Exception {
        var opened = expectDidOpenOf("test.async");
        PsiFile file = myFixture.configureByText("test.async", "content");
        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(10, TimeUnit.SECONDS);
        assertSize(1, servers);
        var wrapper = servers.get(0).getServerWrapper();
        PlatformTestUtil.waitWithEventsDispatching("The language server must get the file's didOpen", opened::isDone, 10);

        var reopened = expectDidOpenOf("test.async");
        wrapper.restart();

        PlatformTestUtil.waitWithEventsDispatching("The restarted language server must get the file's didOpen again",
                () -> wrapper.getServerStatus() == ServerStatus.started && reopened.isDone(), 10);
    }

    /**
     * Expects the next didOpen of the file with the given name, which the mock language server completes the returned
     * future with.
     */
    private static CompletableFuture<DidOpenTextDocumentParams> expectDidOpenOf(@NotNull String fileName) {
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened,
                params -> params.getTextDocument().getUri().endsWith("/" + fileName));
        return opened;
    }

    private static List<LanguageServerDefinition> definitionsOf(@NotNull List<LanguageServerItem> servers) {
        return servers.stream().map(LanguageServerItem::getServerDefinition).toList();
    }

    /**
     * Asserts that the given future ends cancelled, or failed with a cancellation.
     */
    private static void assertCancelled(@NotNull CompletableFuture<?> future) throws Exception {
        try {
            future.get(10, TimeUnit.SECONDS);
            fail("A cancelled match must cancel the request, which got " + future.getNow(null));
        } catch (ExecutionException e) {
            assertInstanceOf(e.getCause(), CancellationException.class);
        } catch (CancellationException e) {
            // The future itself is cancelled
        }
    }

    /**
     * Runs the given code, and returns what LanguageServiceAccessor logs meanwhile, on any thread. LSP4IJ logs with
     * SLF4J, which IntelliJ binds to java.util.logging.
     */
    private static List<LogRecord> logRecordsOf(@NotNull ThrowableRunnable<Exception> code) throws Exception {
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        var logger = Logger.getLogger(LanguageServiceAccessor.class.getName());
        logger.addHandler(handler);
        try {
            code.run();
        } finally {
            logger.removeHandler(handler);
        }
        return records;
    }

    private static List<LogRecord> warnings(@NotNull List<LogRecord> records) {
        return records.stream().filter(record -> record.getLevel().intValue() >= Level.WARNING.intValue()).toList();
    }
}

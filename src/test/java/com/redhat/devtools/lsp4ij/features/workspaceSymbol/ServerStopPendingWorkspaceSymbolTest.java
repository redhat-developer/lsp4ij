/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.workspaceSymbol;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.redhat.devtools.lsp4ij.LSPWorkspaceSupport;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.internal.CancellationUtil;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.mock.MockWorkspaceService;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * The {@code workspace/symbol} request of Search Everywhere, which LSP4IJ sends to each language server of the
 * project and merges, ends when a language server stops before it answers, or doesn't answer in time, with the symbols
 * of the other language servers, instead of making the search wait until the user types again (#722).
 */
public class ServerStopPendingWorkspaceSymbolTest extends UsefulTestCase {

    private LSPCodeInsightTestFixture myFixture;
    private SymbolServerDefinition silent;
    private SymbolServerDefinition answering;

    public void testAServerWhichStopsBeforeItAnswersEndsTheSearchWithTheSymbolsOfTheOthers() throws Exception {
        var file = myFixture.configureByText("main.foo", "content");
        var silentServer = connect(file, silent);
        var symbols = LSPWorkspaceSupport.getSupport(myFixture.getProject()).getWorkspaceSymbolSupport()
                .getWorkspaceSymbol(new LSPWorkspaceSymbolParams("main"));
        PlatformTestUtil.waitWithEventsDispatching("The silent server must get the request",
                () -> !silent.requests.isEmpty(), 10);

        silentServer.getServerWrapper().stop();

        PlatformTestUtil.waitWithEventsDispatching("The search must end once the silent server stopped",
                symbols::isDone, 10);
        assertEquals(List.of("main"), symbols.get(1, TimeUnit.SECONDS).stream()
                .map(WorkspaceSymbolData::getName)
                .toList());
    }

    public void testAServerWhichDoesNotAnswerInTimeEndsTheSearchWithTheSymbolsOfTheOthers() throws Exception {
        var file = myFixture.configureByText("main.foo", "content");
        connect(file, silent);
        var workspaceSymbolSupport = LSPWorkspaceSupport.getSupport(myFixture.getProject()).getWorkspaceSymbolSupport();
        // The answering server has this time too, which a slow CI runner can use up before its answer comes. The
        // support belongs to the project of this test only.
        workspaceSymbolSupport.setAnswerTimeout(Duration.ofSeconds(5));

        var symbols = workspaceSymbolSupport.getWorkspaceSymbol(new LSPWorkspaceSymbolParams("main"));

        PlatformTestUtil.waitWithEventsDispatching("The search must end once the time of the silent server is up",
                symbols::isDone, 30);
        assertEquals(List.of("main"), symbols.get(1, TimeUnit.SECONDS).stream()
                .map(WorkspaceSymbolData::getName)
                .toList());
        // The silent server gets '$/cancelRequest', for which LSP4J cancels the request that the server hasn't answered
        PlatformTestUtil.waitWithEventsDispatching("The silent server must be asked to cancel the request",
                () -> !silent.requests.isEmpty() && silent.requests.stream().allMatch(CompletableFuture::isCancelled), 10);
    }

    public void testARequestToAServerWhichStopsBeforeTheRequestIsKeptForTheStopIsCancelled() throws Exception {
        var file = myFixture.configureByText("main.foo", "content");
        var silentServer = connect(file, silent);
        var request = silentServer.getWorkspaceService().symbol(new WorkspaceSymbolParams("main"));
        PlatformTestUtil.waitWithEventsDispatching("The silent server must get the request",
                () -> !silent.requests.isEmpty(), 10);

        // The stop, on another thread, can come between the send of the request and its keeping for the stop
        silentServer.getServerWrapper().stop();
        silentServer.getServerWrapper().cancelOnStop(silentServer.getServer(), request);

        assertTrue("The request must be cancelled, as no later stop of its server cancels it", endedCancelled(request));
    }

    /**
     * Returns whether the given request ended cancelled, by LSP4IJ or by the language server. LSP4J cancels a request
     * by sending {@code $/cancelRequest} first and cancelling its future after: the language server can answer
     * RequestCancelled in between, and the request then ends with that error, which LSP4IJ takes as a cancel too.
     */
    private static boolean endedCancelled(@NotNull CompletableFuture<?> request) {
        if (request.isCancelled()) {
            return true;
        }
        try {
            request.getNow(null);
            return false;
        } catch (CompletionException e) {
            return CancellationUtil.isRequestCancelledException(e);
        }
    }

    private @NotNull LanguageServerItem connect(@NotNull PsiFile file,
                                                @NotNull SymbolServerDefinition definition) throws Exception {
        var servers = LanguageServiceAccessor.getInstance(file.getProject())
                .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
        assertSize(2, servers);
        return servers.stream()
                .filter(server -> server.getServerDefinition() == definition)
                .findFirst()
                .orElseThrow();
    }

    /**
     * A language server with {@code workspace/symbol}, which answers with a symbol named "main", or never answers.
     */
    private static class SymbolServerDefinition extends MockLanguageServerDefinition {

        private final List<CompletableFuture<?>> requests = new CopyOnWriteArrayList<>();
        private final MockLanguageServer server;

        SymbolServerDefinition(@NotNull String id, boolean answers) {
            super(id, false);
            var workspaceService = new MockWorkspaceService(CompletableFuture::completedFuture) {
                @Override
                public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(
                        WorkspaceSymbolParams params) {
                    var request = answers
                            ? CompletableFuture.completedFuture(Either.<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>forLeft(
                            List.of(symbolNamed("main"))))
                            : new CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>>();
                    requests.add(request);
                    return request;
                }
            };
            this.server = new MockLanguageServer(() -> {
                var capabilities = MockLanguageServer.defaultServerCapabilities();
                capabilities.setWorkspaceSymbolProvider(true);
                return capabilities;
            });
            server.setWorkspaceService(workspaceService);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            return new MockConnectionProvider(server);
        }

        @Override
        public @NotNull MockLanguageServer getServer() {
            return server;
        }

        private static @NotNull SymbolInformation symbolNamed(@NotNull String name) {
            var range = new Range(new Position(0, 0), new Position(0, 4));
            return new SymbolInformation(name, SymbolKind.Function, new Location("file:///main.foo", range));
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
                IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        myFixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        myFixture.setUp();
        silent = new SymbolServerDefinition("test-workspace-symbol-silent", false);
        answering = new SymbolServerDefinition("test-workspace-symbol-answering", true);
        for (var definition : List.of(silent, answering)) {
            LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), definition,
                    List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.foo"), null)));
        }
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            for (var definition : List.of(silent, answering)) {
                definition.getServer().waitBeforeTearDown();
                LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), definition);
            }
            myFixture.tearDown();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            myFixture = null;
            super.tearDown();
        }
    }
}

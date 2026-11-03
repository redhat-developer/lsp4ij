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
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.redhat.devtools.lsp4ij.features.documentation.LSPHoverParams;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * The LSP requests still pending when a language server stops or crashes complete, and the next request is sent to the
 * restarted server, instead of the file waiting on the never completed request until it is modified.
 *
 * @see <a href="https://github.com/redhat-developer/lsp4ij/issues/1565">#1565</a>
 */
public class ServerStopPendingRequestsTest extends UsefulTestCase {

    private LSPCodeInsightTestFixture myFixture;
    private CrashingServerDefinition serverDefinition;
    // The hover requests the server got, which it never answers
    private final List<CompletableFuture<Hover>> hoverRequests = new CopyOnWriteArrayList<>();

    public void testStopCompletesPendingRequest() throws Exception {
        var file = myFixture.configureByText("main.foo", "content");
        var wrapper = connect(file);
        var hover = requestHover(file);
        awaitHoverRequests(1);

        wrapper.stop();

        assertCompletedAndRequestedAgain(file, hover);
    }

    public void testCrashCompletesPendingRequest() throws Exception {
        var file = myFixture.configureByText("main.foo", "content");
        connect(file);
        var hover = requestHover(file);
        awaitHoverRequests(1);

        serverDefinition.crash();

        assertCompletedAndRequestedAgain(file, hover);
    }

    private void assertCompletedAndRequestedAgain(@NotNull PsiFile file,
                                                  @NotNull CompletableFuture<?> hover) {
        PlatformTestUtil.waitWithEventsDispatching("The pending hover never completed after its server stopped",
                hover::isDone, 10);
        // The next hover is not the stopped server's cached request, but a new one, which restarts the server
        var nextHover = requestHover(file);
        assertNotSame(hover, nextHover);
        awaitHoverRequests(2);
    }

    private static @NotNull LanguageServerWrapper connect(@NotNull PsiFile file) throws Exception {
        var servers = LanguageServiceAccessor.getInstance(file.getProject())
                .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
        assertSize(1, servers);
        return servers.get(0).getServerWrapper();
    }

    private static @NotNull CompletableFuture<?> requestHover(@NotNull PsiFile file) {
        var params = new LSPHoverParams(new TextDocumentIdentifier(), new Position(0, 0), 0);
        return LSPFileSupport.getSupport(file).getHoverSupport().getHover(params);
    }

    private void awaitHoverRequests(int count) {
        PlatformTestUtil.waitWithEventsDispatching("The server did not get hover request " + count,
                () -> hoverRequests.size() >= count, 10);
        assertSize(count, hoverRequests);
    }

    /**
     * Server definition whose last connection can crash, as the process of a language server which dies does.
     */
    private static class CrashingServerDefinition extends MockLanguageServerDefinition {

        private volatile CrashingConnectionProvider lastConnection;

        CrashingServerDefinition() {
            super("test-server-stop-pending-requests", false);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            var connection = new CrashingConnectionProvider(getServer());
            lastConnection = connection;
            return connection;
        }

        void crash() {
            lastConnection.crash();
        }
    }

    /**
     * Connection whose crash closes its streams, is no longer alive, and calls its unexpected stop handlers, as
     * {@code LSPProcessListener} does when the process terminates.
     */
    private static class CrashingConnectionProvider extends MockConnectionProvider {

        private final List<Runnable> unexpectedStopHandlers = new CopyOnWriteArrayList<>();
        private volatile boolean crashed;

        CrashingConnectionProvider(@NotNull MockLanguageServer server) {
            super(server);
        }

        @Override
        public void addUnexpectedServerStopHandler(Runnable handler) {
            unexpectedStopHandlers.add(handler);
        }

        @Override
        public boolean isAlive() {
            return !crashed;
        }

        void crash() {
            crashed = true;
            stop();
            unexpectedStopHandlers.forEach(Runnable::run);
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
                IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        myFixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        myFixture.setUp();
        serverDefinition = new CrashingServerDefinition();
        var service = new MockTextDocumentService(CompletableFuture::completedFuture) {
            @Override
            public CompletableFuture<Hover> hover(HoverParams params) {
                var request = new CompletableFuture<Hover>();
                hoverRequests.add(request);
                return request;
            }
        };
        serverDefinition.getServer().setTextDocumentService(service);
        List<ServerMappingSettings> mappings =
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.foo"), null));
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), serverDefinition, mappings);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            serverDefinition.getServer().waitBeforeTearDown();
            LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), serverDefinition);
            myFixture.tearDown();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            myFixture = null;
            super.tearDown();
        }
    }

}

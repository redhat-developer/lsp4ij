/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The hovers of several language servers for the same file come in the order of the names of the language servers,
 * whatever the order in which the language servers are found for the file (#390). A hover finds a language server
 * for the file once the language server has started and has the file, so their order changes from one hover to the
 * next: these tests ask for the hover while one of the language servers is still starting, which makes it come last.
 */
public class HoverOrderTest extends UsefulTestCase {

    private LSPCodeInsightTestFixture myFixture;
    private final List<HoverServerDefinition> definitions = new ArrayList<>();

    public void testTheHoverOfAServerWhoseNameComesFirstComesFirstThoughTheServerIsFoundLast() throws Exception {
        var alpha = add("test-hover-alpha", "Alpha");
        var zeta = add("test-hover-zeta", "Zeta");

        assertEquals(List.of("Alpha", "Zeta"), hoversWhileStarting(alpha, zeta));
    }

    public void testTheHoverOfAServerWhoseNameComesFirstComesFirstWhenTheServerIsFoundFirst() throws Exception {
        var alpha = add("test-hover-alpha", "Alpha");
        var zeta = add("test-hover-zeta", "Zeta");

        assertEquals(List.of("Alpha", "Zeta"), hoversWhileStarting(zeta, alpha));
    }

    public void testTheNamesOfTheServersAreComparedWithoutTheirCase() throws Exception {
        var alpha = add("test-hover-alpha", "alpha");
        var zeta = add("test-hover-zeta", "Zeta");

        assertEquals(List.of("alpha", "Zeta"), hoversWhileStarting(alpha, zeta));
    }

    /**
     * Returns the hovers of the two language servers for a file that both support, as the names of the language
     * servers which answered them, in the order of the list of hovers. The hover is asked for once the language server
     * {@code started} has the file, while {@code starting} is still starting, which it does half a second later.
     */
    private @NotNull List<String> hoversWhileStarting(@NotNull HoverServerDefinition starting,
                                                      @NotNull HoverServerDefinition started) throws Exception {
        var initialize = starting.getServer().holdInitialize();
        try {
            var file = myFixture.configureByText("main.foo", "a word");
            // Starts both language servers, and connects the file to the one whose start isn't held
            var servers = LanguageServiceAccessor.getInstance(file.getProject()).getLanguageServers(file, null, null);
            PlatformTestUtil.waitWithEventsDispatching("The started language server must get the file",
                    () -> started.opened.getCount() == 0, 30);
            var params = new LSPHoverParams(new TextDocumentIdentifier(), new Position(0, 3), 3);
            var hovers = LSPFileSupport.getSupport(file).getHoverSupport().getHover(params);
            AppExecutorUtil.getAppScheduledExecutorService()
                    .schedule(() -> initialize.complete(null), 500, TimeUnit.MILLISECONDS);
            List<String> names = hovers.get(30, TimeUnit.SECONDS).stream()
                    .map(hover -> hover.hover().getContents().getRight().getValue())
                    .toList();
            assertSize(2, servers.get(30, TimeUnit.SECONDS));
            return names;
        } finally {
            initialize.complete(null);
        }
    }

    private @NotNull HoverServerDefinition add(@NotNull String id, @NotNull String name) {
        var definition = new HoverServerDefinition(id, name);
        definitions.add(definition);
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.foo"), null)));
        return definition;
    }

    /**
     * A language server with the given name, which answers a hover with its name.
     */
    private static class HoverServerDefinition extends MockLanguageServerDefinition {

        private final @NotNull String name;
        private final @NotNull MockLanguageServer server;
        private final CountDownLatch opened = new CountDownLatch(1);

        HoverServerDefinition(@NotNull String id, @NotNull String name) {
            super(id, false);
            this.name = name;
            this.server = new MockLanguageServer(MockLanguageServer::defaultServerCapabilities);
            server.setTextDocumentService(new MockTextDocumentService(CompletableFuture::completedFuture) {
                @Override
                public void didOpen(DidOpenTextDocumentParams params) {
                    super.didOpen(params);
                    opened.countDown();
                }

                @Override
                public CompletableFuture<Hover> hover(HoverParams params) {
                    return CompletableFuture.completedFuture(new Hover(new MarkupContent(MarkupKind.PLAINTEXT, name)));
                }
            });
        }

        @Override
        public @NotNull String getDisplayName() {
            return name;
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

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
                IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        myFixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        myFixture.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            for (var definition : definitions) {
                definition.getServer().waitBeforeTearDown();
                LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), definition);
            }
            myFixture.tearDown();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            myFixture = null;
            definitions.clear();
            super.tearDown();
        }
    }
}

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

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A language server whose connection fails to stop is still cleaned up as it is disposed, and neither keeps the other
 * language servers from being disposed nor stays among the started servers: when its server definition is removed,
 * and when the project closes. A dispose that is canceled ends the disposing, and the language servers it did not reach
 * stay started, for a later removal or close to dispose.
 */
public class LanguageServerDisposeFailureTest extends LSPCodeInsightFixtureTestCase {

    private Project project;
    private StopFailingServerDefinition definition;
    private LanguageServiceAccessor accessor;

    public LanguageServerDisposeFailureTest() {
        super("*.ts");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        project = myFixture.getProject();
        definition = new StopFailingServerDefinition();
        register(definition, "*.failing");
        accessor = LanguageServiceAccessor.getInstance(project);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);
        } finally {
            super.tearDown();
        }
    }

    public void testALanguageServerWhoseConnectionFailsToStopIsRemovedWithItsDefinition() throws Exception {
        var failing = start("one.failing");

        LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);

        assertEquals("The connection must be asked to stop, which fails", 1, definition.stops.get());
        assertTrue(failing.isDisposed());
        assertEquals("The dispose must clean up after the failed stop", 1, definition.disposedClientFeatures.get());
        assertFalse("No language server of the removed definition may stay started",
                accessor.getStartedServers().stream().anyMatch(server -> server.getServerDefinition() == definition));
    }

    public void testALanguageServerWhoseConnectionFailsToStopSaysThatItIsDisposed() throws Exception {
        var failing = start("one.failing");
        List<LanguageServerWrapper> disposed = new CopyOnWriteArrayList<>();
        var listener = new LanguageServerLifecycleListener() {
            @Override
            public void handleStatusChanged(@NotNull LanguageServerWrapper languageServer) {
            }

            @Override
            public void handleLSPMessage(@NotNull Message message,
                                         @NotNull MessageConsumer messageConsumer,
                                         @NotNull LanguageServerWrapper languageServer) {
            }

            @Override
            public void handleError(@NotNull LanguageServerWrapper languageServer, @Nullable Throwable exception) {
            }

            @Override
            public void handleDisposed(@NotNull LanguageServerWrapper languageServer) {
                disposed.add(languageServer);
            }

            @Override
            public void dispose() {
            }
        };
        var lifecycleManager = LanguageServerLifecycleManager.getInstance(project);
        lifecycleManager.addLanguageServerLifecycleListener(listener);
        try {
            LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);
        } finally {
            lifecycleManager.removeLanguageServerLifecycleListener(listener);
        }

        assertEquals("The connection must be asked to stop, which fails", 1, definition.stops.get());
        assertTrue(failing.isDisposed());
        // A language server that is disposed is never disposed again, so the listeners, like the Language Servers view,
        // would never learn that it went
        assertEquals(List.of(failing), disposed);
    }

    public void testEveryLanguageServerIsDisposedAsTheProjectClosesThoughOneFailsToStop() throws Exception {
        var failing = start("one.failing");
        var other = start("other.ts");

        // Whichever is disposed first, the other must be disposed too, and neither may stay started
        accessor.projectClosing(project);

        assertEquals("The connection must be asked to stop, which fails", 1, definition.stops.get());
        assertTrue(failing.isDisposed());
        assertEquals("The dispose must clean up after the failed stop", 1, definition.disposedClientFeatures.get());
        assertTrue(other.isDisposed());
        assertEmpty(accessor.getStartedServers());
    }

    public void testALanguageServerThatACanceledDisposeDidNotReachStaysStartedUntilTheProjectClosesAgain()
            throws Exception {
        var canceling = new CancelingServerDefinition();
        register(canceling, "*.canceling");
        try {
            var first = startAnother(canceling);
            var second = startAnother(canceling);

            assertThrows(ProcessCanceledException.class, () -> accessor.projectClosing(project));

            assertEquals("The first connection asked to stop cancels", 1, canceling.stops.get());
            var reached = first.isDisposed() ? first : second;
            var unreached = reached == first ? second : first;
            assertTrue(reached.isDisposed());
            assertDoesntContain(accessor.getStartedServers(), reached);
            assertFalse("The canceled dispose must not go on", unreached.isDisposed());
            assertContainsElements(accessor.getStartedServers(), unreached);

            accessor.projectClosing(project);

            assertEquals(2, canceling.stops.get());
            assertTrue("A later close must dispose the language server", unreached.isDisposed());
            assertEmpty(accessor.getStartedServers());
        } finally {
            LanguageServersRegistry.getInstance().removeServerDefinition(project, canceling);
        }
    }

    public void testALanguageServerThatACanceledDisposeDidNotReachStaysStartedUntilItsDefinitionIsRemovedAgain()
            throws Exception {
        var canceling = new CancelingServerDefinition();
        var registry = LanguageServersRegistry.getInstance();
        register(canceling, "*.canceling");
        try {
            var first = startAnother(canceling);
            var second = startAnother(canceling);

            // the registry logs what a listener throws, the cancellation too
            registry.removeServerDefinition(project, canceling);

            assertEquals("The first connection asked to stop cancels", 1, canceling.stops.get());
            var reached = first.isDisposed() ? first : second;
            var unreached = reached == first ? second : first;
            assertTrue(reached.isDisposed());
            assertDoesntContain(accessor.getStartedServers(), reached);
            assertFalse("The canceled dispose must not go on", unreached.isDisposed());
            assertContainsElements(accessor.getStartedServers(), unreached);

            registry.removeServerDefinition(project, canceling);

            assertEquals(2, canceling.stops.get());
            assertTrue("A later removal must dispose the language server", unreached.isDisposed());
            assertFalse(accessor.getStartedServers().stream()
                    .anyMatch(server -> server.getServerDefinition() == canceling));
        } finally {
            registry.removeServerDefinition(project, canceling);
        }
    }

    private void register(@NotNull LanguageServerDefinition serverDefinition, @NotNull String fileNamePattern) {
        var mapping = ServerMappingSettings.createFileNamePatternsMappingSettings(List.of(fileNamePattern), "plaintext");
        LanguageServersRegistry.getInstance().addServerDefinition(project, serverDefinition, List.of(mapping));
    }

    /**
     * Starts another language server of the given definition, and returns it once it is initialized.
     */
    private @NotNull LanguageServerWrapper startAnother(@NotNull LanguageServerDefinition serverDefinition)
            throws Exception {
        var languageServer = accessor.forceCreateServer(serverDefinition);
        assertNotNull(waitFor(languageServer.getInitializedServer()));
        return languageServer;
    }

    /**
     * Starts the language server of a new file of the given name, and returns it once it is initialized.
     */
    private @NotNull LanguageServerWrapper start(@NotNull String fileName) throws Exception {
        var started = waitFor(accessor.getLanguageServers(myFixture.addFileToProject(fileName, "text"), null, null));
        assertEquals(1, started.size());
        var languageServer = started.get(0).getServerWrapper();
        waitFor(languageServer.getInitializedServer());
        assertContainsElements(accessor.getStartedServers(), languageServer);
        return languageServer;
    }

    private static <T> T waitFor(@NotNull CompletableFuture<T> future) throws Exception {
        PlatformTestUtil.waitWithEventsDispatching("The language server must answer", future::isDone, 10);
        return future.get();
    }

    /**
     * Server definition whose connection throws when it is stopped, once it has closed its streams, and which counts
     * the client features of a language server disposed, as the cleanup that a dispose makes after it stops the
     * connection. The definition's own client features, which give its workspace folder strategy, don't count.
     */
    private static class StopFailingServerDefinition extends MockLanguageServerDefinition {

        private final AtomicInteger stops = new AtomicInteger();
        private final AtomicInteger disposedClientFeatures = new AtomicInteger();

        StopFailingServerDefinition() {
            super("stop-failing-server", false);
        }

        @Override
        public @NotNull LSPClientFeatures createClientFeatures() {
            return new LSPClientFeatures() {
                @Override
                public void dispose() {
                    boolean ofALanguageServer = getServerWrapper() != null;
                    super.dispose();
                    if (ofALanguageServer) {
                        disposedClientFeatures.incrementAndGet();
                    }
                }
            };
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            return new MockConnectionProvider(getServer()) {
                @Override
                public synchronized void stop() {
                    super.stop();
                    stops.incrementAndGet();
                    throw new IllegalStateException("The connection fails to stop");
                }
            };
        }
    }

    /**
     * Server definition whose connection that is stopped first cancels, once it has closed its streams.
     */
    private static class CancelingServerDefinition extends MockLanguageServerDefinition {

        private final AtomicInteger stops = new AtomicInteger();
        private final AtomicBoolean canceled = new AtomicBoolean();

        CancelingServerDefinition() {
            super("canceling-server", false);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            return new MockConnectionProvider(getServer()) {
                @Override
                public synchronized void stop() {
                    super.stop();
                    stops.incrementAndGet();
                    if (canceled.compareAndSet(false, true)) {
                        throw new ProcessCanceledException();
                    }
                }
            };
        }
    }
}

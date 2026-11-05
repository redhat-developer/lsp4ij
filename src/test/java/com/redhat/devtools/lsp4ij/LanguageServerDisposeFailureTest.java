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
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A language server whose connection fails to stop is still cleaned up as it is disposed, and neither keeps the other
 * language servers from being disposed nor stays among the started servers: when its server definition is removed,
 * and when the project closes.
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
        LanguageServersRegistry.getInstance().addServerDefinition(project, definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.failing"), "plaintext")));
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
     * the client features disposed, as the cleanup that a dispose makes after it stops the connection.
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
                    super.dispose();
                    disposedClientFeatures.incrementAndGet();
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
}

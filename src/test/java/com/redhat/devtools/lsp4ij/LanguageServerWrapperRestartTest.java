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
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Test for the restart attempts of {@link LanguageServerWrapper} after a start failure.
 *
 * @see <a href="https://github.com/redhat-developer/lsp4ij/issues/1673">#1673</a>
 */
public class LanguageServerWrapperRestartTest extends UsefulTestCase {

    private LSPCodeInsightTestFixture myFixture;
    private FailingOnceServerDefinition serverDefinition;

    public void testStartDoesNotAbortRestartInFlight() {
        var wrapper = new LanguageServerWrapper(myFixture.getProject(), serverDefinition);
        Disposer.register(getTestRootDisposable(), wrapper);

        // The first start fails: serverError is set.
        wrapper.start();
        waitUntil(() -> wrapper.getServerError() != null && wrapper.getServerStatus() == ServerStatus.stopped,
                "the first start should fail");
        assertEquals(1, serverDefinition.connectionCount.get());

        // The next start() launches a restart attempt which initializes slowly.
        serverDefinition.getServer().setTimeToProceedQueries(2000);
        wrapper.start();
        waitUntil(() -> serverDefinition.connectionCount.get() == 2, "the restart attempt should be launched");
        assertEquals(1, wrapper.getNumberOfRestartAttempts());

        // Further start() calls while the attempt is still initializing must not abort it and spawn new processes.
        for (int i = 0; i < 5; i++) {
            wrapper.start();
        }
        assertEquals(2, serverDefinition.connectionCount.get());
        assertEquals(1, wrapper.getNumberOfRestartAttempts());

        // The restart attempt completes.
        waitUntil(() -> wrapper.getServerStatus() == ServerStatus.started, "the restart attempt should start the server");
        assertNull(wrapper.getServerError());
        assertEquals(2, serverDefinition.connectionCount.get());
        assertEquals(1, wrapper.getNumberOfRestartAttempts());
        serverDefinition.getServer().setTimeToProceedQueries(0);

        wrapper.stop();
        waitUntil(() -> wrapper.getServerStatus() == ServerStatus.stopped, "the server should stop");
    }

    private static void waitUntil(BooleanSupplier condition, String message) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timeout: " + message);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted: " + message);
            }
        }
    }

    /**
     * Server definition whose first connection fails to start.
     */
    private static class FailingOnceServerDefinition extends MockLanguageServerDefinition {

        private final AtomicInteger connectionCount = new AtomicInteger();

        FailingOnceServerDefinition() {
            super("test-server-restart", false);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            if (connectionCount.incrementAndGet() == 1) {
                return new MockConnectionProvider(getServer()) {
                    @Override
                    public void start() throws CannotStartProcessException {
                        throw new CannotStartProcessException("Simulated start failure");
                    }
                };
            }
            return super.createConnectionProvider(project);
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        myFixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        myFixture.setUp();
        serverDefinition = new FailingOnceServerDefinition();
        List<ServerMappingSettings> mappings = List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.foo"), null));
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), serverDefinition, mappings);
    }

    @Override
    protected void tearDown() throws Exception {
        serverDefinition.getServer().waitBeforeTearDown();
        LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), serverDefinition);
        try {
            myFixture.tearDown();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            myFixture = null;
            super.tearDown();
        }
    }

}

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

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
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
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A {@link LanguageServerWrapper} stopped or disposed before the connection of a start exists closes the connection
 * which the start then opens: the stop could not close it, and nothing else would, so it stayed open, with the mock
 * language server's thread which reads it.
 */
public class LanguageServerWrapperDisposeWhileStartingTest extends UsefulTestCase {

    private LSPCodeInsightTestFixture myFixture;
    private SlowConnectionServerDefinition serverDefinition;

    public void testADisposeWhileTheConnectionIsCreatedClosesIt() throws Exception {
        var wrapper = new LanguageServerWrapper(myFixture.getProject(), serverDefinition);

        wrapper.start();
        await(serverDefinition.creating);
        // The wrapper is disposed, as when its server definition is removed, while the connection is being created
        wrapper.dispose();
        serverDefinition.proceed.countDown();

        assertTrue("the connection should be closed", serverDefinition.stopped.await(10, TimeUnit.SECONDS));
    }

    public void testAStopWhileTheConnectionIsCreatedClosesIt() throws Exception {
        var wrapper = new LanguageServerWrapper(myFixture.getProject(), serverDefinition);
        Disposer.register(getTestRootDisposable(), wrapper);

        wrapper.start();
        await(serverDefinition.creating);
        // The language server is stopped, not disposed, while the connection is being created
        wrapper.stop();
        serverDefinition.proceed.countDown();

        assertTrue("the connection should be closed", serverDefinition.stopped.await(10, TimeUnit.SECONDS));
    }

    public void testAStartAfterADisposeClosesTheConnectionBeforeItFails() throws Exception {
        var wrapper = new LanguageServerWrapper(myFixture.getProject(), serverDefinition);
        var closedWhenNotified = new CompletableFuture<Boolean>();
        myFixture.getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                closedWhenNotified.complete(serverDefinition.stopped.getCount() == 0);
            }
        });
        serverDefinition.proceed.countDown();

        // A start after the dispose, as from a document which connects late, fails: its connection is closed first
        wrapper.dispose();
        wrapper.start();

        assertTrue("the connection should be closed", serverDefinition.stopped.await(10, TimeUnit.SECONDS));
        assertTrue("the connection should be closed before the error notification", closedWhenNotified.get(10, TimeUnit.SECONDS));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted");
        }
    }

    /**
     * Server definition whose connection is created once the test lets it, and which tells when the connection is
     * closed.
     */
    private static class SlowConnectionServerDefinition extends MockLanguageServerDefinition {

        private final CountDownLatch creating = new CountDownLatch(1);
        private final CountDownLatch proceed = new CountDownLatch(1);
        private final CountDownLatch stopped = new CountDownLatch(1);

        SlowConnectionServerDefinition() {
            super("test-server-dispose-while-starting", false);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            creating.countDown();
            await(proceed);
            return new MockConnectionProvider(getServer()) {
                @Override
                public void stop() {
                    super.stop();
                    stopped.countDown();
                }
            };
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        myFixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        myFixture.setUp();
        serverDefinition = new SlowConnectionServerDefinition();
        List<ServerMappingSettings> mappings = List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.foo"), null));
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), serverDefinition, mappings);
    }

    @Override
    protected void tearDown() throws Exception {
        // Let a connection still being created go on, should the test have failed before it did
        serverDefinition.proceed.countDown();
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

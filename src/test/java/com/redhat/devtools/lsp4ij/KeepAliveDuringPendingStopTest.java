/*******************************************************************************
 * Copyright (c) 2026 Contributors to the LSP4IJ project.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Once its last document has disconnected, a language server is stopped after lastDocumentDisconnectedTimeout,
 * unless something keeps it alive by then: a lease taken while the stop is pending cancels it, and a stop that comes
 * due while keepServerAlive() says so, or fails to answer, leaves the server running. Until the stop runs, the server
 * stays started, and neither the pending stop nor its cancellation changes the status of an explicit stop.
 */
public class KeepAliveDuringPendingStopTest extends UsefulTestCase {
    private LSPCodeInsightTestFixture fixture;
    private KeptServer definition;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        var builder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        fixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(builder.getFixture());
        fixture.setUp();
        definition = new KeptServer();
        LanguageServersRegistry.getInstance().addServerDefinition(fixture.getProject(), definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.kept"), null)));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            try {
                if (fixture != null && definition != null) {
                    LanguageServersRegistry.getInstance().removeServerDefinition(fixture.getProject(), definition);
                }
            } finally {
                if (fixture != null) {
                    fixture.tearDown();
                }
            }
        } finally {
            super.tearDown();
        }
    }

    public void testTheServerStaysStartedWhileItsStopIsPending() throws Exception {
        var wrapper = start().getServerWrapper();
        definition.statusChanges.clear();
        // As the last document's disconnection does when nothing keeps the server alive
        startStopTimer(wrapper);

        assertEquals(ServerStatus.started, wrapper.getServerStatus());
        assertEquals(1, stopAlarm(wrapper).getActiveRequestCount());
        assertEmpty(definition.statusChanges);
    }

    public void testALeaseTakenWhileTheStopIsPendingCancelsIt() throws Exception {
        var item = start();
        var wrapper = item.getServerWrapper();
        startStopTimer(wrapper);
        assertEquals(1, stopAlarm(wrapper).getActiveRequestCount());

        var lease = item.keepAlive();
        try {
            assertEquals(ServerStatus.started, wrapper.getServerStatus());
            assertEquals(0, stopAlarm(wrapper).getActiveRequestCount());
        } finally {
            lease.dispose();
        }
    }

    public void testALeaseThatCancelsAPendingStopLeavesAnExplicitStopStopping() throws Exception {
        var item = start();
        var wrapper = item.getServerWrapper();
        startStopTimer(wrapper);
        // An explicit stop() sets stopping, then cancels the pending stop: the lease comes in between
        wrapper.updateStatus(ServerStatus.stopping);

        var lease = item.keepAlive();
        try {
            assertEquals(0, stopAlarm(wrapper).getActiveRequestCount());
            assertEquals(ServerStatus.stopping, wrapper.getServerStatus());
        } finally {
            lease.dispose();
        }
    }

    public void testAStopThatComesDueWhileTheClientKeepsTheServerAliveLeavesItRunning() throws Exception {
        var wrapper = start().getServerWrapper();
        // keepServerAlive() is asked, not announced: it now says yes, while the stop is pending
        definition.keepAlive = true;

        wrapper.stopIfNotKeptAlive();
        assertEquals(ServerStatus.started, wrapper.getServerStatus());
        assertTrue(wrapper.isActive());
    }

    public void testAScheduledStopThatComesDueWhileTheClientKeepsTheServerAliveLeavesItRunning() throws Exception {
        definition.stopAtOnce = true;
        var wrapper = start().getServerWrapper();
        definition.keepAlive = true;
        startStopTimer(wrapper);

        var stopAlarm = stopAlarm(wrapper);
        PlatformTestUtil.waitWithEventsDispatching("the scheduled stop must run",
                () -> stopAlarm.getActiveRequestCount() == 0, 10);
        assertEquals(ServerStatus.started, wrapper.getServerStatus());
        assertTrue(wrapper.isActive());
    }

    public void testAStopThatComesDueWhenTheKeepAliveCheckFailsLeavesTheServerRunning() throws Exception {
        var wrapper = start().getServerWrapper();
        definition.keepAliveFailure = new IllegalStateException("keepServerAlive() failed");

        wrapper.stopIfNotKeptAlive();
        assertEquals(ServerStatus.started, wrapper.getServerStatus());
        assertTrue(wrapper.isActive());
    }

    public void testAStopThatComesDueDuringAnExplicitStopLeavesItStopping() throws Exception {
        var wrapper = start().getServerWrapper();
        wrapper.stop().get(10, TimeUnit.SECONDS);
        // What an explicit stop() leaves while its shutdown runs: stopping, with the server's context dropped
        wrapper.updateStatus(ServerStatus.stopping);
        definition.keepAlive = true;

        wrapper.stopIfNotKeptAlive();
        assertEquals(ServerStatus.stopping, wrapper.getServerStatus());
        assertFalse(wrapper.isActive());
    }

    public void testAStopThatComesDueWithNothingKeepingTheServerStopsIt() throws Exception {
        var wrapper = start().getServerWrapper();
        definition.statusChanges.clear();
        startStopTimer(wrapper);
        assertEquals(ServerStatus.started, wrapper.getServerStatus());

        // Runs the scheduled stop now, as the alarm does once lastDocumentDisconnectedTimeout has passed
        stopAlarm(wrapper).drainRequestsInTest();
        PlatformTestUtil.waitWithEventsDispatching("the server must stop",
                () -> definition.statusChanges.contains(ServerStatus.stopped), 10);
        assertEquals(List.of(ServerStatus.stopping, ServerStatus.stopped), definition.statusChanges);
    }

    /** The server started without a document, as LanguageServerManager starts one. */
    private LanguageServerItem start() throws Exception {
        var item = LanguageServerManager.getInstance(fixture.getProject()).getLanguageServer(definition.getId())
                .get(10, TimeUnit.SECONDS);
        assertNotNull(item);
        assertEquals(ServerStatus.started, item.getServerWrapper().getServerStatus());
        return item;
    }

    private static void startStopTimer(LanguageServerWrapper wrapper) throws ReflectiveOperationException {
        var method = LanguageServerWrapper.class.getDeclaredMethod("startStopTimer");
        method.setAccessible(true);
        method.invoke(wrapper);
    }

    private static Alarm stopAlarm(LanguageServerWrapper wrapper) throws ReflectiveOperationException {
        var field = LanguageServerWrapper.class.getDeclaredField("stopAlarm");
        field.setAccessible(true);
        return (Alarm) field.get(wrapper);
    }

    private static class KeptServer extends MockLanguageServerDefinition {
        final List<ServerStatus> statusChanges = new CopyOnWriteArrayList<>();
        volatile boolean keepAlive;
        volatile @Nullable RuntimeException keepAliveFailure;
        volatile boolean stopAtOnce;

        KeptServer() {
            super("keep-alive-during-pending-stop", false);
        }

        @Override
        public int getLastDocumentDisconnectedTimeout() {
            return stopAtOnce ? 0 : super.getLastDocumentDisconnectedTimeout();
        }

        @Override
        public @NotNull LSPClientFeatures createClientFeatures() {
            return new LSPClientFeatures() {
                @Override
                public boolean keepServerAlive() {
                    var failure = keepAliveFailure;
                    if (failure != null) {
                        throw failure;
                    }
                    return keepAlive;
                }

                @Override
                public void handleServerStatusChanged(@NotNull ServerStatus serverStatus) {
                    statusChanges.add(serverStatus);
                }
            };
        }
    }
}

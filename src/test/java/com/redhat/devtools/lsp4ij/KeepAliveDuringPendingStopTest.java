/*******************************************************************************
 * Copyright (c) 2026 Contributors to the LSP4IJ project.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Once its last document has disconnected, a language server is stopped after lastDocumentDisconnectedTimeout,
 * unless something keeps it alive by then: a lease taken while the stop is pending cancels it, and a stop that comes
 * due while keepServerAlive() says so, or fails to answer, leaves the server running. Until the stop runs, the server
 * stays started, and neither the pending stop nor its cancellation changes the status of an explicit stop. A lease
 * keeps the server running across a restart until it ends. A stop for idleness asked for while a restart connects the
 * open documents again, as by the end of a lease, is decided once they are connected or the start has ended without
 * that: the restarting server isn't stopped as it starts, and a restarted server that no stop was asked for keeps
 * running.
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

    public void testALeaseThatEndsAsTheServerRestartsLetsItStartBeforeItStops() throws Exception {
        var item = start();
        var wrapper = item.getServerWrapper();
        // As a file operation just before the restart takes one, which ends once the stopped server is told of it
        var lease = item.keepAlive();
        var initialize = definition.getServer().holdInitialize();
        CompletableFuture<LanguageServer> restarted;
        try {
            wrapper.restart();
            definition.statusChanges.clear();
            // A feature asks for the restarted server, which has no document until it has initialized
            restarted = wrapper.getInitializedServer();

            lease.dispose();
            assertFalse("the end of the lease must not stop the restarted server as it starts", restarted.isDone());
            assertFalse(definition.statusChanges.contains(ServerStatus.stopping));
        } finally {
            initialize.complete(null);
        }
        // The request isn't ended by the end of the lease: it gets the restarted server, or none if the stop below
        // came first. In unit-test mode that stop comes at once, as nothing keeps the server running
        restarted.get(10, TimeUnit.SECONDS);
        // Then the end of the lease is decided: nothing keeps the server, which has no document, running
        PlatformTestUtil.waitWithEventsDispatching("the restarted server must stop once it has started",
                () -> definition.statusChanges.contains(ServerStatus.stopping), 10);
        var started = definition.statusChanges.indexOf(ServerStatus.started);
        assertTrue("the restarted server must start before the end of the lease stops it: " + definition.statusChanges,
                started >= 0 && started < definition.statusChanges.indexOf(ServerStatus.stopping));
    }

    public void testALeaseHeldAcrossARestartKeepsTheRestartedServerRunningUntilItEnds() throws Exception {
        var item = start();
        var wrapper = item.getServerWrapper();
        var heldAcross = item.keepAlive();
        try {
            wrapper.restart();
            var restarted = new LanguageServerItem(wrapper.getInitializedServer().get(10, TimeUnit.SECONDS), wrapper);
            awaitTheEndOfTheReconnection(wrapper);
            assertSame(item, heldAcross.get());
            definition.statusChanges.clear();

            restarted.keepAlive().dispose();
            assertFalse("the lease held across the restart must keep the restarted server running",
                    definition.statusChanges.contains(ServerStatus.stopping));
        } finally {
            heldAcross.dispose();
        }
        // In unit-test mode, the stop comes at once
        assertTrue("the end of the last lease must stop the server, which has no document",
                definition.statusChanges.contains(ServerStatus.stopping));
    }

    public void testALeaseTakenAfterARestartStopsTheServerWithoutDocumentWhenItEnds() throws Exception {
        var wrapper = start().getServerWrapper();
        wrapper.restart();
        var item = new LanguageServerItem(wrapper.getInitializedServer().get(10, TimeUnit.SECONDS), wrapper);
        awaitTheEndOfTheReconnection(wrapper);
        definition.statusChanges.clear();

        item.keepAlive().dispose();
        // In unit-test mode, the stop comes at once
        assertTrue("the end of the lease must stop the server, which has no document",
                definition.statusChanges.contains(ServerStatus.stopping));
    }

    public void testALeaseTakenBeforeTheServerStartsKeepsItRunningAndStopsItWhenItEnds() throws Exception {
        var wrapper = new LanguageServerWrapper(fixture.getProject(), definition);
        Disposer.register(getTestRootDisposable(), wrapper);
        var lease = new LanguageServerItem(null, wrapper).keepAlive();
        try {
            assertNotNull(wrapper.getInitializedServer().get(10, TimeUnit.SECONDS));
            // As the disconnection of the last document does: the lease keeps the server, which has none
            wrapper.maybeShutdown();
            assertEquals(ServerStatus.started, wrapper.getServerStatus());
            definition.statusChanges.clear();
        } finally {
            lease.dispose();
        }
        assertTrue("the end of the lease must stop the server, which has no document",
                definition.statusChanges.contains(ServerStatus.stopping));
    }

    public void testARestartedServerWithoutDocumentOrLeaseKeepsRunning() throws Exception {
        // As LanguageServerManager.start() restarts a stopped language server, which can be used without a document
        var wrapper = start().getServerWrapper();
        definition.statusChanges.clear();

        wrapper.restart();
        assertNotNull(wrapper.getInitializedServer().get(10, TimeUnit.SECONDS));
        awaitTheEndOfTheReconnection(wrapper);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals(ServerStatus.started, wrapper.getServerStatus());
        assertEquals("only the stop of the restart must stop the server", 1,
                definition.statusChanges.stream().filter(ServerStatus.stopping::equals).count());
    }

    public void testTheReconnectionOfARestartEndsWhenItsStartFails() throws Exception {
        var item = start();
        var wrapper = item.getServerWrapper();
        definition.failingStart = true;

        wrapper.restart();
        PlatformTestUtil.waitWithEventsDispatching("the start of the restart must fail",
                () -> wrapper.getServerError() != null && wrapper.getServerStatus() == ServerStatus.stopped, 10);
        awaitTheEndOfTheReconnection(wrapper);
        definition.statusChanges.clear();

        // Nothing holds off a stop for idleness any longer
        item.keepAlive().dispose();
        assertTrue(definition.statusChanges.contains(ServerStatus.stopping));
    }

    public void testTheReconnectionOfAStartForAnEndedConnectionEndsWhenTheStartIsStopped() throws Exception {
        var wrapper = start().getServerWrapper();
        var file = fixture.addFileToProject("ended.kept", "").getVirtualFile();
        assertNotNull(wrapper.connect(file, new LanguageServerWrapper.LSPFileConnectionInfo(null, null, null, true))
                .get(10, TimeUnit.SECONDS));
        // As when the connection to the language server has ended: the next start connects its documents again
        endTheConnection(wrapper);
        var initialize = definition.getServer().holdInitialize();
        try {
            wrapper.start();
            wrapper.stop();
        } finally {
            initialize.complete(null);
        }
        awaitTheEndOfTheReconnection(wrapper);
    }

    public void testTheReconnectionOfARestartEndsWhenTheRestartIsStopped() throws Exception {
        var item = start();
        var wrapper = item.getServerWrapper();
        var initialize = definition.getServer().holdInitialize();
        try {
            wrapper.restart();
            wrapper.stop();
        } finally {
            initialize.complete(null);
        }
        awaitTheEndOfTheReconnection(wrapper);
        PlatformTestUtil.waitWithEventsDispatching("the stopped restart must end",
                () -> wrapper.getServerStatus() == ServerStatus.stopped, 10);
        definition.statusChanges.clear();

        // Nothing holds off a stop for idleness any longer
        item.keepAlive().dispose();
        assertTrue(definition.statusChanges.contains(ServerStatus.stopping));
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

    /**
     * Waits until no start of the language server connects its open documents again, as a restart does, which holds
     * off the stops for idleness.
     */
    private static void awaitTheEndOfTheReconnection(LanguageServerWrapper wrapper) throws ReflectiveOperationException {
        var reconnections = LanguageServerWrapper.class.getDeclaredField("reconnections");
        reconnections.setAccessible(true);
        PlatformTestUtil.waitWithEventsDispatching("the reconnection of the restart must end", () -> {
            try {
                return reconnections.getInt(wrapper) == 0;
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }, 10);
    }

    /**
     * Ends the connection to the language server as its end of stream does, which leaves the language server inactive.
     */
    private static void endTheConnection(LanguageServerWrapper wrapper) throws ReflectiveOperationException {
        var launcherFuture = LanguageServerWrapper.class.getDeclaredField("launcherFuture");
        launcherFuture.setAccessible(true);
        launcherFuture.set(wrapper, CompletableFuture.completedFuture(null));
        assertFalse(wrapper.isActive());
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
        volatile boolean failingStart;

        KeptServer() {
            super("keep-alive-during-pending-stop", false);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            if (!failingStart) {
                return super.createConnectionProvider(project);
            }
            return new MockConnectionProvider(getServer()) {
                @Override
                public synchronized void start() throws CannotStartProcessException {
                    throw new CannotStartProcessException("Simulated start failure");
                }
            };
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

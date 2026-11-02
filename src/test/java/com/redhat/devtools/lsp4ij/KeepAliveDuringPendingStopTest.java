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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Once its last document has disconnected, a language server is stopped after lastDocumentDisconnectedTimeout,
 * unless something keeps it alive by then: a lease taken while the stop is pending cancels it, and a stop that comes
 * due while keepServerAlive() says so leaves the server running.
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

    public void testALeaseTakenWhileTheStopIsPendingCancelsIt() throws Exception {
        var item = start();
        var wrapper = item.getServerWrapper();
        // As the last document's disconnection does when nothing keeps the server alive
        startStopTimer(wrapper);
        assertEquals(ServerStatus.stopping, wrapper.getServerStatus());
        assertEquals(1, stopAlarm(wrapper).getActiveRequestCount());

        var lease = item.keepAlive();
        try {
            assertEquals(ServerStatus.started, wrapper.getServerStatus());
            assertEquals(0, stopAlarm(wrapper).getActiveRequestCount());
        } finally {
            lease.dispose();
        }
    }

    public void testAStopThatComesDueWhileTheClientKeepsTheServerAliveLeavesItRunning() throws Exception {
        var wrapper = start().getServerWrapper();
        wrapper.updateStatus(ServerStatus.stopping);
        // keepServerAlive() is asked, not announced: it now says yes, while the stop is pending
        definition.keepAlive = true;

        wrapper.stopIfNotKeptAlive();
        assertEquals(ServerStatus.started, wrapper.getServerStatus());
        assertTrue(wrapper.isActive());
    }

    public void testAStopThatComesDueWithNothingKeepingTheServerStopsIt() throws Exception {
        var wrapper = start().getServerWrapper();
        wrapper.updateStatus(ServerStatus.stopping);

        wrapper.stopIfNotKeptAlive();
        PlatformTestUtil.waitWithEventsDispatching("the server must stop",
                () -> wrapper.getServerStatus() == ServerStatus.stopped, 10);
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
        volatile boolean keepAlive;

        KeptServer() {
            super("keep-alive-during-pending-stop", false);
        }

        @Override
        public @NotNull LSPClientFeatures createClientFeatures() {
            return new LSPClientFeatures() {
                @Override
                public boolean keepServerAlive() {
                    return keepAlive;
                }
            };
        }
    }
}

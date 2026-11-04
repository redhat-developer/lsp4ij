/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Tests a language server that the filter of a request rejects before it starts.
 */
public class RejectedLanguageServerTest extends LSPCodeInsightFixtureTestCase {

    private Project project;
    private MockLanguageServerDefinition definition;
    private LanguageServiceAccessor accessor;

    public RejectedLanguageServerTest() {
        super("*.ts");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        project = myFixture.getProject();
        definition = new MockLanguageServerDefinition("rejected-server", false);
        LanguageServersRegistry.getInstance().addServerDefinition(project, definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.rejected"), "plaintext")));
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

    public void testALanguageServerThatTheFilterRejectsIsDisposed() throws Exception {
        var rejected = new ArrayList<LanguageServerWrapper>();

        var servers = waitFor(accessor.getLanguageServers(file(), features -> {
            rejected.add(features.getServerWrapper());
            return false;
        }, null));

        assertEmpty(servers);
        assertEquals(1, rejected.size());
        assertTrue(rejected.get(0).isDisposed());
        assertDoesntContain(accessor.getStartedServers(), rejected.get(0));
    }

    public void testALanguageServerThatTheFilterRejectsReportsNoStatus() throws Exception {
        var statuses = new ArrayList<ServerStatus>();
        var listener = new LanguageServerLifecycleListener() {
            @Override
            public void handleStatusChanged(@NotNull LanguageServerWrapper languageServer) {
                statuses.add(languageServer.getServerStatus());
            }

            @Override
            public void handleLSPMessage(@NotNull Message message,
                                         @NotNull MessageConsumer messageConsumer,
                                         @NotNull LanguageServerWrapper languageServer) {
            }

            @Override
            public void handleError(@NotNull LanguageServerWrapper languageServer, @NotNull Throwable exception) {
            }

            @Override
            public void dispose() {
            }
        };
        var manager = LanguageServerLifecycleManager.getInstance(project);
        manager.addLanguageServerLifecycleListener(listener);
        try {
            waitFor(accessor.getLanguageServers(file(), features -> false, null));
        } finally {
            manager.removeLanguageServerLifecycleListener(listener);
        }

        assertEmpty(statuses);
    }

    public void testAStartedLanguageServerStaysWhenTheFilterRejectsIt() throws Exception {
        var file = file();
        var started = waitFor(accessor.getLanguageServers(file, null, null));
        assertEquals(1, started.size());
        var languageServer = started.get(0).getServerWrapper();

        waitFor(accessor.getLanguageServers(file, features -> false, null));

        assertFalse(languageServer.isDisposed());
        assertEquals(ServerStatus.started, languageServer.getServerStatus());
        assertContainsElements(accessor.getStartedServers(), languageServer);
    }

    private @NotNull PsiFile file() {
        return myFixture.addFileToProject("one.rejected", "text");
    }

    private static <T> T waitFor(@NotNull CompletableFuture<T> future) throws Exception {
        PlatformTestUtil.waitWithEventsDispatching("The language servers must answer", future::isDone, 10);
        return future.get();
    }
}

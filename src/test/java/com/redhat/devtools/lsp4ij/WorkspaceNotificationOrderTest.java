/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The workspace configuration and folder notifications are sent like the document notifications, in order:
 * not before the server has initialized, and not after the document notifications sent after them.
 */
public class WorkspaceNotificationOrderTest extends LSPCodeInsightFixtureTestCase {

    public WorkspaceNotificationOrderTest() {
        super("*.foo");
    }

    public void testAConfigurationChangeDuringInitializationWaitsForItsEnd() throws Exception {
        var initialize = MockLanguageServer.INSTANCE.holdInitialize();
        var file = myFixture.configureByText("main.foo", "");
        var connected = LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null);
        try {
            PlatformTestUtil.waitWithEventsDispatching("the server must be initializing",
                    () -> MockLanguageServer.INSTANCE.getNotifications().contains("initialize"), 30);
            // LSP: until the server has answered initialize, the client must send it no notification
            MockLanguageServerDefinition.getLastLanguageClient().triggerChangeConfiguration();
        } finally {
            initialize.complete(null);
        }
        connected.get(30, TimeUnit.SECONDS);

        PlatformTestUtil.waitWithEventsDispatching("the configuration must be sent",
                () -> MockLanguageServer.INSTANCE.getNotifications().contains("didChangeConfiguration"), 30);
        assertEquals(List.of("initialize", "initialized", "didChangeConfiguration"),
                received("initialize", "initialized", "didChangeConfiguration"));
    }

    public void testAWorkspaceFolderIsAnnouncedBeforeTheDocumentThatFindsIt() throws Exception {
        var main = myFixture.configureByText("main.foo", "");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(main, null, null).get(30, TimeUnit.SECONDS).get(0).getServerWrapper();
        var other = myFixture.addFileToProject("other/other.foo", "");
        var folder = new WorkspaceFolder(LSPIJUtils.toUriAsString(other.getVirtualFile().getParent()), "other");
        wrapper.getClientFeatures().setWorkspaceFolderFeature(new LSPWorkspaceFolderFeature() {
            @Override
            public @Nullable WorkspaceFolder computeWorkspaceFolderToNotify(@NotNull VirtualFile file) {
                return "other.foo".equals(file.getName()) ? folder : null;
            }
        });

        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(other, null, null).get(30, TimeUnit.SECONDS);

        PlatformTestUtil.waitWithEventsDispatching("the document and its folder must be sent",
                () -> received("didChangeWorkspaceFolders", "didOpen:other.foo").size() == 2, 30);
        assertEquals(List.of("didChangeWorkspaceFolders", "didOpen:other.foo"),
                received("didChangeWorkspaceFolders", "didOpen:other.foo"));
    }

    private static List<String> received(String... kinds) {
        var wanted = List.of(kinds);
        return MockLanguageServer.INSTANCE.getNotifications().stream().filter(wanted::contains).toList();
    }
}

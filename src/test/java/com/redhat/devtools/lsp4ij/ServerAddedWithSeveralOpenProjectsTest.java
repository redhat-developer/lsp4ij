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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * A language server definition that is added while several projects are open starts, in each project, for the open
 * files of that project only. Each project hears of the definition, and a file of one project must not get a language
 * server of another one, with the root and the workspace folders of that other project, as a second instance next to
 * the one of its own project.
 */
public class ServerAddedWithSeveralOpenProjectsTest extends LSPCodeInsightFixtureTestCase {

    private Disposable otherProjectLifetime;
    private Project otherProject;
    private MockLanguageServerDefinition addedServer;

    public ServerAddedWithSeveralOpenProjectsTest() {
        // The language server of the fixture serves none of the files of the tests
        super("*.unused");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Closed before the fixture's tear down, which checks that nothing of the test, editors included, is left open
        otherProjectLifetime = Disposer.newDisposable();
        otherProject = PlatformTestUtil.loadAndOpenProject(
                FileUtil.createTempDirectory("otherProject", null, true).toPath(), otherProjectLifetime);
        // Both projects use language servers already, as when a file of each has been opened
        LanguageServiceAccessor.getInstance(myFixture.getProject());
        LanguageServiceAccessor.getInstance(otherProject);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (addedServer != null) {
                addedServer.getServer().waitBeforeTearDown();
                LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), addedServer);
            }
        } finally {
            try {
                Disposer.dispose(otherProjectLifetime);
            } finally {
                super.tearDown();
            }
        }
    }

    public void testTheAddedServerDoesNotStartInAnotherProjectForAFileOfThisOne() {
        VirtualFile file = myFixture.configureByText("open.ts", "const value = 1;").getVirtualFile();

        addServer();

        waitUntilConnected(myFixture.getProject(), file, "this project's language server must open the file of this project");
        // The connections that the definition's addition asked for have chosen their language servers
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        assertEquals("the other project, which has no open file, must not start the added language server",
                List.of(), getAddedServers(otherProject));
    }

    public void testTheOpenFileOfAnotherProjectGetsTheAddedServerOfThatProject() throws IOException {
        VirtualFile file = myFixture.getTempDirFixture().createFile("other.ts", "const value = 1;");
        FileEditorManager.getInstance(otherProject).openFile(file, true);

        addServer();

        waitUntilConnected(otherProject, file, "the other project's language server must open the file of the other project");
        // The connections that the definition's addition asked for have chosen their language servers
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        assertEquals("this project, which has no open file, must not start the added language server",
                List.of(), getAddedServers(myFixture.getProject()));
    }

    private void addServer() {
        addedServer = new MockLanguageServerDefinition("added-server", false);
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), addedServer,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.ts"), null)));
    }

    /**
     * Waits until a language server of the added definition in the given project has opened the given file.
     */
    private void waitUntilConnected(@NotNull Project project, @NotNull VirtualFile file, @NotNull String message) {
        PlatformTestUtil.waitWithEventsDispatching(message, () -> getAddedServers(project).stream()
                .anyMatch(server -> server.isConnectedTo(server.toUri(file))), 10);
    }

    private @NotNull List<LanguageServerWrapper> getAddedServers(@NotNull Project project) {
        return LanguageServiceAccessor.getInstance(project).getStartedServers().stream()
                .filter(server -> server.getServerDefinition() == addedServer)
                .toList();
    }
}

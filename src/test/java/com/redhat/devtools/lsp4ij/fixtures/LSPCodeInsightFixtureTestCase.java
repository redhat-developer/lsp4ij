/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.fixtures;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Base class test case to test LSP feature.
 */
public abstract class LSPCodeInsightFixtureTestCase extends UsefulTestCase {

    /**
     * The bound, in seconds, of a wait which includes the start of a language server: far longer than a slow headless
     * runner takes, the first start in the JVM included, such as a start of 6 s, which a bound of 5 s failed. Each
     * test of a fixture waits for it once, so a start which never ends costs each of them this long.
     */
    protected static final int SERVER_START_TIMEOUT_SECONDS = 30;

    /**
     * The bound, in seconds, of a wait for work in this JVM which doesn't start a language server, such as a request
     * to the mock language server once it has started, or the daemon.
     */
    protected static final int IN_MEMORY_TIMEOUT_SECONDS = 10;

    private final String[] fileNamePatterns;
    private String languageId;
    private boolean clientConfigurable = false;

    protected LSPCodeInsightTestFixture myFixture;
    private MockLanguageServerDefinition serverDefinition;

    public LSPCodeInsightFixtureTestCase(String... fileNamePatterns) {
        this.fileNamePatterns = fileNamePatterns;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        myFixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        myFixture.setUp();
        registerServer();
    }

    @Override
    protected void tearDown() throws Exception {
        MockLanguageServer.INSTANCE.waitBeforeTearDown();
        unregisterServer();
        try {
            myFixture.tearDown();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            myFixture = null;
            super.tearDown();
        }
    }

    public void setLanguageId(String languageId) {
        this.languageId = languageId;
    }

    public void setClientConfigurable(boolean clientConfigurable) {
        this.clientConfigurable = clientConfigurable;
    }

    private void registerServer() {
        serverDefinition = createServerDefinition(clientConfigurable);
        List<ServerMappingSettings> mappings = List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of(fileNamePatterns), languageId));
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), serverDefinition, mappings);
    }

    /**
     * Creates the definition of the mock language server that the files of the test map to, which a test overrides to
     * change what the client features of the language server do.
     *
     * @param clientConfigurable whether the client of the language server can be configured.
     * @return the definition.
     */
    protected @NotNull MockLanguageServerDefinition createServerDefinition(boolean clientConfigurable) {
        return new MockLanguageServerDefinition(clientConfigurable, true);
    }

    private void unregisterServer() {
        LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), serverDefinition);
    }

    /**
     * Wait for DaemonCodeAnalyzer restart has finished, while the UI thread dispatches its events, which the daemon
     * may need to end.
     */
    protected void waitForDaemonCodeAnalyzerFinished() {
        var daemon = (DaemonCodeAnalyzerImpl) DaemonCodeAnalyzer.getInstance(myFixture.getProject());
        PlatformTestUtil.waitWithEventsDispatching("the daemon must finish its restart", () -> !daemon.isRunning(),
                IN_MEMORY_TIMEOUT_SECONDS);
    }

    /**
     * Waits until the language servers of the given file have started, and returns them, or fails the test with an
     * error which says for which file the start didn't end, and the status of each language server, and which keeps
     * the cause.
     *
     * @param file the file whose language servers the test waits for.
     * @return the language servers of the file.
     */
    protected static @NotNull List<LanguageServerItem> awaitLanguageServers(@NotNull PsiFile file) {
        var lookup = LanguageServiceAccessor.getInstance(file.getProject()).getLanguageServers(file, null, null);
        try {
            return lookup.get(SERVER_START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("The language servers of " + pathOf(file) + " must start within "
                    + SERVER_START_TIMEOUT_SECONDS + " s, but the lookup of getLanguageServers is still pending, with "
                    + serverStatuses(file.getProject()), e);
        } catch (ExecutionException e) {
            throw new AssertionError("The language servers of " + pathOf(file) + " must start, but the lookup of "
                    + "getLanguageServers failed: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("The wait for the start of the language servers of " + pathOf(file)
                    + " was interrupted", e);
        }
    }

    private static @NotNull String pathOf(@NotNull PsiFile file) {
        var virtualFile = file.getVirtualFile();
        return virtualFile != null ? virtualFile.getPath() : file.getName();
    }

    /**
     * Returns the status of each language server of the project, as "id: status", which tells whether a language
     * server which didn't start in time is still starting, has stopped or has none.
     */
    private static @NotNull String serverStatuses(@NotNull Project project) {
        var servers = LanguageServiceAccessor.getInstance(project).getStartedServers();
        return servers.isEmpty() ? "no language server" : servers.stream()
                .map(server -> server.getServerDefinition().getId() + ": " + server.getServerStatus())
                .sorted()
                .collect(Collectors.joining(", "));
    }
}

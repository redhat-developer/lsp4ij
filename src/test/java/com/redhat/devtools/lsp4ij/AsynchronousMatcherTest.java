/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.ServerFileNamePatternMapping;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A language server definition that matches a file only through a mapping that is matched asynchronously.
 */
public class AsynchronousMatcherTest extends LSPCodeInsightFixtureTestCase {

    private LanguageServerDefinition definition;

    public AsynchronousMatcherTest() {
        super("*.foo", "*.both");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        definition = LanguageServersRegistry.getInstance().getServerDefinition("mock-server-id");
        registerAsynchronousMapping(definition, "*.async", file -> CompletableFuture.completedFuture(true));
    }

    private static void registerAsynchronousMapping(@NotNull LanguageServerDefinition definition,
                                                    @NotNull String fileNamePattern,
                                                    @NotNull Function<VirtualFile, CompletableFuture<Boolean>> matches) {
        LanguageServersRegistry.getInstance().registerAssociation(definition,
                new ServerFileNamePatternMapping(List.of(fileNamePattern), definition.getId(), null, new DocumentMatcher() {
                    @Override
                    public boolean match(@NotNull VirtualFile file, @NotNull Project project) {
                        throw new AssertionError("The mapping is matched asynchronously");
                    }

                    @Override
                    public boolean shouldBeMatchedAsynchronously(@NotNull Project project) {
                        return true;
                    }

                    @Override
                    public @NotNull CompletableFuture<Boolean> matchAsync(@NotNull VirtualFile file, @NotNull Project project) {
                        return matches.apply(file);
                    }
                }));
    }

    /**
     * Registers another definition, with a language server of its own, which matches test.both asynchronously, and
     * which the fixture's definition matches synchronously.
     */
    private LanguageServerDefinition registerOtherDefinition() {
        var other = new MockLanguageServerDefinition("other-mock-server-id", false);
        var project = myFixture.getProject();
        LanguageServersRegistry.getInstance().addServerDefinition(project, other, List.of());
        Disposer.register(getTestRootDisposable(),
                () -> LanguageServersRegistry.getInstance().removeServerDefinition(project, other));
        registerAsynchronousMapping(other, "*.both", file -> CompletableFuture.completedFuture(file.getName().equals("test.both")));
        return other;
    }

    public void testARequestForTheDefinitionStartsItsLanguageServer() throws Exception {
        // Not opened in an editor, which would connect it
        PsiFile file = myFixture.addFileToProject("test.async", "content");
        var accessor = LanguageServiceAccessor.getInstance(myFixture.getProject());
        assertFalse(accessor.getStartedServers().stream()
                .anyMatch(server -> server.getServerDefinition() == definition && server.isActive()));

        assertSize(1, accessor.getLanguageServers(file, null, null, definition).get(10, TimeUnit.SECONDS));
        // As the request without a definition
        assertSize(1, accessor.getLanguageServers(file, null, null).get(10, TimeUnit.SECONDS));
    }

    public void testARequestForTheDefinitionWaitsForAMatcherThatAnswersLater() throws Exception {
        var answer = new CompletableFuture<Boolean>();
        registerAsynchronousMapping(definition, "*.later", file -> answer);
        PsiFile file = myFixture.configureByText("test.later", "content");

        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null, definition);
        assertFalse("The request must wait for the matcher", servers.isDone());
        answer.complete(true);

        assertSize(1, servers.get(10, TimeUnit.SECONDS));
    }

    public void testARequestForTheDefinitionGetsNoOtherDefinitionThatMatchesTheFileAsynchronously() throws Exception {
        var other = registerOtherDefinition();
        PsiFile file = myFixture.configureByText("test.both", "content");
        var accessor = LanguageServiceAccessor.getInstance(myFixture.getProject());
        assertSize(2, accessor.getLanguageServers(file, null, null).get(10, TimeUnit.SECONDS));

        var servers = accessor.getLanguageServers(file, null, null, definition).get(10, TimeUnit.SECONDS);

        assertEquals(List.of(definition), servers.stream().map(server -> server.getServerWrapper().getServerDefinition()).toList());
        assertFalse(servers.stream().anyMatch(server -> server.getServerWrapper().getServerDefinition() == other));
    }

    public void testARequestForADefinitionWhoseAsynchronousMatcherRejectsTheFileGetsNone() throws Exception {
        var other = registerOtherDefinition();
        PsiFile file = myFixture.configureByText("other.both", "content");

        assertEmpty(LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null, other)
                .get(10, TimeUnit.SECONDS));
    }

    public void testARestartConnectsTheOpenFileAgain() throws Exception {
        var opened = expectDidOpenOf("test.async");
        PsiFile file = myFixture.configureByText("test.async", "content");
        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(10, TimeUnit.SECONDS);
        assertSize(1, servers);
        var wrapper = servers.get(0).getServerWrapper();
        PlatformTestUtil.waitWithEventsDispatching("The language server must get the file's didOpen", opened::isDone, 10);

        var reopened = expectDidOpenOf("test.async");
        wrapper.restart();

        PlatformTestUtil.waitWithEventsDispatching("The restarted language server must get the file's didOpen again",
                () -> wrapper.getServerStatus() == ServerStatus.started && reopened.isDone(), 10);
    }

    /**
     * Expects the next didOpen of the file with the given name, which the mock language server completes the returned
     * future with.
     */
    private static CompletableFuture<DidOpenTextDocumentParams> expectDidOpenOf(@NotNull String fileName) {
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened,
                params -> params.getTextDocument().getUri().endsWith("/" + fileName));
        return opened;
    }
}

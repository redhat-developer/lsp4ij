/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.ui;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The banner above the editor of a file which IntelliJ opens as plain text or with TextMate suggests the bundled
 * templates which match the file.
 */
public class LanguageServerSuggestionEditorNotificationProviderTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // The bundled templates are read from the jar of LSP4IJ in the test sandbox, which is outside the roots that a
        // test may read when the checkout isn't in the user's home directory
        String templates = PathManager.getResourceRoot(LanguageServerTemplateManager.class, "/templates/lsp");
        assertNotNull(templates);
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), templates);
    }

    /**
     * erlang_ls is discontinued: its template says {@code "promotable": false}, so the banner of an Erlang file
     * suggests ELP, and not erlang-ls, which the New Language Server dialog still lists.
     */
    public void testTheBannerOfAnErlangFileSuggestsElpAndNotTheDiscontinuedErlangLs() {
        var file = erlangFile();

        var suggestion = new LanguageServerSuggestionEditorNotificationProvider().suggest(getProject(), file);

        assertNotNull("the banner of an Erlang file should suggest a template", suggestion);
        assertEquals(List.of("erlang-elp"), templateIds(suggestion));
        assertContainsElements(suggestion.patterns(), "*.erl");
        assertNotNull("the dialog still lists erlang-ls",
                LanguageServerTemplateManager.getInstance().findTemplateById("erlang-ls"));
    }

    /**
     * A user of erlang-ls, whose language server maps the Erlang files, isn't offered ELP or another template for them.
     */
    public void testNoTemplateIsSuggestedForAFileThatALanguageServerFromATemplateMaps() {
        var file = erlangFile();
        addServer("erlang-ls", "*.erl");

        assertNull(new LanguageServerSuggestionEditorNotificationProvider().suggest(getProject(), file));
    }

    public void testNoTemplateIsSuggestedForAFileThatALanguageServerWithoutATemplateMaps() {
        var file = erlangFile();
        addServer(null, "*.erl");

        assertNull(new LanguageServerSuggestionEditorNotificationProvider().suggest(getProject(), file));
    }

    /**
     * A disabled language server still maps the file: the user turned it off, and a template isn't suggested instead.
     */
    public void testNoTemplateIsSuggestedForAFileThatADisabledLanguageServerMaps() {
        var file = erlangFile();
        addServer(null, "*.erl").setEnabled(false, getProject());

        assertNull(new LanguageServerSuggestionEditorNotificationProvider().suggest(getProject(), file));
    }

    public void testTheTemplatesAreSuggestedWhileTheLanguageServersMapOtherFiles() {
        var file = erlangFile();
        addServer(null, "*.other");

        var suggestion = new LanguageServerSuggestionEditorNotificationProvider().suggest(getProject(), file);

        assertNotNull("no language server maps hello.erl", suggestion);
        assertEquals(List.of("erlang-elp"), templateIds(suggestion));
    }

    /**
     * A language server created from a template, whose user removed the mapping of a file from it, keeps its template
     * from being suggested again for that file.
     */
    public void testATemplateIsNotSuggestedAgainForAFileThatItsLanguageServerDoesntMap() {
        var file = erlangFile();
        addServer("erlang-elp", "*.hrl");

        assertNull(new LanguageServerSuggestionEditorNotificationProvider().suggest(getProject(), file));
    }

    /**
     * Adds a language server of the user, created from the template of the given id, or without one, which maps the
     * files of the given pattern, until the test ends.
     */
    private @NotNull UserDefinedLanguageServerDefinition addServer(@Nullable String templateId, @NotNull String pattern) {
        var definition = new UserDefinedLanguageServerDefinition(UUID.randomUUID().toString(), templateId,
                "My Erlang server", null, null, "my-erlang-server", null, Map.of(), false, null, false, null, null,
                null, null, null, null);
        var registry = LanguageServersRegistry.getInstance();
        registry.addServerDefinition(getProject(), definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of(pattern), "erlang")));
        Disposer.register(getTestRootDisposable(), () -> registry.removeServerDefinition(getProject(), definition));
        return definition;
    }

    /**
     * Adds an Erlang file to the project, which IntelliJ opens as plain text or with TextMate, as without a plugin for
     * Erlang.
     */
    private @NotNull VirtualFile erlangFile() {
        PsiFile file = myFixture.addFileToProject("src/hello.erl", """
                -module(hello).
                -export([hello/0]).

                hello() -> ok.
                """);
        assertTrue("hello.erl should be plain text or TextMate, not " + file.getLanguage(),
                LSPIJEditorUtils.isPlainTextFile(file) || LSPIJEditorUtils.isTextMateFile(file));
        return file.getVirtualFile();
    }

    private static @NotNull List<String> templateIds(
            @NotNull LanguageServerSuggestionEditorNotificationProvider.Suggestion suggestion) {
        return suggestion.templates().stream().map(LanguageServerTemplate::getId).toList();
    }
}

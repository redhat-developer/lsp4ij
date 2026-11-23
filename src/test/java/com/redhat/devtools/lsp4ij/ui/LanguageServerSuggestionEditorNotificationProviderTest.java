/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.ui;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.rename;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies that native rename checks the server's response against the document before applying it.
 */
public class RenameVersionTest extends LSPCodeInsightFixtureTestCase {

    private final AtomicReference<String> error = new AtomicReference<>();
    private LSPRenameUnitTestMode.LSPRenameUnitTestModeHandler previousHandler;

    public RenameVersionTest() {
        super("*.foo");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        previousHandler = LSPRenameUnitTestMode.get();
        LSPRenameUnitTestMode.set(new LSPRenameUnitTestMode.LSPRenameUnitTestModeHandler() {
            @Override
            public void showErrorHint(String message) {
                error.set(message);
            }

            @Override
            public void showRenameRefactoringDialog(RenameParams params) {
                fail("The test invokes rename directly");
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            LSPRenameUnitTestMode.set(previousHandler);
        } finally {
            super.tearDown();
        }
    }

    private List<LanguageServerItem> open() throws Exception {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setRenameProvider(true);
            return capabilities;
        });
        var file = myFixture.configureByText("test.foo", "before");
        return LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
    }

    private static LSPRenameParams renameParams(String uri, List<LanguageServerItem> servers) {
        var params = new LSPRenameParams(new TextDocumentIdentifier(uri), new Position(0, 0), servers);
        params.setNewName("after");
        return params;
    }

    private static TextEdit replacement() {
        return new TextEdit(new Range(new Position(0, 0), new Position(0, 6)), "after");
    }

    public void testOutdatedRenameIsRefused() throws Exception {
        var servers = open();
        var file = myFixture.getFile();
        String uri = servers.get(0).getServerWrapper().toUri(file.getVirtualFile()).toString();
        // The opened document is at version 1; this response describes an older version.
        var text = new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri, 0),
                List.of(Either.forLeft(replacement())));
        MockLanguageServer.INSTANCE.setRenameProcessor(params -> new WorkspaceEdit(List.of(Either.forLeft(text))));

        LSPRenameRefactoringDialog.doRename(renameParams(uri, servers), file, myFixture.getEditor());

        assertEquals("before", myFixture.getEditor().getDocument().getText());
        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.version.changed", uri), error.get());
    }

    public void testTypingWhileRenameIsPendingRefusesUnversionedReply() throws Exception {
        var servers = open();
        var file = myFixture.getFile();
        var editor = myFixture.getEditor();
        String uri = servers.get(0).getServerWrapper().toUri(file.getVirtualFile()).toString();
        // The user types while the server computes the rename. In tests the rename progress is
        // modal and keeps dispatching events: read its modality on the EDT, then type in it.
        MockLanguageServer.INSTANCE.setRenameProcessor(params -> {
            var typed = new CompletableFuture<Void>();
            var application = ApplicationManager.getApplication();
            application.invokeLater(() -> application.invokeLater(() -> {
                WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () ->
                        editor.getDocument().setText("new typing"));
                typed.complete(null);
            }, ModalityState.current()), ModalityState.any());
            typed.orTimeout(30, TimeUnit.SECONDS).join();
            // A server may answer without versions; this reply describes the text before the typing.
            return new WorkspaceEdit(Map.of(uri, List.of(replacement())));
        });

        LSPRenameRefactoringDialog.doRename(renameParams(uri, servers), file, editor);

        assertEquals("new typing", editor.getDocument().getText());
        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.document.changed", uri), error.get());
    }
}

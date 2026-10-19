/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.rename;

import com.redhat.devtools.lsp4ij.LanguageServerBundle;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies that native rename checks versions in the server's response before applying it.
 */
public class RenameVersionTest extends LSPCodeInsightFixtureTestCase {

    public RenameVersionTest() {
        super("*.foo");
    }

    public void testOutdatedRenameIsRefused() throws Exception {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setRenameProvider(true);
            return capabilities;
        });
        var file = myFixture.configureByText("test.foo", "before");
        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
        String uri = servers.get(0).getServerWrapper().toUri(file.getVirtualFile()).toString();
        // The opened document is at version 1; this response describes an older version.
        var text = new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri, 0),
                List.of(Either.forLeft(new TextEdit(new Range(new Position(0, 0), new Position(0, 6)), "after"))));
        MockLanguageServer.INSTANCE.setRenameProcessor(params -> new WorkspaceEdit(List.of(Either.forLeft(text))));

        var error = new AtomicReference<String>();
        var previousHandler = LSPRenameUnitTestMode.get();
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
        try {
            var params = new LSPRenameParams(new TextDocumentIdentifier(uri), new Position(0, 0), servers);
            params.setNewName("after");
            LSPRenameRefactoringDialog.doRename(params, file, myFixture.getEditor());

            assertEquals("before", myFixture.getEditor().getDocument().getText());
            assertNotNull(error.get());
            assertEquals(LanguageServerBundle.message("lsp.workspace.edit.version.changed", uri), error.get());
        } finally {
            LSPRenameUnitTestMode.set(previousHandler);
        }
    }
}

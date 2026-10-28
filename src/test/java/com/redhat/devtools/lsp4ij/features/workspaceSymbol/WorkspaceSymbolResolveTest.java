/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.workspaceSymbol;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.internal.capabilities.ClientCapabilitiesFactory;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolLocation;
import org.eclipse.lsp4j.WorkspaceSymbolOptions;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A language server which supports 'workspaceSymbol/resolve' can return a workspace symbol whose location has a URI
 * but no range, as the client declares that it resolves the range. The range is then resolved when the symbol is
 * opened.
 */
public class WorkspaceSymbolResolveTest extends LSPCodeInsightFixtureTestCase {

    private static final String TEXT = "first line\nsecond line\nthe symbol\n";
    private static final Range SYMBOL_RANGE = new Range(new Position(2, 4), new Position(2, 10));

    public WorkspaceSymbolResolveTest() {
        super("*.foo");
    }

    public void testASymbolWithoutARangeIsResolvedWhenItIsOpened() throws Exception {
        serverResolvesWorkspaceSymbols(true);
        var server = open();
        MockLanguageServer.INSTANCE.getWorkspaceService().setWorkspaceSymbolResolveProcessor(symbol -> {
            var resolved = new WorkspaceSymbol(symbol.getName(), symbol.getKind(),
                    Either.forLeft(new Location(symbol.getLocation().getRight().getUri(), SYMBOL_RANGE)));
            return CompletableFuture.completedFuture(resolved);
        });
        var item = new WorkspaceSymbolData(symbolWithoutRange(server), server, myFixture.getProject());
        assertNull("the symbol has no range before it is resolved", item.getTextRange());

        item.navigate(true);

        List<WorkspaceSymbol> requests = MockLanguageServer.INSTANCE.getWorkspaceService().getWorkspaceSymbolResolveRequests();
        assertEquals(1, requests.size());
        assertEquals("symbol", requests.get(0).getName());
        assertEquals(new LogicalPosition(2, 4), caret());
        assertEquals("the resolved range is kept", TEXT.indexOf("symbol"), item.getTextRange().getStartOffset());
    }

    public void testASymbolIsNotResolvedByAServerWhichCannotResolve() throws Exception {
        serverResolvesWorkspaceSymbols(false);
        var server = open();
        moveCaretToTheEnd();

        new WorkspaceSymbolData(symbolWithoutRange(server), server, myFixture.getProject()).navigate(true);

        assertEmpty(MockLanguageServer.INSTANCE.getWorkspaceService().getWorkspaceSymbolResolveRequests());
        assertEquals("the file opens without a position, as before", new LogicalPosition(3, 0), caret());
    }

    public void testASymbolWhoseResolveFailsOpensTheFileWithoutAPosition() throws Exception {
        serverResolvesWorkspaceSymbols(true);
        var server = open();
        moveCaretToTheEnd();
        MockLanguageServer.INSTANCE.getWorkspaceService().setWorkspaceSymbolResolveProcessor(symbol ->
                CompletableFuture.failedFuture(new ResponseErrorException(
                        new ResponseError(ResponseErrorCode.InvalidParams, "The symbol is stale", null))));

        new WorkspaceSymbolData(symbolWithoutRange(server), server, myFixture.getProject()).navigate(true);

        assertEquals(1, MockLanguageServer.INSTANCE.getWorkspaceService().getWorkspaceSymbolResolveRequests().size());
        assertEquals(new LogicalPosition(3, 0), caret());
    }

    public void testTheClientDeclaresThatItResolvesTheRange() {
        var resolveSupport = ClientCapabilitiesFactory.create(null).getWorkspace().getSymbol().getResolveSupport();
        assertNotNull(resolveSupport);
        assertEquals(List.of("location.range"), resolveSupport.getProperties());
    }

    private static void serverResolvesWorkspaceSymbols(boolean resolveProvider) {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            var options = new WorkspaceSymbolOptions();
            options.setResolveProvider(resolveProvider);
            capabilities.setWorkspaceSymbolProvider(options);
            return capabilities;
        });
    }

    private LanguageServerItem open() throws Exception {
        var file = myFixture.configureByText("main.foo", TEXT);
        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS);
        assertEquals(1, servers.size());
        return servers.get(0);
    }

    private WorkspaceSymbol symbolWithoutRange(LanguageServerItem server) {
        VirtualFile file = myFixture.getFile().getVirtualFile();
        String uri = FileUriSupport.toString(file, server.getClientFeatures());
        return new WorkspaceSymbol("symbol", SymbolKind.Variable, Either.forRight(new WorkspaceSymbolLocation(uri)));
    }

    private void moveCaretToTheEnd() {
        myFixture.getEditor().getCaretModel().moveToOffset(TEXT.length());
    }

    private LogicalPosition caret() {
        var editor = FileEditorManager.getInstance(myFixture.getProject()).getSelectedTextEditor();
        assertNotNull(editor);
        return editor.getCaretModel().getLogicalPosition();
    }
}

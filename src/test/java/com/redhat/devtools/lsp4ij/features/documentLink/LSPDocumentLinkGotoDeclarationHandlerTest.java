/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentLink;

import com.google.gson.JsonPrimitive;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Ctrl+Click on a document link opens its target. A language server which supports 'documentLink/resolve' can leave
 * the target of a link to it: a clicked link without a target is then resolved, and its resolved target opens.
 */
public class LSPDocumentLinkGotoDeclarationHandlerTest extends LSPCodeInsightFixtureTestCase {

    private static final String TEXT = "see https://example.com/ here";
    private static final String URL = "https://example.com/";
    // "https://example.com/" in TEXT
    private static final Range URL_RANGE = new Range(new Position(0, 4), new Position(0, 24));
    // in the URL
    private static final int CLICK = 10;

    public LSPDocumentLinkGotoDeclarationHandlerTest() {
        super("*.foo");
    }

    public void testAClickedLinkWithoutATargetIsResolved() throws Exception {
        serverResolvesLinks(true);
        MockLanguageServer.INSTANCE.setDocumentLinks(List.of(linkWithoutTarget()));
        MockLanguageServer.INSTANCE.getTextDocumentService().setDocumentLinkResolveProcessor(link -> {
            var resolved = new DocumentLink(link.getRange(), URL);
            resolved.setData(link.getData());
            return CompletableFuture.completedFuture(resolved);
        });
        open();

        var targets = targetsAtClick();

        assertEquals(1, targets.length);
        assertInstanceOf(targets[0], LSPDocumentLinkPsiElement.class);
        assertEquals(URL, ((LSPDocumentLinkPsiElement) targets[0]).getLocationString());
        List<DocumentLink> requests = MockLanguageServer.INSTANCE.getTextDocumentService().getDocumentLinkResolveRequests();
        assertEquals(1, requests.size());
        assertEquals(URL_RANGE, requests.get(0).getRange());
        assertEquals("the link is resolved with the data the server gave it", new JsonPrimitive("link-1"), requests.get(0).getData());
    }

    public void testALinkWithoutATargetIsNotResolvedByAServerWhichCannotResolve() throws Exception {
        serverResolvesLinks(false);
        MockLanguageServer.INSTANCE.setDocumentLinks(List.of(linkWithoutTarget()));
        open();

        assertEquals(0, targetsAtClick().length);
        assertEmpty(MockLanguageServer.INSTANCE.getTextDocumentService().getDocumentLinkResolveRequests());
    }

    public void testALinkWithATargetIsNotResolved() throws Exception {
        serverResolvesLinks(true);
        MockLanguageServer.INSTANCE.setDocumentLinks(List.of(new DocumentLink(URL_RANGE, URL)));
        open();

        var targets = targetsAtClick();

        assertEquals(1, targets.length);
        assertEquals(URL, ((LSPDocumentLinkPsiElement) targets[0]).getLocationString());
        assertEmpty(MockLanguageServer.INSTANCE.getTextDocumentService().getDocumentLinkResolveRequests());
    }

    public void testALinkWhoseResolveFailsOpensNothing() throws Exception {
        serverResolvesLinks(true);
        MockLanguageServer.INSTANCE.setDocumentLinks(List.of(linkWithoutTarget()));
        MockLanguageServer.INSTANCE.getTextDocumentService().setDocumentLinkResolveProcessor(link ->
                CompletableFuture.failedFuture(new ResponseErrorException(
                        new ResponseError(ResponseErrorCode.InvalidParams, "The link is stale", null))));
        open();

        assertEquals(0, targetsAtClick().length);
        assertEquals(1, MockLanguageServer.INSTANCE.getTextDocumentService().getDocumentLinkResolveRequests().size());
    }

    private static DocumentLink linkWithoutTarget() {
        var link = new DocumentLink(URL_RANGE);
        link.setData("link-1");
        return link;
    }

    private static void serverResolvesLinks(boolean resolveProvider) {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setDocumentLinkProvider(new DocumentLinkOptions(resolveProvider));
            return capabilities;
        });
    }

    private void open() throws Exception {
        var file = myFixture.configureByText("main.foo", TEXT);
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS);
    }

    private PsiElement[] targetsAtClick() {
        var file = myFixture.getFile();
        PsiElement[] targets = new LSPDocumentLinkGotoDeclarationHandler()
                .getGotoDeclarationTargets(file.findElementAt(CLICK), CLICK, myFixture.getEditor());
        return targets != null ? targets : PsiElement.EMPTY_ARRAY;
    }
}

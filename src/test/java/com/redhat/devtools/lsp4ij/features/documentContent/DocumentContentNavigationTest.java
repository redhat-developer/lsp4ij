/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentContent;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.navigation.LSPGotoDeclarationHandler;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.TextDocumentContentRefreshParams;
import org.eclipse.lsp4j.TextDocumentContentRegistrationOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A navigation target whose URI has a scheme the server serves with workspace/textDocumentContent opens
 * read-only with the text of the server, is open on the server under that URI, follows the refreshes of the
 * server, and closes when the server stops.
 */
public class DocumentContentNavigationTest extends LSPCodeInsightFixtureTestCase {

    private static final String LIBRARY = "lsp-source:///stdlib/Library.foo";
    private static final String LIBRARY_TEXT = "module stdlib;\nclass Library {}";
    // "Library" on the second line of LIBRARY_TEXT
    private static final Range LIBRARY_NAME = new Range(new Position(1, 6), new Position(1, 13));

    public DocumentContentNavigationTest() {
        super("*.foo");
    }

    public void testGoToDefinitionOpensTheContentOfTheServer() throws Exception {
        serverServes("lsp-source");
        open();

        var targets = definitionTargets();

        assertEquals(1, targets.length);
        var file = targets[0].getContainingFile().getVirtualFile();
        assertInstanceOf(file, DocumentContentFile.class);
        assertEquals(LIBRARY, file.getUrl());
        assertFalse(file.isWritable());
        assertEquals("Library", targets[0].getText());
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        MockLanguageServer.INSTANCE.setDidOpenCallback(opened);

        ((Navigatable) targets[0]).navigate(true);

        var manager = FileEditorManager.getInstance(myFixture.getProject());
        PlatformTestUtil.waitWithEventsDispatching("the target must open", () -> manager.isFileOpen(file), 10);
        var editor = manager.getSelectedTextEditor();
        assertNotNull(editor);
        assertSame(file, editor.getVirtualFile());
        assertEquals(LIBRARY_TEXT, editor.getDocument().getText());
        assertFalse(editor.getDocument().isWritable());
        assertEquals("the caret is at the range the server gave", 21, editor.getCaretModel().getOffset());
        PlatformTestUtil.waitWithEventsDispatching("the server must be told that the document is open", opened::isDone, 10);
        assertEquals("the server knows the document by its own URI", LIBRARY, opened.get().getTextDocument().getUri());
        assertEquals(LIBRARY_TEXT, opened.get().getTextDocument().getText());
    }

    public void testTheServerCanShowItsContent() throws Exception {
        serverServes("lsp-source");
        open();

        var shown = client().showDocument(new ShowDocumentParams(LIBRARY));

        PlatformTestUtil.waitWithEventsDispatching("the server must learn whether its document is shown", shown::isDone, 10);
        assertTrue(shown.get().isSuccess());
        var editor = FileEditorManager.getInstance(myFixture.getProject()).getSelectedTextEditor();
        assertNotNull(editor);
        assertInstanceOf(editor.getVirtualFile(), DocumentContentFile.class);
        assertEquals(LIBRARY_TEXT, editor.getDocument().getText());
    }

    public void testTheServerRefreshesItsContent() throws Exception {
        serverServes("lsp-source");
        open();
        var file = definitionTargets()[0].getContainingFile().getVirtualFile();
        var documents = FileDocumentManager.getInstance();
        var document = documents.getDocument(file);
        assertNotNull(document);
        MockLanguageServer.INSTANCE.getWorkspaceService().setDocumentContent(LIBRARY, "module stdlib;\nclass Library2 {}");

        var refreshed = client().refreshTextDocumentContent(new TextDocumentContentRefreshParams(LIBRARY));

        PlatformTestUtil.waitWithEventsDispatching("the server must learn that the refresh is done", refreshed::isDone, 10);
        assertSame(document, documents.getDocument(file));
        assertEquals("module stdlib;\nclass Library2 {}", document.getText());
        assertFalse("the refreshed text is not an edit to save", documents.isDocumentUnsaved(document));
        assertSame("the next navigation finds the same file", file,
                definitionTargets()[0].getContainingFile().getVirtualFile());
    }

    public void testTheContentClosesWhenTheServerStops() throws Exception {
        serverServes("lsp-source");
        var wrapper = open();
        var target = definitionTargets()[0];
        var file = target.getContainingFile().getVirtualFile();
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        MockLanguageServer.INSTANCE.setDidOpenCallback(opened);
        ((Navigatable) target).navigate(true);
        var manager = FileEditorManager.getInstance(myFixture.getProject());
        PlatformTestUtil.waitWithEventsDispatching("the target must open", () -> manager.isFileOpen(file), 10);
        // stopped once the document is open on the server: a stop while a document is still being connected can
        // deadlock, since connectDocument reaches the wrapper's monitor under documentsLock and stop() takes them in
        // the other order
        PlatformTestUtil.waitWithEventsDispatching("the server must be told that the document is open", opened::isDone, 10);

        wrapper.stop();

        PlatformTestUtil.waitWithEventsDispatching("the content of a stopped server must close",
                () -> !manager.isFileOpen(file) && !file.isValid(), 10);
    }

    public void testAnotherSchemeIsNotRequested() throws Exception {
        serverServes("other");
        open();

        assertEquals(0, definitionTargets().length);
        assertFalse(MockLanguageServer.INSTANCE.getNotifications().contains("textDocumentContent"));
    }

    private static void serverServes(String scheme) {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            var workspace = new WorkspaceServerCapabilities();
            workspace.setTextDocumentContent(new TextDocumentContentRegistrationOptions(List.of(scheme)));
            capabilities.setWorkspace(workspace);
            return capabilities;
        });
        MockLanguageServer.INSTANCE.getWorkspaceService().setDocumentContent(LIBRARY, LIBRARY_TEXT);
        MockLanguageServer.INSTANCE.setDefinition(List.of(new Location(LIBRARY, LIBRARY_NAME)));
    }

    private LanguageServerWrapper open() throws Exception {
        var file = myFixture.configureByText("main.foo", "Library x;");
        return LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS).get(0).getServerWrapper();
    }

    private PsiElement[] definitionTargets() {
        var file = myFixture.getFile();
        return LSPGotoDeclarationHandler.getGotoDeclarationTargets(file.findElementAt(0), 0);
    }

    private static LanguageClient client() {
        return MockLanguageServer.INSTANCE.getRemoteProxies().get(0);
    }
}

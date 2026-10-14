/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Sends workspace/applyEdit through the mock server's real LanguageClient proxy.
 * State changes happen explicitly before each request; waits only guard against hangs.
 */
public class WorkspaceEditVersionTest extends LSPCodeInsightFixtureTestCase {

    public WorkspaceEditVersionTest() {
        super("*.foo");
    }

    private record Context(LanguageServerWrapper wrapper, URI uri, Document document) {
        DocumentContentSynchronizer synchronizer() {
            return wrapper.getOpenedDocument(uri).getSynchronizer();
        }

        int version() {
            return synchronizer().getVersion();
        }
    }

    private Context open() throws Exception {
        var file = myFixture.configureByText("test.foo", "before");
        var server = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS).get(0);
        var wrapper = server.getServerWrapper();
        return new Context(wrapper, wrapper.toUri(file.getVirtualFile()), myFixture.getEditor().getDocument());
    }

    private static TextEdit replacement() {
        return new TextEdit(new Range(new Position(0, 0), new Position(0, 6)), "after");
    }

    private static WorkspaceEdit versioned(URI uri, Integer version) {
        var text = new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri.toString(), version),
                List.of(Either.forLeft(replacement())));
        return new WorkspaceEdit(List.of(Either.forLeft(text)));
    }

    private ApplyWorkspaceEditResponse apply(WorkspaceEdit edit) {
        assertEquals(1, MockLanguageServer.INSTANCE.getRemoteProxies().size());
        var pending = MockLanguageServer.INSTANCE.getRemoteProxies().get(0)
                .applyEdit(new ApplyWorkspaceEditParams(edit));
        PlatformTestUtil.waitWithEventsDispatching("workspace/applyEdit reply", pending::isDone, 30);
        return pending.join();
    }

    private void type(Context context) {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> context.document().setText("new typing"));
    }

    public void testCurrentVersionIsApplied() throws Exception {
        var context = open();
        assertTrue(apply(versioned(context.uri(), context.version())).isApplied());
        assertEquals("after", context.document().getText());
    }

    public void testPreviousVersionCannotOverwriteNewTyping() throws Exception {
        var context = open();
        int originalVersion = context.version();
        type(context);
        assertTrue(context.version() > originalVersion);

        var response = apply(versioned(context.uri(), originalVersion));
        assertFalse(response.isApplied());
        assertNotNull(response.getFailureReason());
        assertEquals("new typing", context.document().getText());
    }

    public void testStaleSecondTargetPreventsAllChanges() throws Exception {
        var context = open();
        int originalVersion = context.version();
        type(context);
        var other = myFixture.addFileToProject("other.foo", "before");
        var first = versioned(context.wrapper().toUri(other.getVirtualFile()), null).getDocumentChanges().get(0);
        var second = versioned(context.uri(), originalVersion).getDocumentChanges().get(0);

        assertFalse(apply(new WorkspaceEdit(List.of(first, second))).isApplied());
        assertEquals("before", other.getText());
        assertEquals("new typing", context.document().getText());
    }

    public void testNullVersionIsStillApplied() throws Exception {
        var context = open();
        assertTrue(apply(versioned(context.uri(), null)).isApplied());
        assertEquals("after", context.document().getText());
    }

    public void testLegacyChangesAreStillApplied() throws Exception {
        var context = open();
        var edit = new WorkspaceEdit(Map.of(context.uri().toString(), List.of(replacement())));
        assertTrue(apply(edit).isApplied());
        assertEquals("after", context.document().getText());
    }

    public void testUnsynchronizedFileRetainsExistingBehavior() throws Exception {
        var context = open();
        var other = myFixture.addFileToProject("other.foo", "before");
        var uri = context.wrapper().toUri(other.getVirtualFile());
        assertNull(context.wrapper().getOpenedDocument(uri));

        assertTrue(apply(versioned(uri, 1)).isApplied());
        assertEquals("after", LSPIJUtils.getDocument(other.getVirtualFile()).getText());
    }

    public void testMatchingVersionCannotAuthorizeUnsynchronizedTyping() throws Exception {
        var context = open();
        int originalVersion = context.version();
        // Pause notifications explicitly instead of depending on PSI commit timing. The editor
        // stamp must prevent a matching protocol version from authorizing edits to different text.
        var synchronizer = context.synchronizer();
        context.document().removeDocumentListener(synchronizer);
        try {
            type(context);
        } finally {
            context.document().addDocumentListener(synchronizer, synchronizer);
        }
        assertEquals(originalVersion, context.version());

        assertFalse(apply(versioned(context.uri(), originalVersion)).isApplied());
        assertEquals("new typing", context.document().getText());
    }

    public void testDidOpenVersionCannotAuthorizeTextChangedSinceCapture() throws Exception {
        var context = open();
        var file = myFixture.getFile().getVirtualFile();
        context.wrapper().disconnect(context.uri(), false);
        // Model typing between capturing didOpen's text and asynchronous connection setup.
        var connectionInfo = context.wrapper().createFileConnectionInfo(file, context.document(), true);
        type(context);
        context.wrapper().connect(file, connectionInfo).get(30, TimeUnit.SECONDS);

        assertFalse(apply(versioned(context.uri(), context.version())).isApplied());
        assertEquals("new typing", context.document().getText());
    }
}

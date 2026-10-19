/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.mock.MockDocument;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupportBase;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
        return versioned(uri.toString(), version);
    }

    private static WorkspaceEdit versioned(String uri, Integer version) {
        var text = new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri, version),
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

    public void testMalformedUriIsRejected() throws Exception {
        var context = open();
        var response = apply(versioned(context.uri() + "%invalid", context.version()));

        assertFalse(response.isApplied());
        assertNotNull(response.getFailureReason());
        assertEquals("before", context.document().getText());
    }

    public void testEquivalentUriCannotBypassVersionCheck() throws Exception {
        var context = open();
        int originalVersion = context.version();
        type(context);
        String encodedUri = context.uri().toString().replace("test.foo", "%74est.foo");
        assertFalse(context.uri().equals(URI.create(encodedUri)));
        assertEquals(myFixture.getFile().getVirtualFile(), FileUriSupport.findFileByUri(encodedUri, context.wrapper().getClientFeatures()));

        var response = apply(versioned(encodedUri, originalVersion));
        assertFalse(response.isApplied());
        assertNotNull(response.getFailureReason());
        assertEquals("new typing", context.document().getText());
    }

    public void testEquivalentUriWithCurrentVersionIsApplied() throws Exception {
        var context = open();
        String encodedUri = context.uri().toString().replace("test.foo", "%74est.foo");

        assertTrue(apply(versioned(encodedUri, context.version())).isApplied());
        assertEquals("after", context.document().getText());
    }

    public void testConfiguredUriSupportIsUsedForVersionCheck() throws Exception {
        var context = open();
        int originalVersion = context.version();
        type(context);
        String alias = "test:///document.foo";
        VirtualFile file = myFixture.getFile().getVirtualFile();
        context.wrapper().getClientFeatures().setFileUriSupport(new FileUriSupportBase() {
            @Override
            public VirtualFile findFileByUri(String uri) {
                return alias.equals(uri) ? file : super.findFileByUri(uri);
            }
        });
        try {
            var response = apply(versioned(alias, originalVersion));
            assertFalse(response.isApplied());
            assertNotNull(response.getFailureReason());
            assertEquals("new typing", context.document().getText());
        } finally {
            context.wrapper().getClientFeatures().setFileUriSupport(FileUriSupport.DEFAULT);
        }
    }

    public void testBackgroundFlushCapturesStampUnderReadAccess() throws Exception {
        MockLanguageServer.INSTANCE.getInitializeResult().getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Incremental);
        var context = open();
        var readAccess = new ArrayList<Boolean>();
        var document = new MockDocument() {
            @Override
            public long getModificationStamp() {
                readAccess.add(ApplicationManager.getApplication().isReadAccessAllowed());
                return 42;
            }
        };
        var synchronizer = new DocumentContentSynchronizer(context.wrapper(), context.uri().toString(),
                myFixture.getFile().getVirtualFile(), document, null, null);
        ReadAction.run(() -> synchronizer.beforeDocumentChange(new DocumentEventImpl(context.document(),
                0, "before", "after", context.document().getModificationStamp(), true, 0, 6, 0)));

        // Observe access at the stamp read itself instead of depending on a race with a writer.
        var pending = ApplicationManager.getApplication().executeOnPooledThread(
                () -> synchronizer.flushPendingChanges().get(30, TimeUnit.SECONDS));
        PlatformTestUtil.waitWithEventsDispatching("background document flush", pending::isDone, 30);
        pending.get();

        assertEquals(List.of(true), readAccess);
    }

    public void testOnTypeFormattingFlushesBeforeLeavingWriteAction() throws Exception {
        var capabilities = MockLanguageServer.INSTANCE.getInitializeResult().getCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);
        capabilities.setDocumentOnTypeFormattingProvider(new DocumentOnTypeFormattingOptions("}"));
        MockLanguageServer.INSTANCE.setFormattingTextEdits(List.of(replacement()));
        var context = open();
        var synchronizer = context.synchronizer();
        int originalVersion = context.version();
        var support = LSPFileSupport.getSupport(myFixture.getFile()).getOnTypeFormattingSupport();

        // Queue the change explicitly to model a deferred PSI-commit notification, bypassing
        // the synchronizer's immediate dispatch in unit-test mode.
        context.document().removeDocumentListener(synchronizer);
        try {
            var pending = WriteCommandAction.writeCommandAction(myFixture.getProject()).compute(() -> {
                synchronizer.beforeDocumentChange(new DocumentEventImpl(context.document(),
                        0, "before", "after}", context.document().getModificationStamp(), true, 0, 6, 0));
                context.document().setText("after}");
                var params = new DocumentOnTypeFormattingParams(new TextDocumentIdentifier(context.uri().toString()),
                        new FormattingOptions(4, true), new Position(0, 6), "}");
                var result = support.onTypeFormatting(params);
                // The batch and stamp must be captured synchronously, before the caller awaits formatting.
                assertTrue(synchronizer.isCurrentVersion(originalVersion + 1));
                return result;
            });
            PlatformTestUtil.waitWithEventsDispatching("on-type formatting reply", pending::isDone, 30);
            assertEquals(List.of(replacement()), pending.join());
        } finally {
            context.document().addDocumentListener(synchronizer, synchronizer);
        }
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
        // Use a file without an editor. Selecting an editor schedules its own connection, which
        // can connect the file with the typed text first, so connect() would ignore this capture.
        var file = myFixture.addFileToProject("other.foo", "before").getVirtualFile();
        var document = LSPIJUtils.getDocument(file);
        // Model typing between capturing didOpen's text and asynchronous connection setup.
        var connectionInfo = context.wrapper().createFileConnectionInfo(file, document, true);
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> document.setText("new typing"));
        context.wrapper().connect(file, connectionInfo).get(30, TimeUnit.SECONDS);
        var uri = context.wrapper().toUri(file);

        assertFalse(apply(versioned(uri, context.wrapper().getOpenedDocument(uri).getSynchronizer().getVersion())).isApplied());
        assertEquals("new typing", document.getText());
    }

    public void testVersionFromClosedDocumentCannotEditReopenedDocument() throws Exception {
        var context = open();
        int closedVersion = context.version();
        context.wrapper().disconnect(context.uri(), false);
        // The server never sees this typing, so the reopened document differs from the closed one.
        type(context);
        LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(myFixture.getFile(), null, null).get(30, TimeUnit.SECONDS);

        assertFalse(apply(versioned(context.uri(), closedVersion)).isApplied());
        assertEquals("new typing", context.document().getText());
        // The reopened document must not restart at a version that identified the closed one.
        assertTrue(context.version() > closedVersion);
    }

    public void testRetiredClientCannotApplyIncomingEdit() throws Exception {
        var context = open();
        // A replaced client can still receive a request that was queued on its transport.
        var client = new LanguageClientImpl(myFixture.getProject());
        client.setServerWrapper(context.wrapper());
        client.dispose();

        var pending = client.applyEdit(new ApplyWorkspaceEditParams(versioned(context.uri(), context.version())));
        PlatformTestUtil.waitWithEventsDispatching("workspace/applyEdit reply", pending::isDone, 30);
        assertFalse(pending.join().isApplied());
        assertEquals("before", context.document().getText());
    }

    public void testApplyEditDoesNotWaitForAWriteAction() throws Exception {
        var context = open();
        var client = new LanguageClientImpl(myFixture.getProject());
        // Dispose with the project: the client reads project services when it is disposed.
        Disposer.register(myFixture.getProject(), client);
        client.setServerWrapper(context.wrapper());
        var params = new ApplyWorkspaceEditParams(versioned(context.uri(), context.version()));
        // LSP4J calls applyEdit on its message thread. A write action can be waiting for a reply that only
        // that thread can read, so applyEdit must return while the write action still runs.
        var returned = new CompletableFuture<CompletableFuture<ApplyWorkspaceEditResponse>>();
        WriteAction.run(() -> {
            ApplicationManager.getApplication().executeOnPooledThread(() -> returned.complete(client.applyEdit(params)));
            returned.get(10, TimeUnit.SECONDS);
        });

        var pending = returned.join();
        PlatformTestUtil.waitWithEventsDispatching("workspace/applyEdit reply", pending::isDone, 30);
        assertTrue(pending.join().isApplied());
        assertEquals("after", context.document().getText());
    }
}

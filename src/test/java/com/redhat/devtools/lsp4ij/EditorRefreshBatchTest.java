/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Holds write access while queuing refreshes, then drains read/UI work explicitly. This makes
 * coalescing and disposal assertions independent of thread scheduling and debounce delays.
 */
public class EditorRefreshBatchTest extends LightPlatformTestCase {

    public void testRefreshBurstCoalescesTheWholeConnectionPerFeature() {
        var wrapper = new CountingWrapper();
        var client = client(wrapper);

        ApplicationManager.getApplication().runWriteAction(() ->
                IntStream.range(0, 64).forEach(index -> requestAll(client)));
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

        assertEquals("One file pass per feature, independent of document and request counts", 3, wrapper.filePasses.get());
    }

    public void testDifferentConnectionsRetainTheirOwnRefreshes() {
        var first = new CountingWrapper();
        var second = new CountingWrapper();
        var firstClient = client(first);
        var secondClient = client(second);

        ApplicationManager.getApplication().runWriteAction(() -> {
            requestAll(firstClient);
            requestAll(secondClient);
        });
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

        assertEquals(3, first.filePasses.get());
        assertEquals(3, second.filePasses.get());
    }

    public void testDisposalRetiresQueuedRefreshBeforeEnumeratingFiles() {
        var wrapper = new CountingWrapper();
        var client = client(wrapper);

        ApplicationManager.getApplication().runWriteAction(() -> {
            requestAll(client);
            // LanguageServerWrapper currently disposes its client directly on restart.
            client.dispose();
        });
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

        assertEquals(0, wrapper.filePasses.get());
        requestAll(client);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        assertEquals(0, wrapper.filePasses.get());
    }

    public void testBatchStillInvalidatesCachesForAnOpenEditor() {
        var file = new LightVirtualFile("refresh.txt", "text");
        var document = FileDocumentManager.getInstance().getDocument(file);
        assertNotNull(document);
        var editor = EditorFactory.getInstance().createEditor(document, getProject());
        try {
            var psiFile = PsiManager.getInstance(getProject()).findFile(file);
            assertNotNull(psiFile);
            var support = LSPFileSupport.getSupport(psiFile);
            var identifier = new TextDocumentIdentifier(file.getUrl());
            var requests = ReadAction.compute(() -> List.of(
                    support.getCodeLensSupport().getCodeLenses(new CodeLensParams(identifier)),
                    support.getInlayHintsSupport().getInlayHints(new InlayHintParams(identifier,
                            new Range(new Position(0, 0), new Position(0, 4)))),
                    support.getSemanticTokensSupport().getFeatureData(new SemanticTokensParams(identifier))));
            PlatformTestUtil.waitForFuture(CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)), 30_000);
            assertNotNull(support.getCodeLensSupport().getFuture());
            assertNotNull(support.getInlayHintsSupport().getFuture());
            assertNotNull(support.getSemanticTokensSupport().getFuture());

            var wrapper = new CountingWrapper();
            var client = client(wrapper);
            ApplicationManager.getApplication().runWriteAction(() -> {
                requestAll(client);
                // Enumerate when the queued pass runs, including documents connected meanwhile.
                wrapper.addDocument(file);
            });
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

            assertNull(support.getCodeLensSupport().getFuture());
            assertNull(support.getInlayHintsSupport().getFuture());
            assertNull(support.getSemanticTokensSupport().getFuture());
        } finally {
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }

    private LanguageClientImpl client(CountingWrapper wrapper) {
        var client = new LanguageClientImpl(getProject());
        client.setServerWrapper(wrapper);
        Disposer.register(getTestRootDisposable(), client);
        return client;
    }

    private static void requestAll(LanguageClientImpl client) {
        CompletableFuture.allOf(client.refreshCodeLenses(), client.refreshInlayHints(),
                client.refreshSemanticTokens()).join();
    }

    private class CountingWrapper extends LanguageServerWrapper {
        private final AtomicInteger filePasses = new AtomicInteger();
        private final List<OpenedDocument> documents;

        CountingWrapper() {
            super(EditorRefreshBatchTest.this.getProject(), new MockLanguageServerDefinition("refresh-batch", false));
            Disposer.register(getTestRootDisposable(), this);
            var server = ((MockLanguageServerDefinition) getServerDefinition()).getServer();
            documents = new ArrayList<>(IntStream.range(0, 32)
                    .mapToObj(index -> new OpenedDocument(new LanguageServerItem(server, this),
                            new LightVirtualFile("connected-" + index + ".test"), null)).toList());
        }

        private void addDocument(VirtualFile file) {
            var server = ((MockLanguageServerDefinition) getServerDefinition()).getServer();
            documents.add(new OpenedDocument(new LanguageServerItem(server, this), file, null));
        }

        @Override
        public Collection<OpenedDocument> getOpenedDocuments() {
            filePasses.incrementAndGet();
            return documents;
        }
    }
}

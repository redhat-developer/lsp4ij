/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.files;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link AbstractLSPFileListener#isDocumentStale(String, Document, Map)}.
 * <p>
 * Verifies that stale workspace edits are detected when target documents have
 * been modified or newly opened after the stamp snapshot was captured.
 */
public class DocumentStampValidationTest extends BasePlatformTestCase {

    public void testUnchangedDocumentIsNotStale() {
        var psiFile = myFixture.configureByText("test.txt", "hello");
        Document doc = FileDocumentManager.getInstance().getCachedDocument(psiFile.getVirtualFile());
        assertNotNull(doc);

        String uri = "file:///test.txt";
        Map<String, Long> stamps = Map.of(uri, doc.getModificationStamp());

        assertFalse("Unchanged document should not be stale",
                AbstractLSPFileListener.isDocumentStale(uri, doc, stamps));
    }

    public void testModifiedDocumentIsStale() {
        var psiFile = myFixture.configureByText("test.txt", "hello");
        Document doc = FileDocumentManager.getInstance().getCachedDocument(psiFile.getVirtualFile());
        assertNotNull(doc);

        String uri = "file:///test.txt";
        Map<String, Long> stamps = Map.of(uri, doc.getModificationStamp());

        WriteAction.run(() -> doc.setText("modified"));

        assertTrue("Modified document should be stale",
                AbstractLSPFileListener.isDocumentStale(uri, doc, stamps));
    }

    public void testNewlyOpenedDocumentIsStale() {
        var psiFile = myFixture.configureByText("untracked.txt", "new content");
        Document doc = FileDocumentManager.getInstance().getCachedDocument(psiFile.getVirtualFile());
        assertNotNull(doc);

        String uri = "file:///untracked.txt";
        // Empty stamps: this document was not tracked when stamps were captured
        Map<String, Long> stamps = new HashMap<>();
        stamps.put("file:///other.txt", 42L);

        assertTrue("Document absent from stamp snapshot should be stale",
                AbstractLSPFileListener.isDocumentStale(uri, doc, stamps));
    }

    public void testNullDocumentIsNotStale() {
        String uri = "file:///nonexistent.txt";
        Map<String, Long> stamps = Map.of("file:///other.txt", 42L);

        assertFalse("Null document should not be stale",
                AbstractLSPFileListener.isDocumentStale(uri, null, stamps));
    }

    public void testDocumentNotInStampsAndNotCachedIsNotStale() {
        String uri = "file:///unknown.txt";
        Map<String, Long> stamps = Map.of("file:///tracked.txt", 42L);

        assertFalse("Document with no cache and no stamp should not be stale",
                AbstractLSPFileListener.isDocumentStale(uri, null, stamps));
    }
}

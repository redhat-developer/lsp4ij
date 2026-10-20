/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Request ownership must not disconnect another feature's document. */
public class DocumentConnectionScopeTest extends LSPCodeInsightFixtureTestCase {

    public DocumentConnectionScopeTest() {
        super("*.ts");
    }

    public void testLastScopeReleasesClosedDocument() throws Exception {
        var file = myFixture.addFileToProject("scoped.ts", "const value=1;");
        try (var first = new DocumentConnectionScope(); var second = new DocumentConnectionScope()) {
            var wrapper = connect(file, first).get(0).getServerWrapper();
            var uri = wrapper.toUri(file.getVirtualFile());
            var connection = wrapper.getOpenedDocument(uri);
            assertNotNull(connection);
            assertSame(wrapper, connect(file, second).get(0).getServerWrapper());
            first.close();
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            assertSame("An overlapping request retains the same synchronizer", connection, wrapper.getOpenedDocument(uri));
            second.close();
            PlatformTestUtil.waitWithEventsDispatching("the last scoped owner must disconnect",
                    () -> wrapper.getOpenedDocument(uri) == null, 5);
        }
    }

    public void testOrdinaryLookupPromotesTemporaryConnection() throws Exception {
        var file = myFixture.addFileToProject("promoted.ts", "const value=1;");
        try (var scope = new DocumentConnectionScope()) {
            var wrapper = connect(file, scope).get(0).getServerWrapper();
            var uri = wrapper.toUri(file.getVirtualFile());
            var connection = wrapper.getOpenedDocument(uri);
            LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                    .get(5, TimeUnit.SECONDS);
            scope.close();
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            assertSame("An ordinary feature owns the promoted connection", connection, wrapper.getOpenedDocument(uri));
        }
    }

    public void testExistingConnectionSurvivesScope() throws Exception {
        var file = myFixture.addFileToProject("existing.ts", "const value=1;");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(5, TimeUnit.SECONDS).get(0).getServerWrapper();
        var uri = wrapper.toUri(file.getVirtualFile());
        var connection = wrapper.getOpenedDocument(uri);
        try (var scope = new DocumentConnectionScope()) {
            connect(file, scope);
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertSame(connection, wrapper.getOpenedDocument(uri));
    }

    public void testEditorOpenedDuringRequestRetainsConnection() throws Exception {
        var file = myFixture.addFileToProject("editor.ts", "const value=1;");
        try (var scope = new DocumentConnectionScope()) {
            var wrapper = connect(file, scope).get(0).getServerWrapper();
            var uri = wrapper.toUri(file.getVirtualFile());
            var connection = wrapper.getOpenedDocument(uri);
            FileEditorManager.getInstance(myFixture.getProject()).openFile(file.getVirtualFile(), false);
            scope.close();
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            assertSame(connection, wrapper.getOpenedDocument(uri));
        }
    }

    public void testAlreadyClosedScopeCannotConnectLater() throws Exception {
        var file = myFixture.addFileToProject("cancelled.ts", "const value=1;");
        var other = myFixture.addFileToProject("other.ts", "const other=1;");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(other, null, null)
                .get(5, TimeUnit.SECONDS).get(0).getServerWrapper();
        try (var scope = new DocumentConnectionScope()) {
            scope.close();
            assertEmpty(connect(file, scope));
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            assertNull(wrapper.getOpenedDocument(wrapper.toUri(file.getVirtualFile())));
        }
    }

    public void testOldScopeCannotReleaseReplacementConnection() throws Exception {
        var file = myFixture.addFileToProject("replaced.ts", "const value=1;");
        try (var oldScope = new DocumentConnectionScope(); var currentScope = new DocumentConnectionScope()) {
            var wrapper = connect(file, oldScope).get(0).getServerWrapper();
            var uri = wrapper.toUri(file.getVirtualFile());
            wrapper.disconnect(uri, false);
            connect(file, currentScope);
            var current = wrapper.getOpenedDocument(uri);
            assertNotNull(current);
            oldScope.close();
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            assertSame(current, wrapper.getOpenedDocument(uri));
            currentScope.close();
            PlatformTestUtil.waitWithEventsDispatching("replacement scope must disconnect its own document",
                    () -> wrapper.getOpenedDocument(uri) == null, 5);
        }
    }

    public void testCloseIsIdempotentAndReleasesLateRegistrations() {
        var releases = new AtomicInteger();
        var scope = new DocumentConnectionScope();
        scope.register(releases::incrementAndGet);
        scope.close();
        scope.close();
        scope.register(releases::incrementAndGet);
        assertEquals(2, releases.get());
    }

    private List<LanguageServerItem> connect(PsiFile file, DocumentConnectionScope scope) throws Exception {
        return LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServersForScope(file, null, null, scope).get(5, TimeUnit.SECONDS);
    }
}

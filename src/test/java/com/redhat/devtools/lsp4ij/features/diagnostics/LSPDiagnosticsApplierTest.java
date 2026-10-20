/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link LSPDiagnosticsApplier}.
 */
public class LSPDiagnosticsApplierTest extends LSPCodeInsightFixtureTestCase {

    public LSPDiagnosticsApplierTest() {
        super("*.foo");
    }

    public void testDiagnosticsPublishedBeforeThePassIsRegisteredAreShown() throws Exception {
        if (LSPDiagnosticsApplier.GROUP_ID == -1) {
            // Let the project's registrar register the pass factory, to get a real pass ID.
            myFixture.configureByText("first.txt", "");
            myFixture.doHighlighting();
        }
        int groupId = LSPDiagnosticsApplier.GROUP_ID;
        assertTrue(groupId != -1);
        // In a new IDE session, a server can publish diagnostics before the first highlighting has
        // registered LSPDiagnosticsPassFactory.
        LSPDiagnosticsApplier.GROUP_ID = -1;
        try {
            MockLanguageServer.INSTANCE.setPublishDiagnostics(List.of(
                    new Diagnostic(new Range(new Position(0, 0), new Position(0, 6)), "published early")));
            var file = myFixture.configureByText("test.foo", "before");
            LanguageServiceAccessor.getInstance(myFixture.getProject())
                    .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
            // The document stores the diagnostics before it requests their refresh, so wait for the
            // refresh itself: one requested after the pass registers is applied as any other
            var applier = LSPDiagnosticsApplier.getInstance(myFixture.getProject());
            PlatformTestUtil.waitWithEventsDispatching("the refresh must wait for the pass", applier::hasPendingRefreshes, 30);
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
            assertEmpty(shownDiagnostics());

            new LSPDiagnosticsPassFactory().registerHighlightingPassFactory(new TextEditorHighlightingPassRegistrar() {
                @Override
                public int registerTextEditorHighlightingPass(TextEditorHighlightingPassFactory factory,
                                                              int[] runAfterCompletionOf,
                                                              int[] runAfterStartingOf,
                                                              boolean runIntentionsPassAfter,
                                                              int forcedPassId) {
                    return groupId;
                }
            }, myFixture.getProject());
            // the refresh collects in the background and applies on the UI thread
            PlatformTestUtil.waitWithEventsDispatching("the diagnostics published early must be shown",
                    () -> List.of("published early").equals(shownDiagnostics()), 30);
        } finally {
            LSPDiagnosticsApplier.GROUP_ID = groupId;
        }
    }

    private List<String> shownDiagnostics() {
        return DaemonCodeAnalyzerImpl.getHighlights(myFixture.getEditor().getDocument(), null, myFixture.getProject())
                .stream()
                .map(HighlightInfo::getDescription)
                .toList();
    }
}

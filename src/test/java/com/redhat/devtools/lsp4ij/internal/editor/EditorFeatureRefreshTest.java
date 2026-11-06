/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.editor;

import com.intellij.openapi.util.Disposer;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.internal.VirtualFileCancelChecker;

/**
 * Tests the refresh of the editor features of a file, which a language server submits as it stops.
 */
public class EditorFeatureRefreshTest extends LSPCodeInsightFixtureTestCase {

    public EditorFeatureRefreshTest() {
        super("*.foo");
    }

    public void testARefreshSubmittedWhileTheProjectIsDisposedNeverRuns() {
        var project = myFixture.getProject();
        var file = myFixture.configureByText("one.foo", "text").getVirtualFile();
        var manager = EditorFeatureManager.getInstance(project);
        // A language server is a child of its project, and stopping it as the project is disposed refreshes the
        // editors of its files. That refresh waits for the write action which disposes the project, and must not run
        // after it: the project's services are gone. The tear down disposes the project, then waits for the pending
        // read actions and fails on one that failed
        Disposer.register(project, () -> manager.refreshEditorFeature(file, EditorFeatureType.ALL, true,
                new VirtualFileCancelChecker(file)));
    }
}

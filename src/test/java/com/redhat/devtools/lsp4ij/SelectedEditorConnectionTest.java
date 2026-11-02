/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;

import java.util.concurrent.CompletableFuture;

/**
 * Selecting the editor tab of a file connects the file to its language servers, which then receive its didOpen.
 * Code vision is disabled, as it also asks for the servers of a file whose editor opens, so only the editor events
 * of the project that reach {@link ConnectDocumentToLanguageServerSetupParticipant} can connect the file.
 */
public class SelectedEditorConnectionTest extends LSPCodeInsightFixtureTestCase {

    private boolean codeVisionEnabled;

    public SelectedEditorConnectionTest() {
        super("*.ts");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        codeVisionEnabled = CodeVisionSettings.getInstance().getCodeVisionEnabled();
        CodeVisionSettings.getInstance().setCodeVisionEnabled(false);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            CodeVisionSettings.getInstance().setCodeVisionEnabled(codeVisionEnabled);
        } finally {
            super.tearDown();
        }
    }

    public void testSelectingTheTabOfAFileConnectsIt() {
        var file = myFixture.addFileToProject("selected.ts", "const value=1;");
        var opened = expectDidOpen("selected.ts");

        FileEditorManager.getInstance(myFixture.getProject()).openFile(file.getVirtualFile(), true);

        PlatformTestUtil.waitWithEventsDispatching("selecting the tab of the file must send its didOpen",
                opened::isDone, 10);
    }

    public void testSelectingTheTabOfASecondFileConnectsItToTheRunningServer() {
        var first = myFixture.addFileToProject("first.ts", "const first=1;");
        var second = myFixture.addFileToProject("second.ts", "const second=1;");
        var editors = FileEditorManager.getInstance(myFixture.getProject());
        var firstOpened = expectDidOpen("first.ts");
        editors.openFile(first.getVirtualFile(), true);
        PlatformTestUtil.waitWithEventsDispatching("selecting the first tab must send its didOpen",
                firstOpened::isDone, 10);

        var secondOpened = expectDidOpen("second.ts");
        editors.openFile(second.getVirtualFile(), true);

        PlatformTestUtil.waitWithEventsDispatching("selecting the second tab must send its didOpen",
                secondOpened::isDone, 10);
    }

    private static CompletableFuture<DidOpenTextDocumentParams> expectDidOpen(String fileName) {
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened,
                params -> params.getTextDocument().getUri().endsWith("/" + fileName));
        return opened;
    }
}

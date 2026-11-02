/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import java.util.List;
import java.util.Map;

/**
 * A language server can send a workspace edit whose new text ends its lines with '\r', as Prettier does with
 * 'endOfLine: "cr"'. An IntelliJ document holds '\n' line separators only, so each '\r' becomes a '\n'.
 */
public class WorkspaceEditLineSeparatorsTest extends LSPCodeInsightFixtureTestCase {

    public WorkspaceEditLineSeparatorsTest() {
        // the file is a local file; no language server is connected to it
        super("*.unused");
    }

    public void testAWorkspaceEditWithCrLineEndingsIsApplied() {
        VirtualFile file = myFixture.configureByText("open.txt", "a=1;\nb=2;\n").getVirtualFile();
        WorkspaceEdit edit = new WorkspaceEdit(Map.of(
                LSPIJUtils.toUri(file).toString(),
                List.of(new TextEdit(new Range(new Position(0, 0), new Position(2, 0)), "a = 1;\rb = 2;\r"))));

        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> LSPIJUtils.applyWorkspaceEdit(edit));

        assertEquals("a = 1;\nb = 2;\n", myFixture.getEditor().getDocument().getText());
    }
}

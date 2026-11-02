/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.formatting;

import com.redhat.devtools.lsp4ij.fixtures.LSPFormattingFixtureTestCase;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.List;

/**
 * A language server can reply to 'textDocument/formatting' with CRLF line endings, as a formatter does on Windows or
 * for 'end_of_line = crlf' in .editorconfig. An IntelliJ document holds '\n' line separators only, so the formatted
 * text gets '\n' line separators.
 */
public class CrlfFormattingTest extends LSPFormattingFixtureTestCase {

    public CrlfFormattingTest() {
        super("*.ts");
    }

    public void testAReplyWithCrlfLineEndingsIsApplied() {
        // The server replaces the whole document, with the '1' which assertFormatting types at its start
        assertFormatting("test.ts",
                "function foo() {\nconst s = ''\n}\n",
                List.of(new TextEdit(new Range(new Position(0, 0), new Position(3, 0)),
                        "function foo() {\r\n    const s = ''\r\n}\r\n")),
                "function foo() {\n    const s = ''\n}\n");
    }
}

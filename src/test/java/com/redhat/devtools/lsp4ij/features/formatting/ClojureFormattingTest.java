/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.formatting;

import com.redhat.devtools.lsp4ij.fixtures.LSPFormattingFixtureTestCase;

/**
 * Formatting tests by emulating LSP 'textDocument/formatting', 'textDocument/rangeFormatting'
 * responses from the clojure-lsp language server.
 */
public class ClojureFormattingTest extends LSPFormattingFixtureTestCase {

    public void testFormatting() {
        // Test with TextEdit end which is outside the document length (uses of 999999)
        assertFormatting("test.ts",
                """                
                        (let
                                            [binding ""])""",
                """
                          [
                          {
                               "range": {
                                 "start": {
                                   "line": 0,
                                   "character": 0
                                 },
                                 "end": {
                                   "line": 999999,
                                   "character": 999999
                                 }
                               },
                               "newText": "(let\\n [binding \\"\\"])"
                             }
                        ]
                                        """,
                """                
                        (let
                         [binding ""])""");
    }
}

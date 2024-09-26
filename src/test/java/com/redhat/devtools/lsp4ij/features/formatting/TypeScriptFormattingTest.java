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
 * responses from the typescript-language-server.
 */
public class TypeScriptFormattingTest extends LSPFormattingFixtureTestCase {

    public TypeScriptFormattingTest() {
        super("*.ts");
    }

    public void testFormatting() {
        // 1. Test completion items response
        assertFormatting("test.ts",
                """                
                           function foo() {
                        const s = ''
                        }""",
                """
                          [
                          {
                            "range": {
                              "start": {
                                "line": 0,
                                "character": 0
                              },
                              "end": {
                                "line": 0,
                                "character": 4
                              }
                            },
                            "newText": ""
                          },
                          {
                            "range": {
                              "start": {
                                "line": 1,
                                "character": 0
                              },
                              "end": {
                                "line": 1,
                                "character": 0
                              }
                            },
                            "newText": "    "
                          }
                        ]
                                        """,
                """                
                        function foo() {
                            const s = ''
                        }""");
    }
}

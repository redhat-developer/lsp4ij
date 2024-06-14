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
package com.redhat.devtools.lsp4ij.features.documentation;

import com.redhat.devtools.lsp4ij.fixtures.LSPHoverFixtureTestCase;

/**
 * Hover tests by emulating LSP 'textDocument/hover' responses
 * from the typescript-language-server.
 */
public class TypeScriptHoverTest extends LSPHoverFixtureTestCase {

    public TypeScriptHoverTest() {
        super("*.ts");
        super.setLanguageId("typescript"); // Used to retrieve the TextMate TypeScript (used in Markdown code block), because TextMate are retrieved by file extension.
    }

    public void testHoverAsPlaintext() {
        assertHover("test.ts",
                """
                        const s = "";
                        s.c<caret>harAt(0);
                        """,
                """
                                {
                                  "contents": {
                                    "kind": "plaintext",
                                    "value": "\n```typescript\n(method) String.charAt(pos: number): string\n```\nReturns the character at the specified index.\n\n*@param* `pos` — The zero-based index of the desired character."
                                  },
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 2
                                    },
                                    "end": {
                                      "line": 1,
                                      "character": 8
                                    }
                                  }
                                }   
                        """,
                """
                        \n```typescript\n(method) String.charAt(pos: number): string\n```\nReturns the character at the specified index.\n\n*@param* `pos` — The zero-based index of the desired character."""
        );
    }

    public void testHoverAsMarkdown() {
        assertHover("test.ts",
                """
                        const s = "";
                        s.c<caret>harAt(0);
                        """,
                """
                                {
                                  "contents": {
                                    "kind": "markdown",
                                    "value": "\n```typescript\n(method) String.charAt(pos: number): string\n```\nReturns the character at the specified index.\n\n*@param* `pos` — The zero-based index of the desired character."
                                  },
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 2
                                    },
                                    "end": {
                                      "line": 1,
                                      "character": 8
                                    }
                                  }
                                }   
                        """,
                """
                        <pre><span style="">(</span><span style="">method)&#32;String.charAt(pos:&#32;number):&#32;string<br></span></pre>
                        <p>Returns the character at the specified index.</p>
                        <p><em>@param</em> <code>pos</code> — The zero-based index of the desired character.</p>
                        """
        );
    }

    public void testNoHoverWithEmptyContents() {
        assertHover("test.ts",
                """
                        const s = "";
                        s.c<caret>harAt(0);
                        """,
                """
                                {
                                    "contents": []     
                                }  
                        """,
                null
        );

        assertHover("test.ts",
                """
                        const s = "";
                        s.c<caret>harAt(0);
                        """,
                """
                                {
                                    "contents": {}     
                                }  
                        """,
                null
        );
    }

    public void testNoHoverWithInvalidMarkupContent() {
        assertHover("test.ts",
                """
                        const s = "";
                        s.c<caret>harAt(0);
                        """,
                """
                                {
                                  "contents": {
                                    "kind": "markdown",
                                    "value": ""
                                  },
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 2
                                    },
                                    "end": {
                                      "line": 1,
                                      "character": 8
                                    }
                                  }
                                }  
                        """,
                null
        );
    }

    public void testNoHoverWithInvalidMarkedString() {
        assertHover("test.ts",
                """
                        const s = "";
                        s.c<caret>harAt(0);
                        """,
                """
                        {
                            "contents": [
                                 {
                                     "language": "java",
                                     "value": ""
                                 },
                                 ""
                            ]
                        }  
                        """,
                null
        );
    }

}

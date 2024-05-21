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
                        <html><head><style TYPE='text/css'>html { background-color: #ffffff;color: #000000; }</style></head><body>
                        ```typescript\n(method) String.charAt(pos: number): string\n```\nReturns the character at the specified index.\n\n*@param* `pos` — The zero-based index of the desired character.</body></html>"""
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
                        <html><head><style TYPE='text/css'>html { background-color: #ffffff;color: #000000; }</style></head><body><pre><code class="language-typescript">(method) String.charAt(pos: number): string
                        </code></pre>
                        <p>Returns the character at the specified index.</p>
                        <p><em>@param</em> <code>pos</code> — The zero-based index of the desired character.</p>
                        </body></html>"""
        );
    }
}

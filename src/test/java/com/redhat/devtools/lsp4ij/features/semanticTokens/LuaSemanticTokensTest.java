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
package com.redhat.devtools.lsp4ij.features.semanticTokens;

import com.redhat.devtools.lsp4ij.fixtures.LSPSemanticTokensFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Completion tests by emulating LSP 'textDocument/semanticTokens' responses
 * from the Lua language server.
 */
public class LuaSemanticTokensTest extends LSPSemanticTokensFixtureTestCase {

    public LuaSemanticTokensTest() {
        // Use *.luax instead of *.lua to avoid consuming the lua textmate
        super("*.luax");
    }


    public void testSemanticTokens() {
        // 1. Test completion items response
        assertSemanticTokens("test.luax",
                """
                --[[
                  ** {==================================================================
                  ** Testing memory limits
                  ** ===================================================================
                  --]]
                
                print("memory-allocation errors")
                """,
                """
                {
                  "data": [
                    0,
                    2,
                    4,
                    17,
                    0,
                    6,
                    0,
                    5,
                    12,
                    512
                  ]
                }
                """,
                """
                        --<LSP_COMMENT>[[
                         </LSP_COMMENT> ** {==================================================================
                          ** Testing memory limits
                          ** ===================================================================
                          --]]
                        
                        <LSP_DEFAULT_LIBRARY_FUNCTION>print</LSP_DEFAULT_LIBRARY_FUNCTION>("memory-allocation errors")
                        """
        );
    }

    private void assertSemanticTokens(@NotNull String fileName,
                                     @NotNull String editorContentText,
                                     @NotNull String jsonSemanticTokens,
                                     @NotNull String expected) {
        String semanticProvider = """
                {
                      "legend": {
                        "tokenTypes": [
                          "namespace",
                          "type",
                          "class",
                          "enum",
                          "interface",
                          "struct",
                          "typeParameter",
                          "parameter",
                          "variable",
                          "property",
                          "enumMember",
                          "event",
                          "function",
                          "method",
                          "macro",
                          "keyword",
                          "modifier",
                          "comment",
                          "string",
                          "number",
                          "regexp",
                          "operator",
                          "decorator"
                        ],
                        "tokenModifiers": [
                          "declaration",
                          "definition",
                          "readonly",
                          "static",
                          "deprecated",
                          "abstract",
                          "async",
                          "modification",
                          "documentation",
                          "defaultLibrary",
                          "global"
                        ]
                      },
                      "range": true,
                      "full": true
                    }""";
        assertSemanticTokens(fileName, editorContentText, semanticProvider, jsonSemanticTokens, expected);
    }
}

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

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import java.util.Map;

/**
 * Tests the semantic tokens-based file view provider for TypeScript.
 */
public class TypeScriptSemanticTokensFileViewProviderTest extends LSPSemanticTokensFileViewProviderFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.ts";

    // language=typescript
    private static final String TEST_FILE_BODY = """
            /** Doc comment. */
            export class Foo {
                field: number;
                get property() { return ''; };
            
                // Line comment
                static bar() {
                    console.log('Hello, world.');
                    const declaration = Math.PI;
                    console.log(declaration);
                }
            }
            """;

    public TypeScriptSemanticTokensFileViewProviderTest() {
        super("*.ts");
    }

    public void testSemanticTokens() {
        assertViewProvider(
                TEST_FILE_NAME,
                TEST_FILE_BODY,
                // language=json
                """
                        {
                          "legend": {
                            "tokenTypes": [
                              "class",
                              "enum",
                              "interface",
                              "namespace",
                              "typeParameter",
                              "type",
                              "parameter",
                              "variable",
                              "enumMember",
                              "property",
                              "function",
                              "member"
                            ],
                            "tokenModifiers": [
                              "declaration",
                              "static",
                              "async",
                              "readonly",
                              "defaultLibrary",
                              "local"
                            ]
                          },
                          "range": true,
                          "full": true
                        }
                        """,
                // language=json
                """
                        {
                          "data": [
                            1,
                            13,
                            3,
                            0,
                            1,
                            1,
                            4,
                            5,
                            9,
                            1,
                            1,
                            8,
                            8,
                            9,
                            1,
                            3,
                            11,
                            3,
                            11,
                            3,
                            1,
                            8,
                            7,
                            7,
                            16,
                            0,
                            8,
                            3,
                            11,
                            16,
                            1,
                            14,
                            11,
                            7,
                            41,
                            0,
                            14,
                            4,
                            7,
                            16,
                            0,
                            5,
                            2,
                            9,
                            24,
                            1,
                            8,
                            7,
                            7,
                            16,
                            0,
                            8,
                            3,
                            11,
                            16,
                            0,
                            4,
                            11,
                            7,
                            40
                          ]
                        }
                        """,
                Map.ofEntries(
                        Map.entry(fileBody -> fileBody.indexOf("Foo"), LSPSemanticTokenElementType.DECLARATION),
                        Map.entry(fileBody -> fileBody.indexOf("field"), LSPSemanticTokenElementType.DECLARATION),
                        Map.entry(fileBody -> fileBody.indexOf("property"), LSPSemanticTokenElementType.DECLARATION),
                        Map.entry(fileBody -> fileBody.indexOf("bar()"), LSPSemanticTokenElementType.DECLARATION),
                        Map.entry(fileBody -> fileBody.indexOf("console.log('"), LSPSemanticTokenElementType.REFERENCE),
                        Map.entry(fileBody -> fileBody.indexOf("log('"), LSPSemanticTokenElementType.REFERENCE),
                        Map.entry(fileBody -> fileBody.indexOf("declaration"), LSPSemanticTokenElementType.DECLARATION),
                        Map.entry(fileBody -> fileBody.indexOf("Math"), LSPSemanticTokenElementType.REFERENCE),
                        Map.entry(fileBody -> fileBody.indexOf("PI"), LSPSemanticTokenElementType.REFERENCE),
                        Map.entry(fileBody -> fileBody.indexOf("console.log(d"), LSPSemanticTokenElementType.REFERENCE),
                        Map.entry(fileBody -> fileBody.indexOf("log(d"), LSPSemanticTokenElementType.REFERENCE),
                        Map.entry(fileBody -> fileBody.indexOf("declaration)"), LSPSemanticTokenElementType.REFERENCE)
                )
        );
    }

    // Confirms the behavior for a language server that doesn't support semantic tokens or when they're not yet present
    public void testNoSemanticTokens() {
        assertViewProvider(
                TEST_FILE_NAME,
                TEST_FILE_BODY,
                null,
                null,
                // Should only be one file-level token/element of unknown type; check the start/middle/end
                Map.ofEntries(
                        Map.entry(fileBody -> 0, LSPSemanticTokenElementType.UNKNOWN),
                        Map.entry(fileBody -> TEST_FILE_BODY.length() / 2, LSPSemanticTokenElementType.UNKNOWN),
                        Map.entry(fileBody -> TEST_FILE_BODY.length() - 1, LSPSemanticTokenElementType.UNKNOWN)
                )
        );
    }
}

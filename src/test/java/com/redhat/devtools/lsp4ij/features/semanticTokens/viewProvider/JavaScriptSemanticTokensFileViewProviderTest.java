/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.redhat.devtools.lsp4ij.fixtures.LSPSemanticTokensFileViewProviderFixtureTestCase;

import java.util.Map;

/**
 * Tests the semantic tokens-based file view provider for JavaScript, a plain text/abstract file type.
 */
public class JavaScriptSemanticTokensFileViewProviderTest extends LSPSemanticTokensFileViewProviderFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.js";

    // language=javascript
    private static final String TEST_FILE_BODY = """
            /** Doc comment. */
            export class Foo {
                field;
                get property() { return ''; };
            
                // Line comment
                static bar() {
                    console.log('Hello, world.');
                    const declaration = Math.PI;
                    console.log(declaration);
                }
            }
            """;

    // language=json
    private static final String MOCK_SEMANTIC_TOKENS_PROVIDER_JSON = """
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
            """;

    // language=json
    private static final String MOCK_SEMANTIC_TOKENS_JSON = """
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
            """;

    public JavaScriptSemanticTokensFileViewProviderTest() {
        super("*.js", "javascript");
    }

    public void testEnabled() {
        assertViewProviderEnabled(
                TEST_FILE_NAME,
                TEST_FILE_BODY,
                MOCK_SEMANTIC_TOKENS_PROVIDER_JSON,
                MOCK_SEMANTIC_TOKENS_JSON,
                Map.ofEntries(
                        Map.entry(fileBody -> fileBody.indexOf("Foo"), isTypeDeclaration),
                        Map.entry(fileBody -> fileBody.indexOf("field"), isNonTypeDeclaration),
                        Map.entry(fileBody -> fileBody.indexOf("property"), isNonTypeDeclaration),
                        Map.entry(fileBody -> fileBody.indexOf("bar()"), isNonTypeDeclaration),
                        Map.entry(fileBody -> fileBody.indexOf("console.log('"), isNonTypeReference),
                        Map.entry(fileBody -> fileBody.indexOf("log('"), isNonTypeReference),
                        Map.entry(fileBody -> fileBody.indexOf("declaration"), isNonTypeDeclaration),
                        // "Math" is declared as a variable and not a type
                        Map.entry(fileBody -> fileBody.indexOf("Math"), isNonTypeReference),
                        Map.entry(fileBody -> fileBody.indexOf("PI"), isNonTypeReference),
                        Map.entry(fileBody -> fileBody.indexOf("console.log(d"), isNonTypeReference),
                        Map.entry(fileBody -> fileBody.indexOf("log(d"), isNonTypeReference),
                        Map.entry(fileBody -> fileBody.indexOf("declaration)"), isNonTypeReference)
                )
        );
    }

    public void testDisabled() {
        assertViewProviderDisabled(
                TEST_FILE_NAME,
                TEST_FILE_BODY,
                MOCK_SEMANTIC_TOKENS_PROVIDER_JSON,
                MOCK_SEMANTIC_TOKENS_JSON
        );
    }
}

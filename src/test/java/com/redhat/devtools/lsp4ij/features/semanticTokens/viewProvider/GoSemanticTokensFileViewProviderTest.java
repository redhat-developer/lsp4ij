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
 * Tests the semantic tokens-based file view provider for Go, a TextMate file type.
 */
public class GoSemanticTokensFileViewProviderTest extends LSPSemanticTokensFileViewProviderFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.go";

    // language=go
    private static final String TEST_FILE_BODY = """
            package test
            
            import "fmt"
            
            /* Block comment */
            func main() {
                // Line comment
                var num = 10;
            	fmt.Println("num = " + 10)
            }
            """;

    // language=json
    private static final String MOCK_SEMANTIC_TOKENS_PROVIDER_JSON = """
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
                  "decorator",
                  "label"
                ],
                "tokenModifiers": [
                  "declaration",
                  "definition",
                  "readonly",
                  "static"
                ]
              },
              "range": true,
              "full": true
            }
            """;

    // language=json
    private static final String MOCK_SEMANTIC_TOKENS_JSON = """
            {
              "resultId": "2025-02-21 14:10:40.6313279 -0600 CST m\\u003d+1.127429001",
              "data": [
                0,
                0,
                7,
                15,
                0,
                0,
                8,
                4,
                0,
                0,
                2,
                0,
                6,
                15,
                0,
                0,
                8,
                3,
                0,
                0,
                2,
                0,
                19,
                17,
                0,
                1,
                0,
                4,
                15,
                0,
                0,
                5,
                4,
                12,
                2,
                1,
                4,
                15,
                17,
                0,
                1,
                4,
                3,
                15,
                0,
                0,
                4,
                3,
                8,
                2,
                0,
                6,
                2,
                19,
                0,
                1,
                1,
                3,
                0,
                0,
                0,
                4,
                7,
                12,
                0,
                0,
                8,
                8,
                18,
                0,
                0,
                9,
                1,
                21,
                0,
                0,
                2,
                2,
                19,
                0
              ]
            }
            """;

    public GoSemanticTokensFileViewProviderTest() {
        super("*.go", "go");
    }

    public void testEnabled() {
        assertViewProviderEnabled(
                TEST_FILE_NAME,
                TEST_FILE_BODY,
                MOCK_SEMANTIC_TOKENS_PROVIDER_JSON,
                MOCK_SEMANTIC_TOKENS_JSON,
                Map.ofEntries(
                        // NOTE: This namespace token seems like it should be a declaration, but it doesn't include any modifiers
                        Map.entry(fileBody -> fileBody.indexOf("test"), isTypeReference),
                        Map.entry(fileBody -> fileBody.indexOf("fmt"), isTypeReference),
                        Map.entry(fileBody -> fileBody.indexOf("/*"), isComment),
                        Map.entry(fileBody -> fileBody.indexOf("main"), isNonTypeDeclaration),
                        Map.entry(fileBody -> fileBody.indexOf("//"), isComment),
                        Map.entry(fileBody -> fileBody.indexOf("num"), isNonTypeDeclaration),
                        Map.entry(fileBody -> fileBody.indexOf("10"), isNumericLiteral),
                        Map.entry(fileBody -> fileBody.indexOf("fmt."), isTypeReference),
                        Map.entry(fileBody -> fileBody.indexOf("Println"), isNonTypeReference),
                        Map.entry(fileBody -> fileBody.indexOf("\"num"), isStringLiteral),
                        Map.entry(fileBody -> fileBody.indexOf("10)"), isNumericLiteral)
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

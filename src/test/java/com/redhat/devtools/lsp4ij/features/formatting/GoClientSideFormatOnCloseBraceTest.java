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

package com.redhat.devtools.lsp4ij.features.formatting;

import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature.FormattingScope;
import com.redhat.devtools.lsp4ij.fixtures.LSPClientSideOnTypeFormattingFixtureTestCase;

/**
 * Go-based client-side on-type formatting tests for format-on-close brace. The Go language server does not support
 * range formatting, so these tests confirm its behavior when configured for file scope and also that it does nothing
 * when configured for code block scope (i.e., the default scope).
 */
public class GoClientSideFormatOnCloseBraceTest extends LSPClientSideOnTypeFormattingFixtureTestCase {

    private static final String TEST_FILE_NAME = "Test.go";

    public GoClientSideFormatOnCloseBraceTest() {
        super("*.go");
        setSupportsRangeFormatting(false);
    }

    // language=json
    private static final String SIMPLE_MOCK_SELECTION_RANGE_JSON = "[]";

    // language=json
    private static final String SIMPLE_MOCK_FOLDING_RANGE_JSON = "[]";

    // language=json
    private static final String SIMPLE_MOCK_RANGE_FORMATTING_JSON = """
            [
              {
                "range": {
                  "start": {
                    "line": 5,
                    "character": 0
                  },
                  "end": {
                    "line": 5,
                    "character": 0
                  }
                },
                "newText": "    "
              }
            ]
            """;

    // No language injection here because there are syntax errors
    private static final String SIMPLE_FILE_BODY_BEFORE = """
            package main
            
            import "fmt"
            
            func main() {
            fmt.Println("hello world")
            // type }
            """;

    public void testSimpleDefaults() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                SIMPLE_FILE_BODY_BEFORE,
                // language=go
                """
                        package main
                        
                        import "fmt"
                        
                        func main() {
                        fmt.Println("hello world")
                        }
                        """,
                SIMPLE_MOCK_SELECTION_RANGE_JSON,
                SIMPLE_MOCK_FOLDING_RANGE_JSON,
                SIMPLE_MOCK_RANGE_FORMATTING_JSON,
                null // No-op as the default is disabled
        );
    }

    public void testSimpleEnabled_defaultScope() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                SIMPLE_FILE_BODY_BEFORE,
                // language=go
                """
                        package main
                        
                        import "fmt"
                        
                        func main() {
                        fmt.Println("hello world")
                        }
                        """,
                SIMPLE_MOCK_SELECTION_RANGE_JSON,
                SIMPLE_MOCK_FOLDING_RANGE_JSON,
                SIMPLE_MOCK_RANGE_FORMATTING_JSON,
                clientConfiguration -> clientConfiguration.format.onTypeFormatting.clientSide.formatOnCloseBrace = true
        );
    }

    public void testSimpleEnabled_fileScope() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                SIMPLE_FILE_BODY_BEFORE,
                // language=go
                """
                        package main
                        
                        import "fmt"
                        
                        func main() {
                            fmt.Println("hello world")
                        }
                        """,
                SIMPLE_MOCK_SELECTION_RANGE_JSON,
                SIMPLE_MOCK_FOLDING_RANGE_JSON,
                SIMPLE_MOCK_RANGE_FORMATTING_JSON,
                clientConfiguration -> {
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnCloseBrace = true;
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnCloseBraceScope = FormattingScope.FILE;
                }
        );
    }
}

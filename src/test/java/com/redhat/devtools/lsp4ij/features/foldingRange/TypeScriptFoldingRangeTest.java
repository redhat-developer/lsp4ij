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

package com.redhat.devtools.lsp4ij.features.foldingRange;

import com.intellij.openapi.util.TextRange;
import com.redhat.devtools.lsp4ij.fixtures.LSPFoldingRangeFixtureTestCase;

/**
 * Selection range tests by emulating LSP 'textDocument/foldingRange' responses from the typescript-language-server.
 */
public class TypeScriptFoldingRangeTest extends LSPFoldingRangeFixtureTestCase {

    private static final String DEMO_TS_FILE_NAME = "demo.ts";
    // language=typescript
    private static final String DEMO_TS_FILE_BODY = """
            export class Demo {
                demo() {
                    console.log('demo');
                }
            }
            """;

    // Demo class braced block exclusive of braces
    private static final TextRange DEMO_CLASS_BODY_TEXT_RANGE = TextRange.create(afterFirst(DEMO_TS_FILE_BODY, "{", 1), beforeLast(DEMO_TS_FILE_BODY, "}", 1));
    // demo() function braced block exclusive of braces
    private static final TextRange DEMO_METHOD_BODY_TEXT_RANGE = TextRange.create(afterFirst(DEMO_TS_FILE_BODY, "{", 2), beforeLast(DEMO_TS_FILE_BODY, "}", 2));

    public TypeScriptFoldingRangeTest() {
        super("*.ts");
    }

    public void testFoldingRanges() {
        assertFoldingRanges(
                DEMO_TS_FILE_NAME,
                DEMO_TS_FILE_BODY,
                // language=json
                """
                        
                        [
                          {
                            "startLine": 0,
                            "endLine": 3
                          },
                          {
                            "startLine": 1,
                            "endLine": 2
                          }
                        ]
                        """,
                DEMO_CLASS_BODY_TEXT_RANGE,
                DEMO_METHOD_BODY_TEXT_RANGE
        );
    }

    public void testFoldingRanges_collapsedText() {
        assertFoldingRanges(
                DEMO_TS_FILE_NAME,
                DEMO_TS_FILE_BODY,
                // language=json
                """
                        
                        [
                          {
                            "startLine": 0,
                            "endLine": 3,
                            "collapsedText": "classBody"
                          },
                          {
                            "startLine": 1,
                            "endLine": 2,
                            "collapsedText": "methodBody"
                          }
                        ]
                        """,
                DEMO_CLASS_BODY_TEXT_RANGE,
                DEMO_METHOD_BODY_TEXT_RANGE
        );
    }
}

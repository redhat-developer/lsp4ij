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

import com.redhat.devtools.lsp4ij.fixtures.LSPCodeBlockProviderFixtureTestCase;

/**
 * Selection range tests by emulating LSP 'textDocument/foldingRange' responses from the typescript-language-server.
 */
public class TypeScriptCodeBlockProviderTest extends LSPCodeBlockProviderFixtureTestCase {

    private static final String DEMO_TS_FILE_NAME = "demo.ts";
    // language=typescript
    private static final String DEMO_TS_FILE_BODY = """
            export class Demo {
                demo() {
                    console.log('demo');
                }
            }
            """;

    public TypeScriptCodeBlockProviderTest() {
        super("*.ts");
    }

    public void testCodeBlocks() {
        assertCodeBlocks(
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
                new int[][]{
                        // From "console" to the begin and end of the "demo()" function's braced block
                        {beforeFirst(DEMO_TS_FILE_BODY, "console", 1), afterFirst(DEMO_TS_FILE_BODY, "{", 2), beforeLast(DEMO_TS_FILE_BODY, "}", 2)},
                        // From "demo" to the begin and end of the "Demo" class's braced block
                        {beforeFirst(DEMO_TS_FILE_BODY, "demo", 1), afterFirst(DEMO_TS_FILE_BODY, "{", 1), beforeLast(DEMO_TS_FILE_BODY, "}", 1)},
                        // From before the "Demo" class' open brace to the begin and end of the "Demo" class' braced block
                        {beforeFirst(DEMO_TS_FILE_BODY, "{", 1), afterFirst(DEMO_TS_FILE_BODY, "{", 1), beforeLast(DEMO_TS_FILE_BODY, "}", 1)},
                        // From after the "Demo" class' open brace to the begin and end of the "Demo" class' braced block
                        {afterFirst(DEMO_TS_FILE_BODY, "{", 1), afterFirst(DEMO_TS_FILE_BODY, "{", 1), beforeLast(DEMO_TS_FILE_BODY, "}", 1)},
                        // From before the "Demo" class' close brace to the begin and end of the "Demo" class' braced block
                        {beforeLast(DEMO_TS_FILE_BODY, "}", 1), afterFirst(DEMO_TS_FILE_BODY, "{", 1), beforeLast(DEMO_TS_FILE_BODY, "}", 1)},
                        // From after the "Demo" class' close brace to the begin and end of the "Demo" class' braced block
                        {afterLast(DEMO_TS_FILE_BODY, "}", 1), afterFirst(DEMO_TS_FILE_BODY, "{", 1), beforeLast(DEMO_TS_FILE_BODY, "}", 1)},
                        // From "Demo" which shouldn't move at all
                        {beforeFirst(DEMO_TS_FILE_BODY, "Demo", 1), beforeFirst(DEMO_TS_FILE_BODY, "Demo", 1), beforeFirst(DEMO_TS_FILE_BODY, "Demo", 1)}
                }
        );
    }
}

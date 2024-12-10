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

    public TypeScriptCodeBlockProviderTest() {
        super("*.ts");
    }

    public void testCodeBlocks() {
        String testFilename = "demo.ts";

        // language=json
        String mockFoldingRangesJson = """
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
                """;

        assertCodeBlock(
                testFilename,
                """
                        export class Demo {
                            demo() {<start>
                                <caret>console.log('demo');
                            <end>}
                        }
                        """,
                mockFoldingRangesJson
        );

        assertCodeBlock(
                testFilename,
                """
                        export class Demo {<start>
                            <caret>demo() {
                                console.log('demo');
                            }
                        <end>}
                        """,
                mockFoldingRangesJson
        );

        assertCodeBlock(
                testFilename,
                """
                        export class Demo {<caret><start>
                            demo() {
                                console.log('demo');
                            }
                        <end>}
                        """,
                mockFoldingRangesJson
        );

        assertCodeBlock(
                testFilename,
                """
                        export class Demo <caret>{<start>
                            demo() {
                                console.log('demo');
                            }
                        <end>}
                        """,
                mockFoldingRangesJson
        );

        assertCodeBlock(
                testFilename,
                """
                        export class Demo {<start>
                            demo() {
                                console.log('demo');
                            }
                        <end><caret>}
                        """,
                mockFoldingRangesJson
        );

        assertCodeBlock(
                testFilename,
                """
                        export class Demo {<start>
                            demo() {
                                console.log('demo');
                            }
                        <end>}<caret>
                        """,
                mockFoldingRangesJson
        );

        assertCodeBlock(
                testFilename,
                """
                        export class <caret><start><end>Demo {
                            demo() {
                                console.log('demo');
                            }
                        }
                        """,
                mockFoldingRangesJson
        );
    }
}

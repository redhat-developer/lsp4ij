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

import com.redhat.devtools.lsp4ij.fixtures.LSPFoldingRangeFixtureTestCase;

/**
 * Selection range tests by emulating LSP 'textDocument/foldingRange' responses from the typescript-language-server.
 */
public class TypeScriptFoldingRangeTest extends LSPFoldingRangeFixtureTestCase {

    public TypeScriptFoldingRangeTest() {
        super("*.ts");
    }

    public void testFoldingRanges() {
        assertFoldingRanges(
                "demo.ts",
                """
                        export class Demo {<start1>
                            demo() {<start2>
                                console.log('demo');
                            <end2>}
                        <end1>}
                        """,
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
                        """
        );
    }

    public void testFoldingRanges_collapsedText() {
        assertFoldingRanges(
                "demo.ts",
                """
                        export class Demo {<start1>
                            demo() {<start2>
                                console.log('demo');
                            <end2>}
                        <end1>}
                        """,
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
                        """
        );
    }
}

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

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.redhat.devtools.lsp4ij.fixtures.LSPFoldingRangeFixtureTestCase;

/**
 * Folding range tests by emulating LSP 'textDocument/foldingRange' responses from the typescript-language-server.
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

    // Collapse-by-default tests

    private static final String COLLAPSE_BY_DEFAULT_TEST_BODY = """
            import {something} from 'Somewhere';<start1>
            import {somethingElse} from 'SomewhereElse';<end1>
            
            export class Foo {<start2>
            <end2>}
            """;
    // language=json
    private static final String COLLAPSE_BY_DEFAULT_MOCK_FOLDING_RANGES_JSON = """
            [
              {
                "startLine": 0,
                "endLine": 1,
                "kind": "imports"
              },
              {
                "startLine": 3,
                "endLine": 3
              }
            ]
            """;

    public void testFoldingRanges_collapseNothing() {
        CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS = false;
        assertFoldingRanges(
                "foo.ts",
                COLLAPSE_BY_DEFAULT_TEST_BODY,
                COLLAPSE_BY_DEFAULT_MOCK_FOLDING_RANGES_JSON
                // Nothing should be collapsed by default
        );
    }

    public void testFoldingRanges_collapseImports() {
        CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS = true;
        assertFoldingRanges(
                "foo.ts",
                COLLAPSE_BY_DEFAULT_TEST_BODY,
                COLLAPSE_BY_DEFAULT_MOCK_FOLDING_RANGES_JSON,
                // Imports should be collapsed by default
                1
        );
    }
}

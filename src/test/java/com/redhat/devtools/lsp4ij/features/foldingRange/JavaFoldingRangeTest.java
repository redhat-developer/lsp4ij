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

package com.redhat.devtools.lsp4ij.features.foldingRange;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.redhat.devtools.lsp4ij.fixtures.LSPFoldingRangeFixtureTestCase;

/**
 * Folding range tests by emulating LSP 'textDocument/foldingRange' responses from jdtls.
 */
public class JavaFoldingRangeTest extends LSPFoldingRangeFixtureTestCase {

    private static final String TEST_FILE_NAME = "Hello.java";
    private static final String TEST_FILE_BODY = """
            /*<start1>
             * File header comment.
             */<end1>
            
            import java.util.ArrayList;<start2>
            import java.util.List;<end2>
            
            /**<start3>
             * Class comment.
             */<end3>
            public class Hello {
                /**<start4>
                 * Method comment.
                 * @param args command-line arguments
                 */<end4>
                public static void main(String[] args) {
                    List<String> strings = new ArrayList<>(args);
                }
            }
            """;
    // language=json
    private static final String MOCK_FOLDING_RANGES_JSON = """
            [
              {
                "startLine": 0,
                "endLine": 2,
                "kind": "comment"
              },
              {
                "startLine": 7,
                "endLine": 9,
                "kind": "comment"
              },
              {
                "startLine": 11,
                "endLine": 14,
                "kind": "comment"
              },
              {
                "startLine": 4,
                "endLine": 5,
                "kind": "imports"
              }
            ]
            """;

    public JavaFoldingRangeTest() {
        super("*.java");
    }

    public void testFoldingRanges_collapseNothing() {
        CodeFoldingSettings.getInstance().COLLAPSE_FILE_HEADER = false;
        CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS = false;
        assertFoldingRanges(
                TEST_FILE_NAME,
                TEST_FILE_BODY,
                MOCK_FOLDING_RANGES_JSON
                // Nothing should be collapsed by default
        );
    }

    public void testFoldingRanges_collapseFileHeader() {
        CodeFoldingSettings.getInstance().COLLAPSE_FILE_HEADER = true;
        CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS = false;
        assertFoldingRanges(
                TEST_FILE_NAME,
                TEST_FILE_BODY,
                MOCK_FOLDING_RANGES_JSON,
                // The file header should be collapsed by default
                1
        );
    }

    public void testFoldingRanges_collapseImports() {
        CodeFoldingSettings.getInstance().COLLAPSE_FILE_HEADER = false;
        CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS = true;
        assertFoldingRanges(
                TEST_FILE_NAME,
                TEST_FILE_BODY,
                MOCK_FOLDING_RANGES_JSON,
                // Imports should be collapse by default
                2
        );
    }

    public void testFoldingRanges_collapseBoth() {
        CodeFoldingSettings.getInstance().COLLAPSE_FILE_HEADER = true;
        CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS = true;
        assertFoldingRanges(
                TEST_FILE_NAME,
                TEST_FILE_BODY,
                MOCK_FOLDING_RANGES_JSON,
                // Both should be folded by default
                1,
                2
        );
    }
}

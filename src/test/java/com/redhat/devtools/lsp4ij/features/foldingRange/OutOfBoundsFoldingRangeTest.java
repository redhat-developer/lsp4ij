/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.foldingRange;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.redhat.devtools.lsp4ij.fixtures.LSPFoldingRangeFixtureTestCase;

/**
 * Folding ranges whose lines lie outside the document, as a reply computed before lines were deleted
 * can have, are dropped without failing the other ranges.
 */
public class OutOfBoundsFoldingRangeTest extends LSPFoldingRangeFixtureTestCase {

    private static final String TEST_FILE_NAME = "OutOfBounds.foldtest";
    private static final String TEST_FILE_BODY = """
            /*<start1>
             * File header comment.
             */<end1>
            class OutOfBounds {
            }
            """;

    public OutOfBoundsFoldingRangeTest() {
        super("*.foldtest");
    }

    public void testStartLineAfterTheLastLineIsDropped() {
        CodeFoldingSettings.getInstance().COLLAPSE_FILE_HEADER = false;
        assertFoldingRanges(TEST_FILE_NAME, TEST_FILE_BODY,
                // language=json
                """
                [
                  {
                    "startLine": 0,
                    "endLine": 2,
                    "kind": "comment"
                  },
                  {
                    "startLine": 50,
                    "endLine": 60
                  }
                ]
                """);
    }

    public void testNegativeEndLineIsDropped() {
        CodeFoldingSettings.getInstance().COLLAPSE_FILE_HEADER = false;
        assertFoldingRanges(TEST_FILE_NAME, TEST_FILE_BODY,
                // language=json
                """
                [
                  {
                    "startLine": 0,
                    "endLine": 2,
                    "kind": "comment"
                  },
                  {
                    "startLine": 1,
                    "endLine": -5
                  }
                ]
                """);
    }
}

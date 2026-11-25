/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;

/**
 * Tests completion items in a list with a default edit range ({@code itemDefaults.editRange}).
 * <p>
 * The default range is {@code 0:0-0:6}, which covers all of {@code gap-px}, including the text after the caret. Without
 * the default range, choosing {@code gap-1} would give {@code gap-gap-1px} instead of {@code gap-1}.
 */
public class CompletionItemDefaultsEditRangeTest extends LSPCompletionFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.tsx";

    // Items with only a label: they replace the default range with their label.
    // language=json
    private static final String LABEL_ONLY_COMPLETION_JSON = """
            {
              "isIncomplete": false,
              "items": [
                {
                  "label": "gap-1",
                  "kind": 21,
                  "sortText": "a"
                },
                {
                  "label": "gap-12",
                  "kind": 21,
                  "sortText": "b"
                }
              ],
              "itemDefaults": {
                "editRange": {
                  "start": { "line": 0, "character": 0 },
                  "end": { "line": 0, "character": 6 }
                }
              }
            }""";

    // An item with its own text edit, whose new text differs from its label: it keeps its edit.
    // language=json
    private static final String OWN_TEXT_EDIT_COMPLETION_JSON = """
            {
              "isIncomplete": false,
              "items": [
                {
                  "label": "col-l-sm",
                  "kind": 21,
                  "sortText": "a",
                  "textEdit": {
                    "range": {
                      "start": { "line": 0, "character": 0 },
                      "end": { "line": 0, "character": 8 }
                    },
                    "newText": "column-left-small"
                  }
                },
                {
                  "label": "col-l-md",
                  "kind": 21,
                  "sortText": "b"
                }
              ],
              "itemDefaults": {
                "editRange": {
                  "start": { "line": 0, "character": 0 },
                  "end": { "line": 0, "character": 6 }
                }
              }
            }""";

    public CompletionItemDefaultsEditRangeTest() {
        super("*.tsx");
    }

    public void testItemWithOnlyLabelReplacesDefaultEditRange() {
        assertCompletion(TEST_FILE_NAME, "ga", LABEL_ONLY_COMPLETION_JSON, "gap-1", "gap-12");
        // Without the label fallback, this gives "gap-gap-1px"
        assertApplyCompletionItem(0, "gap-1<caret>");
    }

    public void testItemWithOwnTextEditKeepsIt() {
        assertCompletion(TEST_FILE_NAME, "cl", OWN_TEXT_EDIT_COMPLETION_JSON, "col-l-sm", "col-l-md");
        // Without the textEdit guard, this gives the label "gap-px"
        assertApplyCompletionItem(0, "column-left-small");
    }
}

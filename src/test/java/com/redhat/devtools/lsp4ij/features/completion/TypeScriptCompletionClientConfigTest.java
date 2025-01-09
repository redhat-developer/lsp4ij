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
package com.redhat.devtools.lsp4ij.features.completion;

import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionClientConfigFixtureTestCase;

/**
 * Tests code completion client configuration settings using a mock TypeScript language server.
 */
public class TypeScriptCompletionClientConfigTest extends LSPCompletionClientConfigFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.ts";

    private static final String ABS_FILE_BODY_BEFORE = "Math.<caret>";

    // language=json
    private static final String ABS_MOCK_TEXT_COMPLETION_JSON = """
            {
              "isIncomplete": false,
              "items": [
                {
                  "label": "abs",
                  "kind": 2,
                  "sortText": "11",
                  "insertTextFormat": 2,
                  "data": {
                    "cacheId": 1
                  }
                }
              ]
            }
            """;

    // language=json
    private static final String ABS_MOCK_COMPLETION_ITEM_RESOLVE_JSON = """
            {
              "label": "abs",
              "kind": 2,
              "detail": "(method) Math.abs(x: number): number",
              "documentation": {
                "kind": "markdown",
                "value": "Returns the absolute value of a number (the value without regard to whether it is positive or negative).\\nFor example, the absolute value of -5 is the same as the absolute value of 5.\\n\\n*@param* `x` — A numeric expression for which the absolute value is needed."
              },
              "sortText": "11",
              "insertText": "abs(${1:x})$0",
              "insertTextFormat": 2,
              "data": {
                "file": "test.ts",
                "line": 1,
                "offset": 9,
                "entryNames": [
                  "abs"
                ]
              }
            }
            """;

    public TypeScriptCompletionClientConfigTest() {
        super("*.ts");
    }

    public void testUseTemplateForSingleArgumentDefault() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                ABS_FILE_BODY_BEFORE,
                "Math.abs(x)<caret>",
                ABS_MOCK_TEXT_COMPLETION_JSON,
                ABS_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                null // Use default configuration which has single arg templates enabled
        );
    }

    public void testUseTemplateForSingleArgumentDisabled() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                ABS_FILE_BODY_BEFORE,
                "Math.abs(<caret>)",
                ABS_MOCK_TEXT_COMPLETION_JSON,
                ABS_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForSingleArgument = false
        );
    }
}

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
    private static final String TEST_FILE_BODY_BEFORE = "Math.<caret>";

    public TypeScriptCompletionClientConfigTest() {
        super("*.ts");
    }

    // SINGLE ARGUMENT TESTS

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

    public void testUseTemplateForSingleArgumentDefault_singleArg() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                "Math.abs(x)<caret>",
                ABS_MOCK_TEXT_COMPLETION_JSON,
                ABS_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                null // Use default configuration which has single arg templates enabled
        );
    }

    public void testUseTemplateForSingleArgumentDisabled_singleArg() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                "Math.abs(<caret>)",
                ABS_MOCK_TEXT_COMPLETION_JSON,
                ABS_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForSingleArgument = false
        );
    }

    // MULTIPLE ARGUMENTS TESTS

    // language=json
    private static final String POW_MOCK_TEXT_COMPLETION_JSON = """
            {
              "isIncomplete": false,
              "items": [
                {
                  "label": "pow",
                  "kind": 2,
                  "sortText": "11",
                  "filterText": ".pow",
                  "insertTextFormat": 2,
                  "textEdit": {
                    "range": {
                      "start": {
                        "line": 0,
                        "character": 4
                      },
                      "end": {
                        "line": 0,
                        "character": 5
                      }
                    },
                    "newText": ".pow"
                  },
                  "data": {
                    "cacheId": 32
                  }
                }
              ]
            }
            """;
    // language=json
    private static final String POW_MOCK_COMPLETION_ITEM_RESOLVE_JSON = """
            {
              "label": "pow",
              "kind": 2,
              "detail": "(method) Math.pow(x: number, y: number): number",
              "documentation": {
                "kind": "markdown",
                "value": "Returns the value of a base expression taken to a specified power.\\n\\n*@param* `x` — The base value of the expression.  \\n\\n*@param* `y` — The exponent value of the expression."
              },
              "sortText": "11",
              "filterText": ".pow",
              "insertText": ".pow(${1:x}, ${2:y})$0",
              "insertTextFormat": 2,
              "textEdit": {
                "range": {
                  "start": {
                    "line": 0,
                    "character": 4
                  },
                  "end": {
                    "line": 0,
                    "character": 5
                  }
                },
                "newText": ".pow(${1:x}, ${2:y})$0"
              },
              "data": {
                "file": "test.ts",
                "line": 1,
                "offset": 6,
                "entryNames": [
                  "pow"
                ]
              }
            }
            """;

    public void testUseTemplateForSingleArgumentDefault_multipleArgs() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                "Math.pow(x, y)<caret>",
                POW_MOCK_TEXT_COMPLETION_JSON,
                POW_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                null // Use default configuration which has single arg templates enabled
        );
    }

    public void testUseTemplateForSingleArgumentDisabled_multipleArgs() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                // Because this has multiple arguments, the template will still be used
                "Math.pow(x, y)<caret>",
                POW_MOCK_TEXT_COMPLETION_JSON,
                POW_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForSingleArgument = false
        );
    }

    // NO ARGUMENTS TESTS

    // language=json
    private static final String RANDOM_MOCK_TEXT_COMPLETION_JSON = """
            {
              "isIncomplete": false,
              "items": [
                {
                  "label": "random",
                  "kind": 2,
                  "sortText": "11",
                  "filterText": ".random",
                  "insertTextFormat": 2,
                  "textEdit": {
                    "range": {
                      "start": {
                        "line": 0,
                        "character": 4
                      },
                      "end": {
                        "line": 0,
                        "character": 5
                      }
                    },
                    "newText": ".random"
                  },
                  "data": {
                    "cacheId": 33
                  }
                }
              ]
            }
            """;
    // language=json
    private static final String RANDOM_MOCK_COMPLETION_ITEM_RESOLVE_JSON = """
            {
              "label": "random",
              "kind": 2,
              "detail": "(method) Math.random(): number",
              "documentation": {
                "kind": "markdown",
                "value": "Returns a pseudorandom number between 0 and 1."
              },
              "sortText": "11",
              "insertText": "random()$0",
              "insertTextFormat": 2,
              "data": {
                "file": "test.ts",
                "line": 1,
                "offset": 7,
                "entryNames": [
                  "random"
                ]
              }
            }
            """;

    public void testUseTemplateForSingleArgumentDefault_noArgs() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                // Because this has no arguments, no template will be used and the caret will be placed outside of the argument list
                "Math.random()<caret>",
                RANDOM_MOCK_TEXT_COMPLETION_JSON,
                RANDOM_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                null // Use default configuration which has single arg templates enabled
        );
    }

    public void testUseTemplateForSingleArgumentDisabled_noArgs() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                // Because this has no arguments, no template will be used and the caret will be placed outside of the argument list
                "Math.random()<caret>",
                RANDOM_MOCK_TEXT_COMPLETION_JSON,
                RANDOM_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForSingleArgument = false
        );
    }
}

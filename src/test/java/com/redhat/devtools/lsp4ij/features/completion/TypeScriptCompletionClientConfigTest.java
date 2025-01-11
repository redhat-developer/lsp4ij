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

    // language=json
    private static final String MOCK_TEXT_COMPLETION_JSON = """
            {
              "isIncomplete": false,
              "items": [
                {
                  "label": "abs",
                  "kind": 2,
                  "sortText": "11",
                  "filterText": ".abs",
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
                    "newText": ".abs"
                  },
                  "data": {
                    "cacheId": 1
                  }
                },
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
                },
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

    public TypeScriptCompletionClientConfigTest() {
        super("*.ts");
    }

    // SINGLE ARGUMENT TESTS

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
              "filterText": ".abs",
              "insertText": ".abs(${1:x})$0",
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
                "newText": ".abs(${1:x})$0"
              },
              "data": {
                "file": "test.ts",
                "line": 1,
                "offset": 6,
                "entryNames": [
                  "abs"
                ]
              }
            }
            """;

    public void testUseTemplateForInvocationOnlySnippet_default_singleArg() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                "Math.abs(x)<caret>",
                MOCK_TEXT_COMPLETION_JSON,
                ABS_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                // Default config
                null
        );
    }

    public void testUseTemplateForInvocationOnlySnippet_disabled_singleArg() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                "Math.abs(<caret>)",
                MOCK_TEXT_COMPLETION_JSON,
                ABS_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForInvocationOnlySnippet = false
        );
    }

    // SINGLE ARGUMENT TESTS WITH NO VARIABLE VALUES

    private static final String ABS_NO_VARIABLE_VALUE_MOCK_COMPLETION_ITEM_RESOLVE_JSON = ABS_MOCK_COMPLETION_ITEM_RESOLVE_JSON.replace("${1:x}", "$1");

    public void testUseTemplateForInvocationOnlySnippet_default_singleArg_noVariableValue() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                // Even when disabled, the caret should be placed in the parens because there's no variable value
                "Math.abs(<caret>)",
                MOCK_TEXT_COMPLETION_JSON,
                ABS_NO_VARIABLE_VALUE_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                // Default config
                null
        );
    }

    public void testUseTemplateForInvocationOnlySnippet_disabled_singleArg_noVariableValue() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                "Math.abs(<caret>)",
                MOCK_TEXT_COMPLETION_JSON,
                ABS_NO_VARIABLE_VALUE_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForInvocationOnlySnippet = false
        );
    }

    // OPTIONAL ARGUMENT TESTS

    private static final String OPTIONAL_ARG_MOCK_COMPLETION_ITEM_RESOLVE_JSON = ABS_MOCK_COMPLETION_ITEM_RESOLVE_JSON.replace("${1:x}", "${1:x}$2");

    public void testUseTemplateForInvocationOnlySnippet_default_optionalArg() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                "Math.abs(x)<caret>",
                MOCK_TEXT_COMPLETION_JSON,
                OPTIONAL_ARG_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                // Default config
                null
        );
    }

    public void testUseTemplateForInvocationOnlySnippet_disabled_optionalArg() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                "Math.abs(<caret>)",
                MOCK_TEXT_COMPLETION_JSON,
                OPTIONAL_ARG_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForInvocationOnlySnippet = false
        );
    }

    // MULTIPLE ARGUMENTS TESTS

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

    public void testUseTemplateForInvocationOnlySnippet_default_multipleArgs() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                "Math.pow(x, y)<caret>",
                MOCK_TEXT_COMPLETION_JSON,
                POW_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                // Default config
                null
        );
    }

    public void testUseTemplateForInvocationOnlySnippet_disabled_multipleArgs() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                "Math.pow(<caret>)",
                MOCK_TEXT_COMPLETION_JSON,
                POW_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForInvocationOnlySnippet = false
        );
    }

    // NO ARGUMENTS TESTS

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
              "filterText": ".random",
              "insertText": ".random()$0",
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
                "newText": ".random()$0"
              },
              "data": {
                "file": "test.ts",
                "line": 1,
                "offset": 6,
                "entryNames": [
                  "random"
                ]
              }
            }
            """;

    public void testUseTemplateForInvocationOnlySnippet_default_noArgs() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                // Because this has no arguments, no template will be used and the caret will be placed outside of the argument list
                "Math.random()<caret>",
                MOCK_TEXT_COMPLETION_JSON,
                RANDOM_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                // Default config
                null
        );
    }

    public void testUseTemplateForInvocationOnlySnippet_disabled_noArgs() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                // Because this has no arguments, no template will be used and the caret will be placed outside of the argument list
                "Math.random()<caret>",
                MOCK_TEXT_COMPLETION_JSON,
                RANDOM_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForInvocationOnlySnippet = false
        );
    }

    // NON-ARGUMENT TAB STOP TESTS

    private static final String NON_ARGUMENT_TAB_STOPS_MOCK_COMPLETION_ITEM_RESOLVE_JSON = POW_MOCK_COMPLETION_ITEM_RESOLVE_JSON.replace("$0", " /* Enter text ${3:here} */$0");

    public void testUseTemplateForInvocationOnlySnippet_default_nonArgumentTabStops() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                "Math.pow(x, y) /* Enter text here */<caret>",
                MOCK_TEXT_COMPLETION_JSON,
                NON_ARGUMENT_TAB_STOPS_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                // Default config
                null
        );
    }

    public void testUseTemplateForInvocationOnlySnippet_disabled_nonArgumentTabStops() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                // Because there are arguments outside of the invocation args, the template will be used
                "Math.pow(x, y) /* Enter text here */<caret>",
                MOCK_TEXT_COMPLETION_JSON,
                NON_ARGUMENT_TAB_STOPS_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForInvocationOnlySnippet = false
        );
    }
}

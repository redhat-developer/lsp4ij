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
 * Tests code completion client configuration settings using a mock CSS language server.
 */
public class CssCompletionClientConfigTest extends LSPCompletionClientConfigFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.css";
    private static final String TEST_FILE_BODY_BEFORE = """
            .selector {
                background-color: <caret>;
            }
            """;

    // language=json
    private static final String MOCK_TEXT_COMPLETION_JSON = """
            {
              "isIncomplete": true,
              "items": [
                {
                  "label": "rgb",
                  "kind": 3,
                  "detail": "rgb($red, $green, $blue)",
                  "documentation": "Creates a Color from red, green, and blue values.",
                  "insertTextFormat": 2,
                  "textEdit": {
                    "range": {
                      "start": {
                        "line": 1,
                        "character": 22
                      },
                      "end": {
                        "line": 1,
                        "character": 22
                      }
                    },
                    "newText": "rgb(${1:red}, ${2:green}, ${3:blue})"
                  }
                },
                {
                  "label": "rgb relative",
                  "kind": 3,
                  "detail": "rgb(from $color $red $green $blue)",
                  "documentation": "Creates a Color from the red, green, and blue values of another Color.",
                  "insertTextFormat": 2,
                  "textEdit": {
                    "range": {
                      "start": {
                        "line": 1,
                        "character": 22
                      },
                      "end": {
                        "line": 1,
                        "character": 22
                      }
                    },
                    "newText": "rgb(from ${1:color} ${2:r} ${3:g} ${4:b})"
                  }
                },
                {
                  "label": "hwb",
                  "kind": 3,
                  "detail": "hwb($hue $white $black)",
                  "documentation": "Creates a Color from hue, white, and black values.",
                  "insertTextFormat": 2,
                  "textEdit": {
                    "range": {
                      "start": {
                        "line": 1,
                        "character": 22
                      },
                      "end": {
                        "line": 1,
                        "character": 22
                      }
                    },
                    "newText": "hwb(${1:hue} ${2:white} ${3:black})"
                  }
                },
                {
                  "label": "color",
                  "kind": 3,
                  "detail": "color($color-space $red $green $blue)",
                  "documentation": "Creates a Color in a specific color space from red, green, and blue values.",
                  "insertTextFormat": 2,
                  "textEdit": {
                    "range": {
                      "start": {
                        "line": 1,
                        "character": 22
                      },
                      "end": {
                        "line": 1,
                        "character": 22
                      }
                    },
                    "newText": "color(${1|srgb,srgb-linear,display-p3,a98-rgb,prophoto-rgb,rec2020,xyx,xyz-d50,xyz-d65|} ${2:red} ${3:green} ${4:blue})"
                  }
                },
                {
                  "label": "var()",
                  "kind": 3,
                  "documentation": "Evaluates the value of a custom variable.",
                  "insertTextFormat": 2,
                  "textEdit": {
                    "range": {
                      "start": {
                        "line": 1,
                        "character": 22
                      },
                      "end": {
                        "line": 1,
                        "character": 22
                      }
                    },
                    "newText": "var($1)"
                  },
                  "command": {
                    "title": "Suggest",
                    "command": "editor.action.triggerSuggest"
                  }
                }
              ],
              "itemDefaults": {
                "editRange": {
                  "start": {
                    "line": 1,
                    "character": 22
                  },
                  "end": {
                    "line": 1,
                    "character": 22
                  }
                }
              }
            }
            """;

    public CssCompletionClientConfigTest() {
        super("*.css");
    }

    // SINGLE ARGUMENT TESTS

    // language=json
    private static final String VAR_MOCK_COMPLETION_ITEM_RESOLVE_JSON = """
            {
              "label": "var()",
              "kind": 3,
              "documentation": "Evaluates the value of a custom variable.",
              "insertTextFormat": 2,
              "textEdit": {
                "range": {
                  "start": {
                    "line": 1,
                    "character": 22
                  },
                  "end": {
                    "line": 1,
                    "character": 22
                  }
                },
                "newText": "var($1)"
              },
              "command": {
                "title": "Suggest",
                "command": "editor.action.triggerSuggest"
              }
            }
            """;

    public void testUseTemplateForInvocationOnlySnippet_default_singleArg() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                // This one doesn't have a variable value, so it'll be placed within the parens
                """
                        .selector {
                            background-color: var(<caret>);
                        }
                        """,
                MOCK_TEXT_COMPLETION_JSON,
                VAR_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                // Default config
                null
        );
    }

    public void testUseTemplateForInvocationOnlySnippet_disabled_singleArg() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                """
                        .selector {
                            background-color: var(<caret>);
                        }
                        """,
                MOCK_TEXT_COMPLETION_JSON,
                VAR_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForInvocationOnlySnippet = false
        );
    }

    // MULTIPLE COMMA-DELIMITED ARGUMENTS TESTS

    // language=json
    private static final String RGB_MOCK_COMPLETION_ITEM_RESOLVE_JSON = """
            {
              "label": "rgb",
              "kind": 3,
              "detail": "rgb($red, $green, $blue)",
              "documentation": "Creates a Color from red, green, and blue values.",
              "insertTextFormat": 2,
              "textEdit": {
                "range": {
                  "start": {
                    "line": 1,
                    "character": 22
                  },
                  "end": {
                    "line": 1,
                    "character": 22
                  }
                },
                "newText": "rgb(${1:red}, ${2:green}, ${3:blue})"
              }
            }
            """;

    public void testUseTemplateForInvocationOnlySnippet_default_multipleArgs_commaDelimited() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                """
                        .selector {
                            background-color: rgb(red, green, blue);
                        }
                        """,
                MOCK_TEXT_COMPLETION_JSON,
                RGB_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                // Default config
                null
        );
    }

    public void testUseTemplateForInvocationOnlySnippet_disabled_multipleArgs_commaDelimited() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                """
                        .selector {
                            background-color: rgb(<caret>);
                        }
                        """,
                MOCK_TEXT_COMPLETION_JSON,
                RGB_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForInvocationOnlySnippet = false
        );
    }

    // MULTIPLE SPACE-DELIMITED ARGUMENTS TESTS

    // language=json
    private static final String HWB_MOCK_COMPLETION_ITEM_RESOLVE_JSON = """
            {
              "label": "hwb",
              "kind": 3,
              "detail": "hwb($hue $white $black)",
              "documentation": "Creates a Color from hue, white, and black values.",
              "insertTextFormat": 2,
              "textEdit": {
                "range": {
                  "start": {
                    "line": 1,
                    "character": 22
                  },
                  "end": {
                    "line": 1,
                    "character": 22
                  }
                },
                "newText": "hwb(${1:hue} ${2:white} ${3:black})"
              }
            }
            """;

    public void testUseTemplateForInvocationOnlySnippet_default_multipleArgs_spaceDelimited() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                """
                        .selector {
                            background-color: hwb(hue white black);
                        }
                        """,
                MOCK_TEXT_COMPLETION_JSON,
                HWB_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                // Default config
                null
        );
    }

    public void testUseTemplateForInvocationOnlySnippet_disabled_multipleArgs_spaceDelimited() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                """
                        .selector {
                            background-color: hwb(<caret>);
                        }
                        """,
                MOCK_TEXT_COMPLETION_JSON,
                HWB_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForInvocationOnlySnippet = false
        );
    }

    // COMPLEX ARGUMENT TESTS

    // language=json
    private static final String COMPLEX_MOCK_COMPLETION_ITEM_RESOLVE_JSON = """
            {
              "label": "color",
              "kind": 3,
              "detail": "color($color-space $red $green $blue)",
              "documentation": "Creates a Color in a specific color space from red, green, and blue values.",
              "insertTextFormat": 2,
              "textEdit": {
                "range": {
                  "start": {
                    "line": 1,
                    "character": 22
                  },
                  "end": {
                    "line": 1,
                    "character": 22
                  }
                },
                "newText": "color(${1|srgb,srgb-linear,display-p3,a98-rgb,prophoto-rgb,rec2020,xyx,xyz-d50,xyz-d65|} ${2:red} ${3:green} ${4:blue})"
              }
            }
            """;

    public void testUseTemplateForInvocationOnlySnippet_default_complex() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                """
                        .selector {
                            background-color: color(srgb red green blue);
                        }
                        """,
                MOCK_TEXT_COMPLETION_JSON,
                COMPLEX_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                // Default config
                null
        );
    }

    public void testUseTemplateForInvocationOnlySnippet_disabled_complex() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                """
                        .selector {
                            background-color: color(<caret>);
                        }
                        """,
                MOCK_TEXT_COMPLETION_JSON,
                COMPLEX_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForInvocationOnlySnippet = false
        );
    }

    // NON-ARGUMENT TAB STOP TESTS

    // language=json
    private static final String NON_ARGUMENT_TAB_STOPS_MOCK_COMPLETION_ITEM_RESOLVE_JSON = """
            {
              "label": "rgb relative",
              "kind": 3,
              "detail": "rgb(from $color $red $green $blue)",
              "documentation": "Creates a Color from the red, green, and blue values of another Color.",
              "insertTextFormat": 2,
              "textEdit": {
                "range": {
                  "start": {
                    "line": 1,
                    "character": 22
                  },
                  "end": {
                    "line": 1,
                    "character": 22
                  }
                },
                "newText": "rgb(from ${1:color} ${2:r} ${3:g} ${4:b})"
              }
            }
            """;

    public void testUseTemplateForInvocationOnlySnippet_default_nonArgumentTabStops() {
        assertTemplateForArguments(
                TEST_FILE_NAME,
                TEST_FILE_BODY_BEFORE,
                """
                        .selector {
                            background-color: rgb(from color r g b);
                        }
                        """,
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
                """
                        .selector {
                            background-color: rgb(from color r g b);
                        }
                        """,
                MOCK_TEXT_COMPLETION_JSON,
                NON_ARGUMENT_TAB_STOPS_MOCK_COMPLETION_ITEM_RESOLVE_JSON,
                clientConfig -> clientConfig.completion.useTemplateForInvocationOnlySnippet = false
        );
    }
}

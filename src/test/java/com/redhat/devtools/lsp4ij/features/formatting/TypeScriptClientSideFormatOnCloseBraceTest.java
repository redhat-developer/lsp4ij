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

package com.redhat.devtools.lsp4ij.features.formatting;

import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature.FormattingScope;
import com.redhat.devtools.lsp4ij.fixtures.LSPClientSideOnTypeFormattingFixtureTestCase;

/**
 * TypeScript-based client-side on-type formatting tests for format-on-close brace.
 */
public class TypeScriptClientSideFormatOnCloseBraceTest extends LSPClientSideOnTypeFormattingFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.ts";

    public TypeScriptClientSideFormatOnCloseBraceTest() {
        super("*.ts");
    }

    // SIMPLE TESTS

    // language=json
    private static final String SIMPLE_MOCK_SELECTION_RANGE_JSON = """
            [
              {
                "range": {
                  "start": {
                    "line": 1,
                    "character": 10
                  },
                  "end": {
                    "line": 4,
                    "character": 1
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 1,
                      "character": 4
                    },
                    "end": {
                      "line": 4,
                      "character": 1
                    }
                  },
                  "parent": {
                    "range": {
                      "start": {
                        "line": 0,
                        "character": 0
                      },
                      "end": {
                        "line": 4,
                        "character": 1
                      }
                    }
                  }
                }
              }
            ]
            """;

    // language=json
    private static final String SIMPLE_MOCK_FOLDING_RANGE_JSON = """
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

    // language=json
    private static final String SIMPLE_MOCK_RANGE_FORMATTING_JSON = """
            [
              {
                "range": {
                  "start": {
                    "line": 2,
                    "character": 0
                  },
                  "end": {
                    "line": 2,
                    "character": 0
                  }
                },
                "newText": "        "
              }
            ]
            """;

    // No language injection here because there are syntax errors
    private static final String SIMPLE_FILE_BODY_BEFORE = """
            export class Foo {
                bar() {
            console.log('Hello, world.');
                // type }
            }
            """;

    public void testSimpleDefaults() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                SIMPLE_FILE_BODY_BEFORE,
                // language=typescript
                """
                        export class Foo {
                            bar() {
                        console.log('Hello, world.');
                            }
                        }
                        """,
                SIMPLE_MOCK_SELECTION_RANGE_JSON,
                SIMPLE_MOCK_FOLDING_RANGE_JSON,
                SIMPLE_MOCK_RANGE_FORMATTING_JSON,
                null // No-op as the default is disabled
        );
    }

    public void testSimpleEnabled() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                SIMPLE_FILE_BODY_BEFORE,
                // language=typescript
                """
                        export class Foo {
                            bar() {
                                console.log('Hello, world.');
                            }
                        }
                        """,
                SIMPLE_MOCK_SELECTION_RANGE_JSON,
                SIMPLE_MOCK_FOLDING_RANGE_JSON,
                SIMPLE_MOCK_RANGE_FORMATTING_JSON,
                clientConfiguration -> clientConfiguration.format.onTypeFormatting.clientSide.formatOnCloseBrace = true
        );
    }

    public void testSimpleEnabledNoCurlyBrace() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                SIMPLE_FILE_BODY_BEFORE,
                // language=typescript
                """
                        export class Foo {
                            bar() {
                        console.log('Hello, world.');
                            }
                        }
                        """,
                SIMPLE_MOCK_SELECTION_RANGE_JSON,
                SIMPLE_MOCK_FOLDING_RANGE_JSON,
                SIMPLE_MOCK_RANGE_FORMATTING_JSON,
                clientConfiguration -> {
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnCloseBrace = true;
                    // Explicitly specify close brace characters that don't include right curly brace
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnCloseBraceCharacters = "])";
                }
        );
    }

    // BOUNDARY TESTS

    // language=json
    private static final String BOUNDARY_MOCK_SELECTION_RANGE_JSON = """
            [
              {
                "range": {
                  "start": {
                    "line": 1,
                    "character": 0
                  },
                  "end": {
                    "line": 1,
                    "character": 1
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 0,
                      "character": 15
                    },
                    "end": {
                      "line": 1,
                      "character": 1
                    }
                  },
                  "parent": {
                    "range": {
                      "start": {
                        "line": 0,
                        "character": 0
                      },
                      "end": {
                        "line": 1,
                        "character": 1
                      }
                    }
                  }
                }
              }
            ]
            """;

    // language=json
    private static final String BOUNDARY_MOCK_FOLDING_RANGE_JSON = "[]";

    // language=json
    private static final String BOUNDARY_MOCK_RANGE_FORMATTING_JSON = "[]";

    // No language injection here because there are syntax errors
    private static final String BOUNDARY_FILE_BODY_BEFORE = """
            function foo() {
            // type }"""; // NOTE: It's critical that this happen at the VERY end of the file to confirm the fix for 822

    // Confirms the fix for https://github.com/redhat-developer/lsp4ij/issues/822
    public void testCloseBraceAtEndOfFile() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                BOUNDARY_FILE_BODY_BEFORE,
                // language=typescript
                """
                        function foo() {
                        }""",
                BOUNDARY_MOCK_SELECTION_RANGE_JSON,
                BOUNDARY_MOCK_FOLDING_RANGE_JSON,
                BOUNDARY_MOCK_RANGE_FORMATTING_JSON,
                clientConfiguration -> clientConfiguration.format.onTypeFormatting.clientSide.formatOnCloseBrace = true
        );
    }

    // COMPLEX TESTS

    // language=json
    private static final String COMPLEX_MOCK_SELECTION_RANGE_JSON = """
            [
              {
                "range": {
                  "start": {
                    "line": 4,
                    "character": 0
                  },
                  "end": {
                    "line": 4,
                    "character": 1
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 0,
                      "character": 0
                    },
                    "end": {
                      "line": 4,
                      "character": 1
                    }
                  }
                }
              }
            ]
            """;

    // language=json
    private static final String COMPLEX_MOCK_FOLDING_RANGE_JSON = """
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

    // language=json
    private static final String COMPLEX_MOCK_RANGE_FORMATTING_JSON = """
            [
              {
                "range": {
                  "start": {
                    "line": 1,
                    "character": 0
                  },
                  "end": {
                    "line": 1,
                    "character": 0
                  }
                },
                "newText": "    "
              },
              {
                "range": {
                  "start": {
                    "line": 2,
                    "character": 0
                  },
                  "end": {
                    "line": 2,
                    "character": 0
                  }
                },
                "newText": "        "
              },
              {
                "range": {
                  "start": {
                    "line": 3,
                    "character": 0
                  },
                  "end": {
                    "line": 3,
                    "character": 0
                  }
                },
                "newText": "    "
              }
            ]
            """;

    // No language injection here because there are syntax errors
    private static final String COMPLEX_FILE_BODY_BEFORE = """
            export class Foo {
            bar() {
            console.log('Hello, world.');
            }
            // type }
            """;

    public void testComplexDefaults() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                COMPLEX_FILE_BODY_BEFORE,
                // language=typescript
                """
                        export class Foo {
                        bar() {
                        console.log('Hello, world.');
                        }
                        }
                        """,
                COMPLEX_MOCK_SELECTION_RANGE_JSON,
                COMPLEX_MOCK_FOLDING_RANGE_JSON,
                COMPLEX_MOCK_RANGE_FORMATTING_JSON,
                null // No-op as the default is disabled
        );
    }

    public void testComplexEnabled() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                COMPLEX_FILE_BODY_BEFORE,
                // language=typescript
                """
                        export class Foo {
                            bar() {
                                console.log('Hello, world.');
                            }
                        }
                        """,
                COMPLEX_MOCK_SELECTION_RANGE_JSON,
                COMPLEX_MOCK_FOLDING_RANGE_JSON,
                COMPLEX_MOCK_RANGE_FORMATTING_JSON,
                clientConfiguration -> clientConfiguration.format.onTypeFormatting.clientSide.formatOnCloseBrace = true
        );
    }

    public void testComplexEnabledNoCurlyBrace() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                COMPLEX_FILE_BODY_BEFORE,
                // language=typescript
                """
                        export class Foo {
                        bar() {
                        console.log('Hello, world.');
                        }
                        }
                        """,
                COMPLEX_MOCK_SELECTION_RANGE_JSON,
                COMPLEX_MOCK_FOLDING_RANGE_JSON,
                COMPLEX_MOCK_RANGE_FORMATTING_JSON,
                clientConfiguration -> {
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnCloseBrace = true;
                    // Explicitly specify close brace characters that don't include right curly brace
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnCloseBraceCharacters = "])";
                }
        );
    }

    // SCOPE TESTS

    public void testEnabledFileScope() {
        // language=json
        String mockRangeFormattingJson = """
                [
                  {
                    "range": {
                      "start": {
                        "line": 1,
                        "character": 0
                      },
                      "end": {
                        "line": 1,
                        "character": 0
                      }
                    },
                    "newText": "    "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 2,
                        "character": 0
                      },
                      "end": {
                        "line": 2,
                        "character": 0
                      }
                    },
                    "newText": "        "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 3,
                        "character": 0
                      },
                      "end": {
                        "line": 3,
                        "character": 0
                      }
                    },
                    "newText": "            "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 4,
                        "character": 0
                      },
                      "end": {
                        "line": 4,
                        "character": 0
                      }
                    },
                    "newText": "        "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 5,
                        "character": 0
                      },
                      "end": {
                        "line": 5,
                        "character": 0
                      }
                    },
                    "newText": "    "
                  }
                ]
                """;

        // No language injection here because there are syntax errors
        String fileBodyBefore = """
                export class Foo {
                bar() {
                if (true) {
                console.log('Hello, world.');
                // type }
                }
                }
                """;

        assertOnTypeFormatting(
                TEST_FILE_NAME,
                fileBodyBefore,
                // language=typescript
                """
                        export class Foo {
                            bar() {
                                if (true) {
                                    console.log('Hello, world.');
                                }
                            }
                        }
                        """,
                "[]",
                "[]",
                mockRangeFormattingJson,
                clientConfiguration -> {
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnCloseBrace = true;
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnCloseBraceScope = FormattingScope.FILE;
                }
        );
    }
}

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
 * Java-based client-side on-type formatting tests for format-on-close brace. Note that the jdtls range formatter does
 * not work well enough for format-on-statement terminator or format-on-completion trigger, so this is the only test
 * for Java/jdtls.
 */
public class JavaClientSideFormatOnCloseBraceTest extends LSPClientSideOnTypeFormattingFixtureTestCase {

    private static final String TEST_FILE_NAME = "Test.java";

    public JavaClientSideFormatOnCloseBraceTest() {
        super("*.java");
    }

    // SIMPLE TESTS

    // language=json
    private static final String SIMPLE_MOCK_SELECTION_RANGE_JSON = """
            [
              {
                "range": {
                  "start": {
                    "line": 1,
                    "character": 43
                  },
                  "end": {
                    "line": 3,
                    "character": 5
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 1,
                      "character": 4
                    },
                    "end": {
                      "line": 3,
                      "character": 5
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
                    },
                    "parent": {
                      "range": {
                        "start": {
                          "line": 0,
                          "character": 0
                        },
                        "end": {
                          "line": 5,
                          "character": 0
                        }
                      }
                    }
                  }
                }
              }
            ]
            """;

    // language=json
    private static final String SIMPLE_MOCK_FOLDING_RANGE_JSON = "[]";

    // language=json
    private static final String SIMPLE_MOCK_RANGE_FORMATTING_JSON = """
            [
              {
                "range": {
                  "start": {
                    "line": 1,
                    "character": 44
                  },
                  "end": {
                    "line": 2,
                    "character": 0
                  }
                },
                "newText": "\\n        "
              }
            ]
            """;

    // No language injection here because there are syntax errors
    private static final String SIMPLE_FILE_BODY_BEFORE = """
            public class Hello {
                public static void main(String[] args) {
            System.out.println("Hello, world.");
                // type }
            }
            """;

    public void testSimpleDefaults() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                SIMPLE_FILE_BODY_BEFORE,
                // language=java
                """
                        public class Hello {
                            public static void main(String[] args) {
                        System.out.println("Hello, world.");
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
                // language=java
                """
                        public class Hello {
                            public static void main(String[] args) {
                                System.out.println("Hello, world.");
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
                // language=java
                """
                        public class Hello {
                            public static void main(String[] args) {
                        System.out.println("Hello, world.");
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

    // COMPLEX TESTS

    // language=json
    private static final String COMPLEX_MOCK_SELECTION_RANGE_JSON = """
            [
              {
                "range": {
                  "start": {
                    "line": 0,
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
                      "line": 5,
                      "character": 0
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
                "endLine": 4
              },
              {
                "startLine": 1,
                "endLine": 3
              }
            ]
            """;

    // language=json
    private static final String COMPLEX_MOCK_RANGE_FORMATTING_JSON = """
            [
              {
                "range": {
                  "start": {
                    "line": 0,
                    "character": 20
                  },
                  "end": {
                    "line": 1,
                    "character": 0
                  }
                },
                "newText": "\\n    "
              },
              {
                "range": {
                  "start": {
                    "line": 1,
                    "character": 40
                  },
                  "end": {
                    "line": 2,
                    "character": 0
                  }
                },
                "newText": "\\n        "
              },
              {
                "range": {
                  "start": {
                    "line": 2,
                    "character": 36
                  },
                  "end": {
                    "line": 3,
                    "character": 0
                  }
                },
                "newText": "\\n    "
              }
            ]
            """;

    // No language injection here because there are syntax errors
    private static final String COMPLEX_FILE_BODY_BEFORE = """
            public class Hello {
            public static void main(String[] args) {
            System.out.println("Hello, world.");
            }
            // type }
            """;

    public void testComplexDefaults() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                COMPLEX_FILE_BODY_BEFORE,
                // language=java
                """
                        public class Hello {
                        public static void main(String[] args) {
                        System.out.println("Hello, world.");
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
                // language=java
                """
                        public class Hello {
                            public static void main(String[] args) {
                                System.out.println("Hello, world.");
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
                // language=java
                """
                        public class Hello {
                        public static void main(String[] args) {
                        System.out.println("Hello, world.");
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
                        "line": 0,
                        "character": 20
                      },
                      "end": {
                        "line": 1,
                        "character": 0
                      }
                    },
                    "newText": "\\n    "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 1,
                        "character": 40
                      },
                      "end": {
                        "line": 2,
                        "character": 0
                      }
                    },
                    "newText": "\\n        "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 2,
                        "character": 22
                      },
                      "end": {
                        "line": 3,
                        "character": 0
                      }
                    },
                    "newText": "\\n            "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 3,
                        "character": 36
                      },
                      "end": {
                        "line": 4,
                        "character": 0
                      }
                    },
                    "newText": "\\n        "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 4,
                        "character": 1
                      },
                      "end": {
                        "line": 5,
                        "character": 0
                      }
                    },
                    "newText": "\\n    "
                  }
                ]
                """;

        // No language injection here because there are syntax errors
        String fileBodyBefore = """
                public class Hello {
                public static void main(String[] args) {
                if (args.length > 0) {
                System.out.println("Hello, world.");
                // type }
                }
                }
                """;

        assertOnTypeFormatting(
                TEST_FILE_NAME,
                fileBodyBefore,
                // language=java
                """
                        public class Hello {
                            public static void main(String[] args) {
                                if (args.length > 0) {
                                    System.out.println("Hello, world.");
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

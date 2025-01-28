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

import com.redhat.devtools.lsp4ij.fixtures.LSPServerSideOnTypeFormattingFixtureTestCase;

/**
 * Java-based server-side on-type formatting tests for format-on-close brace. Note that jdtls' support for on-type
 * formatting does not seem to work well enough for format-on-statement terminator or newline even though those are
 * included as supported on-type formatting characters in the language server initialization response. Both of those
 * characters seem to yield empty text edit lists to be applied.
 */
public class JavaServerSideFormatOnCloseBraceTest extends LSPServerSideOnTypeFormattingFixtureTestCase {

    private static final String TEST_FILE_NAME = "Test.java";

    public JavaServerSideFormatOnCloseBraceTest() {
        super("*.java");
        // On-type formatting trigger characters for jdtls
        setTriggerCharacters(";", "\n", "}");
    }

    // Simple tests

    // No language injection here because there are syntax errors
    private static final String SIMPLE_FILE_BODY_BEFORE = """
            public class Hello {
                public static void main(String[] args) {
            System.out.println("Hello, world.");
                // type }
            }
            """;
    // language=json
    private static final String SIMPLE_MOCK_ON_TYPE_FORMATTING_JSON = """
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

    public void testSimple_default() {
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
                SIMPLE_MOCK_ON_TYPE_FORMATTING_JSON,
                // No-op as the default is enabled
                null
        );
    }

    public void testSimple_disabled() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                // No language injection here because there are syntax errors
                SIMPLE_FILE_BODY_BEFORE,
                // language=java
                """
                        public class Hello {
                            public static void main(String[] args) {
                        System.out.println("Hello, world.");
                            }
                        }
                        """,
                SIMPLE_MOCK_ON_TYPE_FORMATTING_JSON,
                clientConfig -> clientConfig.format.onTypeFormatting.serverSide.enabled = false
        );
    }

    // Complex tests

    // No language injection here because there are syntax errors
    private static final String COMPLEX_FILE_BODY_BEFORE = """
            public class Hello {
            public static void main(String[] args) {
            System.out.println("Hello, world.");
            }
            // type }
            """;

    // language=json
    private static final String COMPLEX_MOCK_ON_TYPE_FORMATTING_JSON = """
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

    public void testComplex_default() {
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
                COMPLEX_MOCK_ON_TYPE_FORMATTING_JSON,
                // No-op as the default is enabled
                null
        );
    }

    public void testComplex_disabled() {
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
                COMPLEX_MOCK_ON_TYPE_FORMATTING_JSON,
                clientConfig -> clientConfig.format.onTypeFormatting.serverSide.enabled = false
        );
    }
}

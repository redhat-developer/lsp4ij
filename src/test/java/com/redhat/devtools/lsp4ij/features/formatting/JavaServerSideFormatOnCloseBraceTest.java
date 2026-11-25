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

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.fixtures.LSPServerSideOnTypeFormattingFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Java-based server-side on-type formatting tests for format-on-close brace. Note that jdtls' support for on-type
 * formatting does not seem to work well enough for format-on-statement terminator or newline even though those are
 * included as supported on-type formatting characters in the language server initialization response. Both of those
 * characters seem to yield empty text edit lists to be applied.
 */
public class JavaServerSideFormatOnCloseBraceTest extends LSPServerSideOnTypeFormattingFixtureTestCase {

    private static final String JAVA_FILE_EXTENSION = "java";
    private static final String JAVA_FILE_NAME_PATTERN = "*." + JAVA_FILE_EXTENSION;
    private static final String TEST_FILE_NAME = "Test." + JAVA_FILE_EXTENSION;

    // How long the typing waits for the edits of the language server, in the tests whose server answers: as long as a
    // slow CI runner takes to answer. Once the 500 ms for a user are up, the document stays as typed.
    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(30);

    private FileType javaFileType;

    public JavaServerSideFormatOnCloseBraceTest() {
        super(JAVA_FILE_NAME_PATTERN);
        // On-type formatting trigger characters for jdtls
        setTriggerCharacters(";", "\n", "}");
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        // Remove the native Java file type association so that it doesn't interfere
        javaFileType = FileTypeManager.getInstance().getFileTypeByExtension(JAVA_FILE_EXTENSION);
        assertNotNull(javaFileType);
        WriteAction.run(() -> FileTypeManager.getInstance().removeAssociatedExtension(javaFileType, JAVA_FILE_EXTENSION));
        // IntelliJ formats a Java file itself, as the brace is typed, and asks no language server. The check names the
        // Java file type: a test before this one can have left .java with another file type, which the removal took
        assertNotSame("Test.java must not be a Java file", FileTypeManager.getInstance().findFileTypeByName("JAVA"),
                FileTypeManager.getInstance().getFileTypeByFileName(TEST_FILE_NAME));
    }

    @Override
    protected void tearDown() throws Exception
    {
        // Restore the native Java file type association
        WriteAction.run(() -> FileTypeManager.getInstance().associateExtension(javaFileType, JAVA_FILE_EXTENSION));
        super.tearDown();
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
        LSPServerSideOnTypeFormattingHelper.setOnTypeFormattingTimeout(ANSWER_TIMEOUT, getTestRootDisposable());
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
        LSPServerSideOnTypeFormattingHelper.setOnTypeFormattingTimeout(ANSWER_TIMEOUT, getTestRootDisposable());
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

    // Answer after the wait

    /**
     * An answer that comes once the typing stopped waiting for it, after 100 ms here, leaves the document as typed:
     * nothing applies its edits later.
     */
    public void testAnAnswerAfterTheWaitEditsNothing() throws Exception {
        // Short, as the typing waits for the held answer in its write action on the UI thread
        LSPServerSideOnTypeFormattingHelper.setOnTypeFormattingTimeout(Duration.ofMillis(100), getTestRootDisposable());
        var textDocumentService = MockLanguageServer.INSTANCE.getTextDocumentService();
        var release = textDocumentService.holdOnTypeFormatting();
        // language=java
        String typed = """
                public class Hello {
                    public static void main(String[] args) {
                System.out.println("Hello, world.");
                    }
                }
                """;
        try {
            assertOnTypeFormatting(TEST_FILE_NAME, SIMPLE_FILE_BODY_BEFORE, typed, SIMPLE_MOCK_ON_TYPE_FORMATTING_JSON, null);
            PlatformTestUtil.waitWithEventsDispatching("the language server must get the textDocument/onTypeFormatting "
                    + "request", () -> textDocumentService.onTypeFormattingRequests.get() == 1, IN_MEMORY_TIMEOUT_SECONDS);
        } finally {
            release.complete(null);
        }

        // The answer reaches LSP4IJ with its edits, which nothing applies
        var answer = LSPFileSupport.getSupport(myFixture.getFile()).getOnTypeFormattingSupport().getFuture();
        assertNotNull("the typing must ask the language server for its edits", answer);
        assertFalse("the answer must have the edits of the language server",
                answer.get(IN_MEMORY_TIMEOUT_SECONDS, TimeUnit.SECONDS).isEmpty());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertEquals("the edits that come after the wait must not be applied", typed,
                myFixture.getEditor().getDocument().getText());
    }
}

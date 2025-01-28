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

import com.redhat.devtools.lsp4ij.fixtures.LSPClientSideOnTypeFormattingFixtureTestCase;

/**
 * TypeScript-based client-side on-type formatting tests for format-on-completion trigger.
 */
public class TypeScriptClientSideFormatOnCompletionTriggerTest extends LSPClientSideOnTypeFormattingFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.ts";

    public TypeScriptClientSideFormatOnCompletionTriggerTest() {
        super("*.ts");
    }

    // language=json
    private static final String MOCK_RANGE_FORMATTING_JSON = """
            [
              {
                "range": {
                  "start": {
                    "line": 3,
                    "character": 0
                  },
                  "end": {
                    "line": 3,
                    "character": 8
                  }
                },
                "newText": "            "
              }
            ]
            """;

    private static final String FILE_BODY_BEFORE = """
            export class Foo {
                bar() {
                    invokePromise()
                    // type .
                }
            }
            """;

    public void testDefaults() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                FILE_BODY_BEFORE,
                """
                        export class Foo {
                            bar() {
                                invokePromise()
                                .
                            }
                        }
                        """,
                "[]",
                "[]",
                MOCK_RANGE_FORMATTING_JSON,
                null // No-op as the default is disabled
        );
    }

    public void testEnabled() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                FILE_BODY_BEFORE,
                """
                        export class Foo {
                            bar() {
                                invokePromise()
                                    .
                            }
                        }
                        """,
                "[]",
                "[]",
                MOCK_RANGE_FORMATTING_JSON,
                clientConfiguration -> clientConfiguration.format.onTypeFormatting.clientSide.formatOnCompletionTrigger = true
        );
    }

    public void testEnabledNoDotAsCompletionTrigger() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                FILE_BODY_BEFORE,
                """
                        export class Foo {
                            bar() {
                                invokePromise()
                                .
                            }
                        }
                        """,
                "[]",
                "[]",
                MOCK_RANGE_FORMATTING_JSON,
                clientConfiguration -> {
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnCompletionTrigger = true;
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnCompletionTriggerCharacters = "/";
                }
        );
    }
}

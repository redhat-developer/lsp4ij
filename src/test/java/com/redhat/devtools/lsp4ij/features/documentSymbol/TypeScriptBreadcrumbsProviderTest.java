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

package com.redhat.devtools.lsp4ij.features.documentSymbol;

import com.redhat.devtools.lsp4ij.fixtures.LSPBreadcrumbsProviderFixtureTestCase;

import java.util.List;
import java.util.Map;

/**
 * Tests the document symbol-based breadcrumbs info provider for TypeScript.
 */
public class TypeScriptBreadcrumbsProviderTest extends LSPBreadcrumbsProviderFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.ts";

    // language=typescript
    @SuppressWarnings("TypeScriptUnresolvedReference")
    private static final String TEST_FILE_BODY = """
            export class Foo {
                bar() {
                    invokePromise()
                        .then(() => {
                            console.log('test');
                        })
                        .catch((error) => {
                            console.error(error);
                        });
                }
            }
            """;

    // language=json
    private static final String MOCK_DOCUMENT_SYMBOL_JSON = """
            [
              {
                "name": "Foo",
                "kind": 5,
                "range": {
                  "start": {
                    "line": 0,
                    "character": 0
                  },
                  "end": {
                    "line": 10,
                    "character": 1
                  }
                },
                "selectionRange": {
                  "start": {
                    "line": 0,
                    "character": 13
                  },
                  "end": {
                    "line": 0,
                    "character": 16
                  }
                },
                "detail": "",
                "children": [
                  {
                    "name": "bar",
                    "kind": 6,
                    "range": {
                      "start": {
                        "line": 1,
                        "character": 4
                      },
                      "end": {
                        "line": 9,
                        "character": 5
                      }
                    },
                    "selectionRange": {
                      "start": {
                        "line": 1,
                        "character": 4
                      },
                      "end": {
                        "line": 1,
                        "character": 7
                      }
                    },
                    "detail": "",
                    "children": [
                      {
                        "name": "catch() callback",
                        "kind": 12,
                        "range": {
                          "start": {
                            "line": 6,
                            "character": 19
                          },
                          "end": {
                            "line": 8,
                            "character": 13
                          }
                        },
                        "selectionRange": {
                          "start": {
                            "line": 6,
                            "character": 19
                          },
                          "end": {
                            "line": 8,
                            "character": 13
                          }
                        },
                        "detail": "",
                        "children": []
                      },
                      {
                        "name": "then() callback",
                        "kind": 12,
                        "range": {
                          "start": {
                            "line": 3,
                            "character": 18
                          },
                          "end": {
                            "line": 5,
                            "character": 13
                          }
                        },
                        "selectionRange": {
                          "start": {
                            "line": 3,
                            "character": 18
                          },
                          "end": {
                            "line": 5,
                            "character": 13
                          }
                        },
                        "detail": "",
                        "children": []
                      }
                    ]
                  }
                ]
              }
            ]
            """;

    public TypeScriptBreadcrumbsProviderTest() {
        super("*.ts");
    }

    public void testEnabled() {
        assertBreadcrumbs(
                TEST_FILE_NAME,
                TEST_FILE_BODY,
                MOCK_DOCUMENT_SYMBOL_JSON,
                Map.ofEntries(
                        Map.entry("Foo", List.of("Foo")),
                        Map.entry("bar", List.of("Foo", "bar")),
                        Map.entry("console.log", List.of("Foo", "bar", "then() callback")),
                        Map.entry("console.error", List.of("Foo", "bar", "catch() callback"))
                )
        );
    }

    public void testDisabled() {
        assertBreadcrumbsDisabled(
                TEST_FILE_NAME,
                TEST_FILE_BODY,
                MOCK_DOCUMENT_SYMBOL_JSON
        );
    }
}

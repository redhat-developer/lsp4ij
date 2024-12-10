/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.selectionRange;

import com.redhat.devtools.lsp4ij.fixtures.LSPSelectionRangeFixtureTestCase;

/**
 * Selection range tests by emulating LSP 'textDocument/selectionRange' responses from the typescript-language-server.
 */
public class TypeScriptSelectionRangeTest extends LSPSelectionRangeFixtureTestCase {

    public TypeScriptSelectionRangeTest() {
        super("*.ts");
    }

    public void testSelectionRanges_qualifierExpression() {
        assertSelectionRanges(
                "demo.ts",
                // language=typescript
                "console.log('message');",
                // Start on the qualifier
                "console",
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 0,
                                "character": 0
                              },
                              "end": {
                                "line": 0,
                                "character": 7
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 0,
                                  "character": 0
                                },
                                "end": {
                                  "line": 0,
                                  "character": 11
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 0,
                                    "character": 0
                                  },
                                  "end": {
                                    "line": 0,
                                    "character": 22
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 0,
                                      "character": 0
                                    },
                                    "end": {
                                      "line": 0,
                                      "character": 23
                                    }
                                  }
                                }
                              }
                            }
                          }
                        ]
                        """,
                "console",
                "console.log",
                "console.log('message')",
                "console.log('message');"
        );
    }

    public void testSelectionRanges_callExpression() {
        assertSelectionRanges(
                "demo.ts",
                // language=typescript
                "console.log('message');",
                // Start at the call to log
                "log",
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 0,
                                "character": 8
                              },
                              "end": {
                                "line": 0,
                                "character": 11
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 0,
                                  "character": 0
                                },
                                "end": {
                                  "line": 0,
                                  "character": 11
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 0,
                                    "character": 0
                                  },
                                  "end": {
                                    "line": 0,
                                    "character": 22
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 0,
                                      "character": 0
                                    },
                                    "end": {
                                      "line": 0,
                                      "character": 23
                                    }
                                  }
                                }
                              }
                            }
                          }
                        ]
                        """,
                "log",
                "console.log",
                "console.log('message')",
                "console.log('message');"
        );
    }

    public void testSelectionRanges_stringLiteral() {
        assertSelectionRanges(
                "demo.ts",
                // language=typescript
                "console.log('message');",
                // Start in the string literal
                "message",
                // language=json
                """
                        [
                        {
                        "range": {
                          "start": {
                            "line": 0,
                            "character": 13
                          },
                          "end": {
                            "line": 0,
                            "character": 20
                          }
                        },
                        "parent": {
                          "range": {
                            "start": {
                              "line": 0,
                              "character": 12
                            },
                            "end": {
                              "line": 0,
                              "character": 21
                            }
                          },
                          "parent": {
                            "range": {
                              "start": {
                                "line": 0,
                                "character": 0
                              },
                              "end": {
                                "line": 0,
                                "character": 22
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 0,
                                  "character": 0
                                },
                                "end": {
                                  "line": 0,
                                  "character": 23
                                }
                              }
                            }
                          }
                        }
                        }
                        ]
                        """,
                "message",
                "'message'",
                "console.log('message')",
                "console.log('message');"
        );
    }
}

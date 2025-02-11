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
 * TypeScript-based client-side on-type formatting tests for format-on-statement terminator.
 */
public class TypeScriptClientSideFormatOnStatementTerminatorTest extends LSPClientSideOnTypeFormattingFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.ts";

    public TypeScriptClientSideFormatOnStatementTerminatorTest() {
        super("*.ts");
    }

    // SIMPLE TESTS

    // language=json
    private static final String SIMPLE_MOCK_SELECTION_RANGE_JSON = """
            [
              {
                "range": {
                  "start": {
                    "line": 2,
                    "character": 27
                  },
                  "end": {
                    "line": 2,
                    "character": 28
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 2,
                      "character": 0
                    },
                    "end": {
                      "line": 2,
                      "character": 28
                    }
                  },
                  "parent": {
                    "range": {
                      "start": {
                        "line": 1,
                        "character": 11
                      },
                      "end": {
                        "line": 3,
                        "character": 4
                      }
                    },
                    "parent": {
                      "range": {
                        "start": {
                          "line": 1,
                          "character": 10
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
                              "character": 18
                            },
                            "end": {
                              "line": 4,
                              "character": 0
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
                                  "line": 4,
                                  "character": 2
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
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

    // language=typescript
    private static final String SIMPLE_FILE_BODY_BEFORE = """
            export class Foo {
                bar() {
            console.log('Hello, world.')// type ;
                }
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
                "[]",
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
                "[]",
                SIMPLE_MOCK_RANGE_FORMATTING_JSON,
                clientConfiguration -> {
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnStatementTerminator = true;
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnStatementTerminatorCharacters = ";";
                }
        );
    }

    public void testSimpleEnabledNoStatementTerminators() {
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
                "[]",
                SIMPLE_MOCK_RANGE_FORMATTING_JSON,
                clientConfiguration -> clientConfiguration.format.onTypeFormatting.clientSide.formatOnStatementTerminator = true
        );
    }

    // COMPLEX TESTS

    // language=json
    private static final String COMPLEX_MOCK_SELECTION_RANGE_JSON = """
            [
              {
                "range": {
                  "start": {
                    "line": 8,
                    "character": 9
                  },
                  "end": {
                    "line": 8,
                    "character": 10
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 2,
                      "character": 8
                    },
                    "end": {
                      "line": 8,
                      "character": 10
                    }
                  },
                  "parent": {
                    "range": {
                      "start": {
                        "line": 1,
                        "character": 11
                      },
                      "end": {
                        "line": 9,
                        "character": 4
                      }
                    },
                    "parent": {
                      "range": {
                        "start": {
                          "line": 1,
                          "character": 10
                        },
                        "end": {
                          "line": 9,
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
                            "line": 9,
                            "character": 5
                          }
                        },
                        "parent": {
                          "range": {
                            "start": {
                              "line": 0,
                              "character": 18
                            },
                            "end": {
                              "line": 10,
                              "character": 0
                            }
                          },
                          "parent": {
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
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 0,
                                  "character": 0
                                },
                                "end": {
                                  "line": 10,
                                  "character": 2
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            ]
            """;

    // language=json
    private static final String COMPLEX_MOCK_FOLDING_RANGE_JSON = "[]";

    // language=json
    private static final String COMPLEX_MOCK_RANGE_FORMATTING_JSON = """
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
              },
              {
                "range": {
                  "start": {
                    "line": 4,
                    "character": 0
                  },
                  "end": {
                    "line": 4,
                    "character": 8
                  }
                },
                "newText": "                "
              },
              {
                "range": {
                  "start": {
                    "line": 5,
                    "character": 0
                  },
                  "end": {
                    "line": 5,
                    "character": 8
                  }
                },
                "newText": "            "
              },
              {
                "range": {
                  "start": {
                    "line": 6,
                    "character": 0
                  },
                  "end": {
                    "line": 6,
                    "character": 8
                  }
                },
                "newText": "            "
              },
              {
                "range": {
                  "start": {
                    "line": 7,
                    "character": 0
                  },
                  "end": {
                    "line": 7,
                    "character": 8
                  }
                },
                "newText": "                "
              },
              {
                "range": {
                  "start": {
                    "line": 8,
                    "character": 0
                  },
                  "end": {
                    "line": 8,
                    "character": 8
                  }
                },
                "newText": "            "
              }
            ]
            """;

    // No language injection here because there are syntax errors
    private static final String COMPLEX_FILE_BODY_BEFORE = """
            export class Foo {
                bar() {
                    invokePromise()
                    .then(() => {
                    console.log('test');
                    })
                    .catch((error) => {
                    console.error(error);
                    })// type ;
                }
            }
            """;

    public void testComplexDefaults() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                COMPLEX_FILE_BODY_BEFORE,
                """
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
                """
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
                        """,
                COMPLEX_MOCK_SELECTION_RANGE_JSON,
                COMPLEX_MOCK_FOLDING_RANGE_JSON,
                COMPLEX_MOCK_RANGE_FORMATTING_JSON,
                clientConfiguration -> {
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnStatementTerminator = true;
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnStatementTerminatorCharacters = ";";
                }
        );
    }

    public void testComplexEnabledNoStatementTerminators() {
        assertOnTypeFormatting(
                TEST_FILE_NAME,
                COMPLEX_FILE_BODY_BEFORE,
                """
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
                        """,
                COMPLEX_MOCK_SELECTION_RANGE_JSON,
                COMPLEX_MOCK_FOLDING_RANGE_JSON,
                COMPLEX_MOCK_RANGE_FORMATTING_JSON,
                clientConfiguration -> clientConfiguration.format.onTypeFormatting.clientSide.formatOnStatementTerminator = true
        );
    }

    // SCOPE TESTS

    public void testEnabledCodeBlockScope() {
        // language=json
        String mockSelectionRangeJson = """
                [
                  {
                    "range": {
                      "start": {
                        "line": 9,
                        "character": 9
                      },
                      "end": {
                        "line": 9,
                        "character": 10
                      }
                    },
                    "parent": {
                      "range": {
                        "start": {
                          "line": 3,
                          "character": 8
                        },
                        "end": {
                          "line": 9,
                          "character": 10
                        }
                      },
                      "parent": {
                        "range": {
                          "start": {
                            "line": 2,
                            "character": 19
                          },
                          "end": {
                            "line": 10,
                            "character": 8
                          }
                        },
                        "parent": {
                          "range": {
                            "start": {
                              "line": 2,
                              "character": 8
                            },
                            "end": {
                              "line": 10,
                              "character": 9
                            }
                          },
                          "parent": {
                            "range": {
                              "start": {
                                "line": 1,
                                "character": 11
                              },
                              "end": {
                                "line": 11,
                                "character": 4
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 1,
                                  "character": 10
                                },
                                "end": {
                                  "line": 11,
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
                                    "line": 11,
                                    "character": 5
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 0,
                                      "character": 18
                                    },
                                    "end": {
                                      "line": 12,
                                      "character": 0
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 0,
                                        "character": 0
                                      },
                                      "end": {
                                        "line": 12,
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
                                          "line": 12,
                                          "character": 2
                                        }
                                      }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                ]
                """;

        // language=json
        String mockRangeFormattingJson = """
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
                  },
                  {
                    "range": {
                      "start": {
                        "line": 4,
                        "character": 0
                      },
                      "end": {
                        "line": 4,
                        "character": 8
                      }
                    },
                    "newText": "                "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 5,
                        "character": 0
                      },
                      "end": {
                        "line": 5,
                        "character": 8
                      }
                    },
                    "newText": "                    "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 6,
                        "character": 0
                      },
                      "end": {
                        "line": 6,
                        "character": 8
                      }
                    },
                    "newText": "                "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 7,
                        "character": 0
                      },
                      "end": {
                        "line": 7,
                        "character": 8
                      }
                    },
                    "newText": "                "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 8,
                        "character": 0
                      },
                      "end": {
                        "line": 8,
                        "character": 8
                      }
                    },
                    "newText": "                    "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 9,
                        "character": 0
                      },
                      "end": {
                        "line": 9,
                        "character": 8
                      }
                    },
                    "newText": "                "
                  }
                ]
                """;

        // No language injection here because there are syntax errors
        String fileBodyBefore = """
                export class Foo {
                    bar() {
                        if (true) {
                        invokePromise()
                        .then(() => {
                        console.log('test');
                        })
                        .catch((error) => {
                        console.error(error);
                        })// type ;
                        }
                    }
                }
                """;

        assertOnTypeFormatting(
                TEST_FILE_NAME,
                fileBodyBefore,
                """
                        export class Foo {
                            bar() {
                                if (true) {
                                    invokePromise()
                                        .then(() => {
                                            console.log('test');
                                        })
                                        .catch((error) => {
                                            console.error(error);
                                        });
                                }
                            }
                        }
                        """,
                mockSelectionRangeJson,
                "[]",
                mockRangeFormattingJson,
                clientConfiguration -> {
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnStatementTerminator = true;
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnStatementTerminatorCharacters = ";";
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnStatementTerminatorScope = FormattingScope.CODE_BLOCK;
                }
        );
    }

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
                    "newText": "                "
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
                    "newText": "                    "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 6,
                        "character": 0
                      },
                      "end": {
                        "line": 6,
                        "character": 0
                      }
                    },
                    "newText": "                "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 7,
                        "character": 0
                      },
                      "end": {
                        "line": 7,
                        "character": 0
                      }
                    },
                    "newText": "                "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 8,
                        "character": 0
                      },
                      "end": {
                        "line": 8,
                        "character": 0
                      }
                    },
                    "newText": "                    "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 9,
                        "character": 0
                      },
                      "end": {
                        "line": 9,
                        "character": 0
                      }
                    },
                    "newText": "                "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 10,
                        "character": 0
                      },
                      "end": {
                        "line": 10,
                        "character": 0
                      }
                    },
                    "newText": "        "
                  },
                  {
                    "range": {
                      "start": {
                        "line": 11,
                        "character": 0
                      },
                      "end": {
                        "line": 11,
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
                invokePromise()
                .then(() => {
                console.log('test');
                })
                .catch((error) => {
                console.error(error);
                })// type ;
                }
                }
                }
                """;

        assertOnTypeFormatting(
                TEST_FILE_NAME,
                fileBodyBefore,
                """
                        export class Foo {
                            bar() {
                                if (true) {
                                    invokePromise()
                                        .then(() => {
                                            console.log('test');
                                        })
                                        .catch((error) => {
                                            console.error(error);
                                        });
                                }
                            }
                        }
                        """,
                "[]",
                "[]",
                mockRangeFormattingJson,
                clientConfiguration -> {
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnStatementTerminator = true;
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnStatementTerminatorCharacters = ";";
                    clientConfiguration.format.onTypeFormatting.clientSide.formatOnStatementTerminatorScope = FormattingScope.FILE;
                }
        );
    }
}

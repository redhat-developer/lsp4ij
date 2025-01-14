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

package com.redhat.devtools.lsp4ij.features.foldingRange;

import com.redhat.devtools.lsp4ij.fixtures.LSPCodeBlockProviderFixtureTestCase;

/**
 * Selection range tests by emulating LSP 'textDocument/foldingRange' responses from the typescript-language-server.
 */
public class TypeScriptCodeBlockProviderTest extends LSPCodeBlockProviderFixtureTestCase {

    public TypeScriptCodeBlockProviderTest() {
        super("*.ts");
    }

    // language=json
    private static final String SIMPLE_MOCK_FOLDING_RANGES_JSON = """
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

    public void testSimpleMethodBodyCaretBeforeStatement() {
        assertCodeBlock(
                "demo.ts",
                """
                        export class Demo {
                            demo() {<start>
                                <caret>console.log('demo');
                            <end>}
                        }
                        """,
                SIMPLE_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 2,
                                "character": 8
                              },
                              "end": {
                                "line": 2,
                                "character": 15
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 2,
                                  "character": 8
                                },
                                "end": {
                                  "line": 2,
                                  "character": 19
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 2,
                                    "character": 8
                                  },
                                  "end": {
                                    "line": 2,
                                    "character": 27
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 2,
                                      "character": 8
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
                                        "character": 12
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
                                          "character": 11
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
                                              "character": 19
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
                            }
                          }
                        ]
                        """);
    }

    public void testSimpleClassBodyCaretBeforeMethod() {
        assertCodeBlock(
                "demo.ts",
                """
                        export class Demo {<start>
                            <caret>demo() {
                                console.log('demo');
                            }
                        <end>}
                        """,
                SIMPLE_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 1,
                                "character": 4
                              },
                              "end": {
                                "line": 1,
                                "character": 8
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
                                    "character": 19
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
                        ]
                        """);
    }

    public void testSimpleClassBodyCaretBeforeOpenBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        export class Demo <caret>{<start>
                            demo() {
                                console.log('demo');
                            }
                        <end>}
                        """,
                SIMPLE_MOCK_FOLDING_RANGES_JSON,
                // language=json
                "[]");
    }

    public void testSimpleClassBodyCaretAfterOpenBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        export class Demo {<caret><start>
                            demo() {
                                console.log('demo');
                            }
                        <end>}
                        """,
                SIMPLE_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 0,
                                "character": 18
                              },
                              "end": {
                                "line": 0,
                                "character": 19
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
                        ]
                        """);
    }

    public void testSimpleClassBodyCaretBeforeCloseBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        export class Demo {<start>
                            demo() {
                                console.log('demo');
                            }
                        <end><caret>}
                        """,
                SIMPLE_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
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
                        ]
                        """);
    }

    public void testSimpleClassBodyCaretAfterCloseBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        export class Demo {<start>
                            demo() {
                                console.log('demo');
                            }
                        <end>}<caret>
                        """,
                SIMPLE_MOCK_FOLDING_RANGES_JSON,
                // language=json
                "[]");
    }

    public void testSimpleCaretBeforeClassName() {
        assertCodeBlock(
                "demo.ts",
                """
                        export class <caret><start><end>Demo {
                            demo() {
                                console.log('demo');
                            }
                        }
                        """,
                SIMPLE_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 0,
                                "character": 7
                              },
                              "end": {
                                "line": 0,
                                "character": 12
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
                        ]
                        """);
    }

    // language=json
    private static final String COMPLEX_MOCK_FOLDING_RANGES_JSON = """
            [
              {
                "startLine": 0,
                "endLine": 7
              },
              {
                "startLine": 1,
                "endLine": 1
              },
              {
                "startLine": 2,
                "endLine": 4
              },
              {
                "startLine": 2,
                "endLine": 3
              },
              {
                "startLine": 5,
                "endLine": 7
              },
              {
                "startLine": 5,
                "endLine": 6
              }
            ]
            """;

    public void testComplexCaretAfterOutermostOpenBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {<caret><start>
                            invokePromise({foo: '', bar: 10})
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => {
                                    console.error(error);
                                });
                        <end>}
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 0,
                                "character": 15
                              },
                              "end": {
                                "line": 0,
                                "character": 16
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 0,
                                  "character": 0
                                },
                                "end": {
                                  "line": 8,
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
                                    "line": 8,
                                    "character": 2
                                  }
                                }
                              }
                            }
                          }
                        ]
                        """);
    }

    public void testComplexCaretBeforeOutermostOpenBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) <caret>{<start>
                            invokePromise({foo: '', bar: 10})
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => {
                                    console.error(error);
                                });
                        <end>}
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 0,
                                "character": 15
                              },
                              "end": {
                                "line": 0,
                                "character": 16
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 0,
                                  "character": 0
                                },
                                "end": {
                                  "line": 8,
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
                                    "line": 8,
                                    "character": 2
                                  }
                                }
                              }
                            }
                          }
                        ]
                        """);
    }

    public void testComplexCaretBeforeOutermostCloseBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {<start>
                            invokePromise({foo: '', bar: 10})
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => {
                                    console.error(error);
                                });
                        <caret><end>}
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 8,
                                "character": 0
                              },
                              "end": {
                                "line": 8,
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
                                  "line": 8,
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
                                    "line": 8,
                                    "character": 2
                                  }
                                }
                              }
                            }
                          }
                        ]
                        """);
    }

    public void testComplexCaretAfterOutermostCloseBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {<start>
                            invokePromise({foo: '', bar: 10})
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => {
                                    console.error(error);
                                });
                        <end>}<caret>
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 8,
                                "character": 0
                              },
                              "end": {
                                "line": 8,
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
                                  "line": 8,
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
                                    "line": 8,
                                    "character": 2
                                  }
                                }
                              }
                            }
                          }
                        ]
                        """);
    }

    public void testComplexCaretBeforeOutermostBlockStatement() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {<start>
                            <caret>invokePromise({foo: '', bar: 10})
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => {
                                    console.error(error);
                                });
                        <end>}
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 1,
                                "character": 4
                              },
                              "end": {
                                "line": 1,
                                "character": 17
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 1,
                                  "character": 4
                                },
                                "end": {
                                  "line": 1,
                                  "character": 37
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 1,
                                    "character": 4
                                  },
                                  "end": {
                                    "line": 2,
                                    "character": 13
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
                                      "character": 10
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 5,
                                        "character": 14
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 1,
                                          "character": 4
                                        },
                                        "end": {
                                          "line": 7,
                                          "character": 10
                                        }
                                      },
                                      "parent": {
                                        "range": {
                                          "start": {
                                            "line": 1,
                                            "character": 4
                                          },
                                          "end": {
                                            "line": 7,
                                            "character": 11
                                          }
                                        },
                                        "parent": {
                                          "range": {
                                            "start": {
                                              "line": 0,
                                              "character": 16
                                            },
                                            "end": {
                                              "line": 8,
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
                                                "line": 8,
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
                                                  "line": 8,
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
                        """);
    }

    public void testComplexCaretAfterObjectLiteralOpenBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise(<start>{<caret>foo: '', bar: 10}<end>)
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => {
                                    console.error(error);
                                });
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 1,
                                "character": 19
                              },
                              "end": {
                                "line": 1,
                                "character": 22
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 1,
                                  "character": 19
                                },
                                "end": {
                                  "line": 1,
                                  "character": 26
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 1,
                                    "character": 19
                                  },
                                  "end": {
                                    "line": 1,
                                    "character": 35
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 18
                                    },
                                    "end": {
                                      "line": 1,
                                      "character": 36
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 1,
                                        "character": 37
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 1,
                                          "character": 4
                                        },
                                        "end": {
                                          "line": 2,
                                          "character": 13
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
                                            "character": 10
                                          }
                                        },
                                        "parent": {
                                          "range": {
                                            "start": {
                                              "line": 1,
                                              "character": 4
                                            },
                                            "end": {
                                              "line": 5,
                                              "character": 14
                                            }
                                          },
                                          "parent": {
                                            "range": {
                                              "start": {
                                                "line": 1,
                                                "character": 4
                                              },
                                              "end": {
                                                "line": 7,
                                                "character": 10
                                              }
                                            },
                                            "parent": {
                                              "range": {
                                                "start": {
                                                  "line": 1,
                                                  "character": 4
                                                },
                                                "end": {
                                                  "line": 7,
                                                  "character": 11
                                                }
                                              },
                                              "parent": {
                                                "range": {
                                                  "start": {
                                                    "line": 0,
                                                    "character": 16
                                                  },
                                                  "end": {
                                                    "line": 8,
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
                                                      "line": 8,
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
                                                        "line": 8,
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
                              }
                            }
                          }
                        ]
                        """);
    }

    public void testComplexCaretBeforeObjectLiteralOpenBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise(<caret><start>{foo: '', bar: 10}<end>)
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => {
                                    console.error(error);
                                });
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 1,
                                "character": 19
                              },
                              "end": {
                                "line": 1,
                                "character": 22
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 1,
                                  "character": 19
                                },
                                "end": {
                                  "line": 1,
                                  "character": 26
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 1,
                                    "character": 19
                                  },
                                  "end": {
                                    "line": 1,
                                    "character": 35
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 18
                                    },
                                    "end": {
                                      "line": 1,
                                      "character": 36
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 1,
                                        "character": 37
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 1,
                                          "character": 4
                                        },
                                        "end": {
                                          "line": 2,
                                          "character": 13
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
                                            "character": 10
                                          }
                                        },
                                        "parent": {
                                          "range": {
                                            "start": {
                                              "line": 1,
                                              "character": 4
                                            },
                                            "end": {
                                              "line": 5,
                                              "character": 14
                                            }
                                          },
                                          "parent": {
                                            "range": {
                                              "start": {
                                                "line": 1,
                                                "character": 4
                                              },
                                              "end": {
                                                "line": 7,
                                                "character": 10
                                              }
                                            },
                                            "parent": {
                                              "range": {
                                                "start": {
                                                  "line": 1,
                                                  "character": 4
                                                },
                                                "end": {
                                                  "line": 7,
                                                  "character": 11
                                                }
                                              },
                                              "parent": {
                                                "range": {
                                                  "start": {
                                                    "line": 0,
                                                    "character": 16
                                                  },
                                                  "end": {
                                                    "line": 8,
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
                                                      "line": 8,
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
                                                        "line": 8,
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
                              }
                            }
                          }
                        ]
                        """);
    }

    public void testComplexCaretBeforeObjectLiteralCloseBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise(<start>{foo: '', bar: 10<caret>}<end>)
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => {
                                    console.error(error);
                                });
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 1,
                                "character": 33
                              },
                              "end": {
                                "line": 1,
                                "character": 35
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 1,
                                  "character": 28
                                },
                                "end": {
                                  "line": 1,
                                  "character": 35
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 1,
                                    "character": 19
                                  },
                                  "end": {
                                    "line": 1,
                                    "character": 35
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 18
                                    },
                                    "end": {
                                      "line": 1,
                                      "character": 36
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 1,
                                        "character": 37
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 1,
                                          "character": 4
                                        },
                                        "end": {
                                          "line": 2,
                                          "character": 13
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
                                            "character": 10
                                          }
                                        },
                                        "parent": {
                                          "range": {
                                            "start": {
                                              "line": 1,
                                              "character": 4
                                            },
                                            "end": {
                                              "line": 5,
                                              "character": 14
                                            }
                                          },
                                          "parent": {
                                            "range": {
                                              "start": {
                                                "line": 1,
                                                "character": 4
                                              },
                                              "end": {
                                                "line": 7,
                                                "character": 10
                                              }
                                            },
                                            "parent": {
                                              "range": {
                                                "start": {
                                                  "line": 1,
                                                  "character": 4
                                                },
                                                "end": {
                                                  "line": 7,
                                                  "character": 11
                                                }
                                              },
                                              "parent": {
                                                "range": {
                                                  "start": {
                                                    "line": 0,
                                                    "character": 16
                                                  },
                                                  "end": {
                                                    "line": 8,
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
                                                      "line": 8,
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
                                                        "line": 8,
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
                              }
                            }
                          }
                        ]
                        """);
    }

    public void testComplexCaretAfterObjectLiteralCloseBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise(<start>{foo: '', bar: 10}<end><caret>)
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => {
                                    console.error(error);
                                });
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 1,
                                "character": 33
                              },
                              "end": {
                                "line": 1,
                                "character": 35
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 1,
                                  "character": 28
                                },
                                "end": {
                                  "line": 1,
                                  "character": 35
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 1,
                                    "character": 19
                                  },
                                  "end": {
                                    "line": 1,
                                    "character": 35
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 18
                                    },
                                    "end": {
                                      "line": 1,
                                      "character": 36
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 1,
                                        "character": 37
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 1,
                                          "character": 4
                                        },
                                        "end": {
                                          "line": 2,
                                          "character": 13
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
                                            "character": 10
                                          }
                                        },
                                        "parent": {
                                          "range": {
                                            "start": {
                                              "line": 1,
                                              "character": 4
                                            },
                                            "end": {
                                              "line": 5,
                                              "character": 14
                                            }
                                          },
                                          "parent": {
                                            "range": {
                                              "start": {
                                                "line": 1,
                                                "character": 4
                                              },
                                              "end": {
                                                "line": 7,
                                                "character": 10
                                              }
                                            },
                                            "parent": {
                                              "range": {
                                                "start": {
                                                  "line": 1,
                                                  "character": 4
                                                },
                                                "end": {
                                                  "line": 7,
                                                  "character": 11
                                                }
                                              },
                                              "parent": {
                                                "range": {
                                                  "start": {
                                                    "line": 0,
                                                    "character": 16
                                                  },
                                                  "end": {
                                                    "line": 8,
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
                                                      "line": 8,
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
                                                        "line": 8,
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
                              }
                            }
                          }
                        ]
                        """);
    }

    public void testComplexCaretBeforeObjectLiteralProperty() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise({<start>foo: '', <caret>bar: 10<end>})
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => {
                                    console.error(error);
                                });
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 1,
                                "character": 28
                              },
                              "end": {
                                "line": 1,
                                "character": 31
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 1,
                                  "character": 28
                                },
                                "end": {
                                  "line": 1,
                                  "character": 35
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 1,
                                    "character": 19
                                  },
                                  "end": {
                                    "line": 1,
                                    "character": 35
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 18
                                    },
                                    "end": {
                                      "line": 1,
                                      "character": 36
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 1,
                                        "character": 37
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 1,
                                          "character": 4
                                        },
                                        "end": {
                                          "line": 2,
                                          "character": 13
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
                                            "character": 10
                                          }
                                        },
                                        "parent": {
                                          "range": {
                                            "start": {
                                              "line": 1,
                                              "character": 4
                                            },
                                            "end": {
                                              "line": 5,
                                              "character": 14
                                            }
                                          },
                                          "parent": {
                                            "range": {
                                              "start": {
                                                "line": 1,
                                                "character": 4
                                              },
                                              "end": {
                                                "line": 7,
                                                "character": 10
                                              }
                                            },
                                            "parent": {
                                              "range": {
                                                "start": {
                                                  "line": 1,
                                                  "character": 4
                                                },
                                                "end": {
                                                  "line": 7,
                                                  "character": 11
                                                }
                                              },
                                              "parent": {
                                                "range": {
                                                  "start": {
                                                    "line": 0,
                                                    "character": 16
                                                  },
                                                  "end": {
                                                    "line": 8,
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
                                                      "line": 8,
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
                                                        "line": 8,
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
                              }
                            }
                          }
                        ]
                        """);
    }

    public void testComplexCaretAfterPromiseThenOpenBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise({foo: '', bar: 10})
                                .then(() => <start>{<caret>
                                    console.log('demo');
                                }<end>)
                                .catch((error) => {
                                    console.error(error);
                                });
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 2,
                                "character": 20
                              },
                              "end": {
                                "line": 2,
                                "character": 21
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 2,
                                  "character": 20
                                },
                                "end": {
                                  "line": 4,
                                  "character": 9
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 2,
                                    "character": 14
                                  },
                                  "end": {
                                    "line": 4,
                                    "character": 9
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
                                      "character": 10
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 5,
                                        "character": 14
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 1,
                                          "character": 4
                                        },
                                        "end": {
                                          "line": 7,
                                          "character": 10
                                        }
                                      },
                                      "parent": {
                                        "range": {
                                          "start": {
                                            "line": 1,
                                            "character": 4
                                          },
                                          "end": {
                                            "line": 7,
                                            "character": 11
                                          }
                                        },
                                        "parent": {
                                          "range": {
                                            "start": {
                                              "line": 0,
                                              "character": 16
                                            },
                                            "end": {
                                              "line": 8,
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
                                                "line": 8,
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
                                                  "line": 8,
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
                        """);
    }

    public void testComplexCaretBeforePromiseThenOpenBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise({foo: '', bar: 10})
                                .then(() => <start><caret>{
                                    console.log('demo');
                                }<end>)
                                .catch((error) => {
                                    console.error(error);
                                });
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 2,
                                "character": 20
                              },
                              "end": {
                                "line": 2,
                                "character": 21
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 2,
                                  "character": 20
                                },
                                "end": {
                                  "line": 4,
                                  "character": 9
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 2,
                                    "character": 14
                                  },
                                  "end": {
                                    "line": 4,
                                    "character": 9
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
                                      "character": 10
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 5,
                                        "character": 14
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 1,
                                          "character": 4
                                        },
                                        "end": {
                                          "line": 7,
                                          "character": 10
                                        }
                                      },
                                      "parent": {
                                        "range": {
                                          "start": {
                                            "line": 1,
                                            "character": 4
                                          },
                                          "end": {
                                            "line": 7,
                                            "character": 11
                                          }
                                        },
                                        "parent": {
                                          "range": {
                                            "start": {
                                              "line": 0,
                                              "character": 16
                                            },
                                            "end": {
                                              "line": 8,
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
                                                "line": 8,
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
                                                  "line": 8,
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
                        """);
    }

    public void testComplexCaretBeforePromiseThenCloseBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise({foo: '', bar: 10})
                                .then(() => <start>{
                                    console.log('demo');
                                <caret>}<end>)
                                .catch((error) => {
                                    console.error(error);
                                });
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 4,
                                "character": 8
                              },
                              "end": {
                                "line": 4,
                                "character": 9
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 2,
                                  "character": 20
                                },
                                "end": {
                                  "line": 4,
                                  "character": 9
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 2,
                                    "character": 14
                                  },
                                  "end": {
                                    "line": 4,
                                    "character": 9
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
                                      "character": 10
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 5,
                                        "character": 14
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 1,
                                          "character": 4
                                        },
                                        "end": {
                                          "line": 7,
                                          "character": 10
                                        }
                                      },
                                      "parent": {
                                        "range": {
                                          "start": {
                                            "line": 1,
                                            "character": 4
                                          },
                                          "end": {
                                            "line": 7,
                                            "character": 11
                                          }
                                        },
                                        "parent": {
                                          "range": {
                                            "start": {
                                              "line": 0,
                                              "character": 16
                                            },
                                            "end": {
                                              "line": 8,
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
                                                "line": 8,
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
                                                  "line": 8,
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
                        """);
    }

    public void testComplexCaretAfterPromiseThenCloseBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise({foo: '', bar: 10})
                                .then(() => <start>{
                                    console.log('demo');
                                }<caret><end>)
                                .catch((error) => {
                                    console.error(error);
                                });
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 4,
                                "character": 8
                              },
                              "end": {
                                "line": 4,
                                "character": 9
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 2,
                                  "character": 20
                                },
                                "end": {
                                  "line": 4,
                                  "character": 9
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 2,
                                    "character": 14
                                  },
                                  "end": {
                                    "line": 4,
                                    "character": 9
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
                                      "character": 10
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 5,
                                        "character": 14
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 1,
                                          "character": 4
                                        },
                                        "end": {
                                          "line": 7,
                                          "character": 10
                                        }
                                      },
                                      "parent": {
                                        "range": {
                                          "start": {
                                            "line": 1,
                                            "character": 4
                                          },
                                          "end": {
                                            "line": 7,
                                            "character": 11
                                          }
                                        },
                                        "parent": {
                                          "range": {
                                            "start": {
                                              "line": 0,
                                              "character": 16
                                            },
                                            "end": {
                                              "line": 8,
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
                                                "line": 8,
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
                                                  "line": 8,
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
                        """);
    }

    public void testComplexCaretBeforePromiseThenStatement() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise({foo: '', bar: 10})
                                .then(() => {<start>
                                    <caret>console.log('demo');
                                <end>})
                                .catch((error) => {
                                    console.error(error);
                                });
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 3,
                                "character": 12
                              },
                              "end": {
                                "line": 3,
                                "character": 19
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 3,
                                  "character": 12
                                },
                                "end": {
                                  "line": 3,
                                  "character": 23
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 3,
                                    "character": 12
                                  },
                                  "end": {
                                    "line": 3,
                                    "character": 31
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 3,
                                      "character": 12
                                    },
                                    "end": {
                                      "line": 3,
                                      "character": 32
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 2,
                                        "character": 21
                                      },
                                      "end": {
                                        "line": 4,
                                        "character": 8
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 2,
                                          "character": 20
                                        },
                                        "end": {
                                          "line": 4,
                                          "character": 9
                                        }
                                      },
                                      "parent": {
                                        "range": {
                                          "start": {
                                            "line": 2,
                                            "character": 14
                                          },
                                          "end": {
                                            "line": 4,
                                            "character": 9
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
                                              "character": 10
                                            }
                                          },
                                          "parent": {
                                            "range": {
                                              "start": {
                                                "line": 1,
                                                "character": 4
                                              },
                                              "end": {
                                                "line": 5,
                                                "character": 14
                                              }
                                            },
                                            "parent": {
                                              "range": {
                                                "start": {
                                                  "line": 1,
                                                  "character": 4
                                                },
                                                "end": {
                                                  "line": 7,
                                                  "character": 10
                                                }
                                              },
                                              "parent": {
                                                "range": {
                                                  "start": {
                                                    "line": 1,
                                                    "character": 4
                                                  },
                                                  "end": {
                                                    "line": 7,
                                                    "character": 11
                                                  }
                                                },
                                                "parent": {
                                                  "range": {
                                                    "start": {
                                                      "line": 0,
                                                      "character": 16
                                                    },
                                                    "end": {
                                                      "line": 8,
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
                                                        "line": 8,
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
                                                          "line": 8,
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
                                }
                              }
                            }
                          }
                        ]
                        """);
    }

    public void testComplexCaretAfterPromiseCatchOpenBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise({foo: '', bar: 10})
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => <start>{<caret>
                                    console.error(error);
                                }<end>);
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 5,
                                "character": 26
                              },
                              "end": {
                                "line": 5,
                                "character": 27
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 5,
                                  "character": 26
                                },
                                "end": {
                                  "line": 7,
                                  "character": 9
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 5,
                                    "character": 15
                                  },
                                  "end": {
                                    "line": 7,
                                    "character": 9
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 4
                                    },
                                    "end": {
                                      "line": 7,
                                      "character": 10
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 7,
                                        "character": 11
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 0,
                                          "character": 16
                                        },
                                        "end": {
                                          "line": 8,
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
                                            "line": 8,
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
                                              "line": 8,
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
                        """);
    }

    public void testComplexCaretBeforePromiseCatchOpenBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise({foo: '', bar: 10})
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => <start><caret>{
                                    console.error(error);
                                }<end>);
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 5,
                                "character": 26
                              },
                              "end": {
                                "line": 5,
                                "character": 27
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 5,
                                  "character": 26
                                },
                                "end": {
                                  "line": 7,
                                  "character": 9
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 5,
                                    "character": 15
                                  },
                                  "end": {
                                    "line": 7,
                                    "character": 9
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 4
                                    },
                                    "end": {
                                      "line": 7,
                                      "character": 10
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 7,
                                        "character": 11
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 0,
                                          "character": 16
                                        },
                                        "end": {
                                          "line": 8,
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
                                            "line": 8,
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
                                              "line": 8,
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
                        """);
    }

    public void testComplexCaretBeforePromiseCatchCloseBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise({foo: '', bar: 10})
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => <start>{
                                    console.error(error);
                                <caret>}<end>);
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 7,
                                "character": 8
                              },
                              "end": {
                                "line": 7,
                                "character": 9
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 5,
                                  "character": 26
                                },
                                "end": {
                                  "line": 7,
                                  "character": 9
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 5,
                                    "character": 15
                                  },
                                  "end": {
                                    "line": 7,
                                    "character": 9
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 4
                                    },
                                    "end": {
                                      "line": 7,
                                      "character": 10
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 7,
                                        "character": 11
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 0,
                                          "character": 16
                                        },
                                        "end": {
                                          "line": 8,
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
                                            "line": 8,
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
                                              "line": 8,
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
                        """);
    }

    public void testComplexCaretAfterPromiseCatchCloseBrace() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise({foo: '', bar: 10})
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => <start>{
                                    console.error(error);
                                }<caret><end>);
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 7,
                                "character": 8
                              },
                              "end": {
                                "line": 7,
                                "character": 9
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 5,
                                  "character": 26
                                },
                                "end": {
                                  "line": 7,
                                  "character": 9
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 5,
                                    "character": 15
                                  },
                                  "end": {
                                    "line": 7,
                                    "character": 9
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 4
                                    },
                                    "end": {
                                      "line": 7,
                                      "character": 10
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 1,
                                        "character": 4
                                      },
                                      "end": {
                                        "line": 7,
                                        "character": 11
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 0,
                                          "character": 16
                                        },
                                        "end": {
                                          "line": 8,
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
                                            "line": 8,
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
                                              "line": 8,
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
                        """);
    }

    public void testComplexCaretBeforePromiseCatchStatement() {
        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise({foo: '', bar: 10})
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => {<start>
                                    <caret>console.error(error);
                                <end>});
                        }
                        """,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                // language=json
                """
                        [
                          {
                            "range": {
                              "start": {
                                "line": 6,
                                "character": 12
                              },
                              "end": {
                                "line": 6,
                                "character": 19
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 6,
                                  "character": 12
                                },
                                "end": {
                                  "line": 6,
                                  "character": 25
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 6,
                                    "character": 12
                                  },
                                  "end": {
                                    "line": 6,
                                    "character": 32
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 6,
                                      "character": 12
                                    },
                                    "end": {
                                      "line": 6,
                                      "character": 33
                                    }
                                  },
                                  "parent": {
                                    "range": {
                                      "start": {
                                        "line": 5,
                                        "character": 27
                                      },
                                      "end": {
                                        "line": 7,
                                        "character": 8
                                      }
                                    },
                                    "parent": {
                                      "range": {
                                        "start": {
                                          "line": 5,
                                          "character": 26
                                        },
                                        "end": {
                                          "line": 7,
                                          "character": 9
                                        }
                                      },
                                      "parent": {
                                        "range": {
                                          "start": {
                                            "line": 5,
                                            "character": 15
                                          },
                                          "end": {
                                            "line": 7,
                                            "character": 9
                                          }
                                        },
                                        "parent": {
                                          "range": {
                                            "start": {
                                              "line": 1,
                                              "character": 4
                                            },
                                            "end": {
                                              "line": 7,
                                              "character": 10
                                            }
                                          },
                                          "parent": {
                                            "range": {
                                              "start": {
                                                "line": 1,
                                                "character": 4
                                              },
                                              "end": {
                                                "line": 7,
                                                "character": 11
                                              }
                                            },
                                            "parent": {
                                              "range": {
                                                "start": {
                                                  "line": 0,
                                                  "character": 16
                                                },
                                                "end": {
                                                  "line": 8,
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
                                                    "line": 8,
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
                                                      "line": 8,
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
                            }
                          }
                        ]
                        """);
    }
}

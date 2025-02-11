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

package com.redhat.devtools.lsp4ij.features.selectionRange;

import com.redhat.devtools.lsp4ij.fixtures.LSPSelectionersFixtureTestCase;

/**
 * TypeScript test for {@link LSPCodeBlockStatementGroupSelectioner} and {@link LSPCodeBlockBodySelectioner}.
 */
public class TypeScriptSelectionersTest extends LSPSelectionersFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.ts";

    public TypeScriptSelectionersTest() {
        super("*.ts");
    }

    // SIMPLE TESTS

    // NOTE: This is sufficient to test both the statement group and code block body selectioners
    // language=typescript
    private static final String SIMPLE_TEST_FILE_BODY = """
            export class Foo {
                bar() {
                    console.log('foo'); // line 2
                    console.log('bar'); // line 3
                    console.log('baz'); // line 4
            
                    console.log('foo'); // line 6
                    console.log('bar'); // line 7
                    console.log('baz'); // line 8
                }
            }
            """;

    // NOTE: This is slightly truncated to include selection ranges starting with the code block itself
    // language=json
    private static final String SIMPLE_MOCK_SELECTION_RANGES_JSON = """
            [
              {
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
                        }
                      }
                    }
                  }
                }
              }
            ]
            """;

    // language=json
    private static final String SIMPLE_MOCK_FOLDING_RANGES_JSON = """
            [
              {
                "startLine": 0,
                "endLine": 9
              },
              {
                "startLine": 1,
                "endLine": 8
              }
            ]
            """;

    public void testSelectionersSimple() {
        // Test from the start of any of the first three lines and the results should be the first statement group
        // followed by that group plus the intermediate empty line followed by the code block body
        for (int startLineNumber = 2; startLineNumber <= 4; startLineNumber++) {
            testSelectioner(
                    TEST_FILE_NAME,
                    SIMPLE_TEST_FILE_BODY,
                    SIMPLE_MOCK_SELECTION_RANGES_JSON,
                    SIMPLE_MOCK_FOLDING_RANGES_JSON,
                    startLineNumber,
                    new int[][]{
                            new int[]{2, 4},
                            new int[]{2, 5},
                            new int[]{2, 8},
                    }
            );
        }

        // Test from the start of any of the last three lines and the results should be the second statement group
        // followed by that group plus the intermediate empty line followed by the code block body
        for (int startLineNumber = 6; startLineNumber <= 8; startLineNumber++) {
            testSelectioner(
                    TEST_FILE_NAME,
                    SIMPLE_TEST_FILE_BODY,
                    SIMPLE_MOCK_SELECTION_RANGES_JSON,
                    SIMPLE_MOCK_FOLDING_RANGES_JSON,
                    startLineNumber,
                    new int[][]{
                            new int[]{6, 8},
                            new int[]{5, 8},
                            new int[]{2, 8},
                    }
            );
        }
    }

    // COMPLEX TESTS

    // NOTE: This is sufficient to test both the statement group and code block body selectioners
    // language=typescript
    @SuppressWarnings("TypeScriptUnresolvedReference")
    private static final String COMPLEX_TEST_FILE_BODY = """
            export class Foo {
                bar(args) {
                    console.log('Separate statement before');       // line 2
            
                    console.log('Contiguous statement before');     // line 4
                    let condition: boolean = true;                  // line 5
                    if (condition) {                                // line 6
                        console.log('Separate statement before');   // line 7
            
                        console.log('Contiguous statement before'); // line 9
                        invokePromise(args)                         // line 10
                            .then(() => {                           // line 11
                                console.log('demo');                // line 12
                            })                                      // line 13
                            .catch((error) => {                     // line 14
                                console.error(error);               // line 15
                            });                                     // line 16
                        console.log('Contiguous statement after');  // line 17
            
                        console.log('Separate statement after');    // line 19
                    }                                               // line 20
                    console.log('Contiguous statement after');      // line 21
            
                    console.log('Separate statement after');        // line 23
                }
            }
            """;

    // NOTE: This includes selection ranges taken from all of the lines required for these tests and is slightly
    // truncated to include selection ranges starting with the code block itself
    // language=json
    private static final String COMPLEX_MOCK_SELECTION_RANGES_JSON = """
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
                        "character": 48
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
                          "character": 49
                        }
                      },
                      "parent": {
                        "range": {
                          "start": {
                            "line": 1,
                            "character": 15
                          },
                          "end": {
                            "line": 24,
                            "character": 4
                          }
                        }
                      }
                    }
                  }
                }
              },
              {
                "range": {
                  "start": {
                    "line": 4,
                    "character": 8
                  },
                  "end": {
                    "line": 4,
                    "character": 15
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 4,
                      "character": 8
                    },
                    "end": {
                      "line": 4,
                      "character": 19
                    }
                  },
                  "parent": {
                    "range": {
                      "start": {
                        "line": 4,
                        "character": 8
                      },
                      "end": {
                        "line": 4,
                        "character": 50
                      }
                    },
                    "parent": {
                      "range": {
                        "start": {
                          "line": 4,
                          "character": 8
                        },
                        "end": {
                          "line": 4,
                          "character": 51
                        }
                      },
                      "parent": {
                        "range": {
                          "start": {
                            "line": 1,
                            "character": 15
                          },
                          "end": {
                            "line": 24,
                            "character": 4
                          }
                        }
                      }
                    }
                  }
                }
              },
              {
                "range": {
                  "start": {
                    "line": 5,
                    "character": 8
                  },
                  "end": {
                    "line": 5,
                    "character": 11
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 5,
                      "character": 8
                    },
                    "end": {
                      "line": 5,
                      "character": 38
                    }
                  }
                }
              },
              {
                "range": {
                  "start": {
                    "line": 6,
                    "character": 8
                  },
                  "end": {
                    "line": 6,
                    "character": 10
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 6,
                      "character": 8
                    },
                    "end": {
                      "line": 20,
                      "character": 9
                    }
                  },
                  "parent": {
                    "range": {
                      "start": {
                        "line": 1,
                        "character": 15
                      },
                      "end": {
                        "line": 24,
                        "character": 4
                      }
                    }
                  }
                }
              },
              {
                "range": {
                  "start": {
                    "line": 7,
                    "character": 12
                  },
                  "end": {
                    "line": 7,
                    "character": 19
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 7,
                      "character": 12
                    },
                    "end": {
                      "line": 7,
                      "character": 23
                    }
                  },
                  "parent": {
                    "range": {
                      "start": {
                        "line": 7,
                        "character": 12
                      },
                      "end": {
                        "line": 7,
                        "character": 52
                      }
                    },
                    "parent": {
                      "range": {
                        "start": {
                          "line": 7,
                          "character": 12
                        },
                        "end": {
                          "line": 7,
                          "character": 53
                        }
                      },
                      "parent": {
                        "range": {
                          "start": {
                            "line": 6,
                            "character": 24
                          },
                          "end": {
                            "line": 20,
                            "character": 8
                          }
                        },
                        "parent": {
                          "range": {
                            "start": {
                              "line": 6,
                              "character": 8
                            },
                            "end": {
                              "line": 20,
                              "character": 9
                            }
                          },
                          "parent": {
                            "range": {
                              "start": {
                                "line": 1,
                                "character": 15
                              },
                              "end": {
                                "line": 24,
                                "character": 4
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              },
              {
                "range": {
                  "start": {
                    "line": 9,
                    "character": 12
                  },
                  "end": {
                    "line": 9,
                    "character": 19
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 9,
                      "character": 12
                    },
                    "end": {
                      "line": 9,
                      "character": 23
                    }
                  },
                  "parent": {
                    "range": {
                      "start": {
                        "line": 9,
                        "character": 12
                      },
                      "end": {
                        "line": 9,
                        "character": 54
                      }
                    },
                    "parent": {
                      "range": {
                        "start": {
                          "line": 9,
                          "character": 12
                        },
                        "end": {
                          "line": 9,
                          "character": 55
                        }
                      },
                      "parent": {
                        "range": {
                          "start": {
                            "line": 6,
                            "character": 24
                          },
                          "end": {
                            "line": 20,
                            "character": 8
                          }
                        },
                        "parent": {
                          "range": {
                            "start": {
                              "line": 6,
                              "character": 8
                            },
                            "end": {
                              "line": 20,
                              "character": 9
                            }
                          },
                          "parent": {
                            "range": {
                              "start": {
                                "line": 1,
                                "character": 15
                              },
                              "end": {
                                "line": 24,
                                "character": 4
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              },
              {
                "range": {
                  "start": {
                    "line": 10,
                    "character": 12
                  },
                  "end": {
                    "line": 10,
                    "character": 25
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 10,
                      "character": 12
                    },
                    "end": {
                      "line": 10,
                      "character": 31
                    }
                  },
                  "parent": {
                    "range": {
                      "start": {
                        "line": 10,
                        "character": 12
                      },
                      "end": {
                        "line": 11,
                        "character": 21
                      }
                    },
                    "parent": {
                      "range": {
                        "start": {
                          "line": 10,
                          "character": 12
                        },
                        "end": {
                          "line": 13,
                          "character": 18
                        }
                      },
                      "parent": {
                        "range": {
                          "start": {
                            "line": 10,
                            "character": 12
                          },
                          "end": {
                            "line": 14,
                            "character": 22
                          }
                        },
                        "parent": {
                          "range": {
                            "start": {
                              "line": 10,
                              "character": 12
                            },
                            "end": {
                              "line": 16,
                              "character": 18
                            }
                          },
                          "parent": {
                            "range": {
                              "start": {
                                "line": 10,
                                "character": 12
                              },
                              "end": {
                                "line": 16,
                                "character": 19
                              }
                            },
                            "parent": {
                              "range": {
                                "start": {
                                  "line": 6,
                                  "character": 24
                                },
                                "end": {
                                  "line": 20,
                                  "character": 8
                                }
                              },
                              "parent": {
                                "range": {
                                  "start": {
                                    "line": 6,
                                    "character": 8
                                  },
                                  "end": {
                                    "line": 20,
                                    "character": 9
                                  }
                                },
                                "parent": {
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 15
                                    },
                                    "end": {
                                      "line": 24,
                                      "character": 4
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
              },
              {
                "range": {
                  "start": {
                    "line": 17,
                    "character": 12
                  },
                  "end": {
                    "line": 17,
                    "character": 19
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 17,
                      "character": 12
                    },
                    "end": {
                      "line": 17,
                      "character": 23
                    }
                  },
                  "parent": {
                    "range": {
                      "start": {
                        "line": 17,
                        "character": 12
                      },
                      "end": {
                        "line": 17,
                        "character": 53
                      }
                    },
                    "parent": {
                      "range": {
                        "start": {
                          "line": 17,
                          "character": 12
                        },
                        "end": {
                          "line": 17,
                          "character": 54
                        }
                      },
                      "parent": {
                        "range": {
                          "start": {
                            "line": 6,
                            "character": 24
                          },
                          "end": {
                            "line": 20,
                            "character": 8
                          }
                        },
                        "parent": {
                          "range": {
                            "start": {
                              "line": 6,
                              "character": 8
                            },
                            "end": {
                              "line": 20,
                              "character": 9
                            }
                          },
                          "parent": {
                            "range": {
                              "start": {
                                "line": 1,
                                "character": 15
                              },
                              "end": {
                                "line": 24,
                                "character": 4
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              },
              {
                "range": {
                  "start": {
                    "line": 19,
                    "character": 12
                  },
                  "end": {
                    "line": 19,
                    "character": 19
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 19,
                      "character": 12
                    },
                    "end": {
                      "line": 19,
                      "character": 23
                    }
                  },
                  "parent": {
                    "range": {
                      "start": {
                        "line": 19,
                        "character": 12
                      },
                      "end": {
                        "line": 19,
                        "character": 51
                      }
                    },
                    "parent": {
                      "range": {
                        "start": {
                          "line": 19,
                          "character": 12
                        },
                        "end": {
                          "line": 19,
                          "character": 52
                        }
                      },
                      "parent": {
                        "range": {
                          "start": {
                            "line": 6,
                            "character": 24
                          },
                          "end": {
                            "line": 20,
                            "character": 8
                          }
                        },
                        "parent": {
                          "range": {
                            "start": {
                              "line": 6,
                              "character": 8
                            },
                            "end": {
                              "line": 20,
                              "character": 9
                            }
                          },
                          "parent": {
                            "range": {
                              "start": {
                                "line": 1,
                                "character": 15
                              },
                              "end": {
                                "line": 24,
                                "character": 4
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              },
              {
                "range": {
                  "start": {
                    "line": 21,
                    "character": 8
                  },
                  "end": {
                    "line": 21,
                    "character": 15
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 21,
                      "character": 8
                    },
                    "end": {
                      "line": 21,
                      "character": 19
                    }
                  },
                  "parent": {
                    "range": {
                      "start": {
                        "line": 21,
                        "character": 8
                      },
                      "end": {
                        "line": 21,
                        "character": 49
                      }
                    },
                    "parent": {
                      "range": {
                        "start": {
                          "line": 21,
                          "character": 8
                        },
                        "end": {
                          "line": 21,
                          "character": 50
                        }
                      },
                      "parent": {
                        "range": {
                          "start": {
                            "line": 1,
                            "character": 15
                          },
                          "end": {
                            "line": 24,
                            "character": 4
                          }
                        }
                      }
                    }
                  }
                }
              },
              {
                "range": {
                  "start": {
                    "line": 23,
                    "character": 8
                  },
                  "end": {
                    "line": 23,
                    "character": 15
                  }
                },
                "parent": {
                  "range": {
                    "start": {
                      "line": 23,
                      "character": 8
                    },
                    "end": {
                      "line": 23,
                      "character": 19
                    }
                  },
                  "parent": {
                    "range": {
                      "start": {
                        "line": 23,
                        "character": 8
                      },
                      "end": {
                        "line": 23,
                        "character": 47
                      }
                    },
                    "parent": {
                      "range": {
                        "start": {
                          "line": 23,
                          "character": 8
                        },
                        "end": {
                          "line": 23,
                          "character": 48
                        }
                      },
                      "parent": {
                        "range": {
                          "start": {
                            "line": 1,
                            "character": 15
                          },
                          "end": {
                            "line": 24,
                            "character": 4
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
    private static final String COMPLEX_MOCK_FOLDING_RANGES_JSON = """
            [
              {
                "startLine": 0,
                "endLine": 24
              },
              {
                "startLine": 1,
                "endLine": 23
              },
              {
                "startLine": 6,
                "endLine": 19
              },
              {
                "startLine": 11,
                "endLine": 13
              },
              {
                "startLine": 11,
                "endLine": 12
              },
              {
                "startLine": 14,
                "endLine": 16
              },
              {
                "startLine": 14,
                "endLine": 15
              }
            ]
            """;

    public void testSelectionersComplex() {

        // Test from the start of the first line in bar() and it should select that line plus the blank line after it
        // then all lines in the body of bar()
        testSelectioner(
                TEST_FILE_NAME,
                COMPLEX_TEST_FILE_BODY,
                COMPLEX_MOCK_SELECTION_RANGES_JSON,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                2,
                new int[][]{
                        new int[]{2, 2}, // No newline
                        new int[]{2, 2}, // Newline
                        new int[]{2, 3},
                        new int[]{2, 23},
                }
        );

        // Test from the start of any of the lines in the second larger statement group and it should select the entire
        // statement group then all lines in the body of bar()
        for (int[] startAndInitialEndLineNumbers : new int[][]{
                new int[]{4, 4},
                new int[]{5, 5},
                new int[]{6, 20},
                new int[]{21, 21}
        }) {
            int startLineNumber = startAndInitialEndLineNumbers[0];
            int initialEndLineNumber = startAndInitialEndLineNumbers[1];
            testSelectioner(
                    TEST_FILE_NAME,
                    COMPLEX_TEST_FILE_BODY,
                    COMPLEX_MOCK_SELECTION_RANGES_JSON,
                    COMPLEX_MOCK_FOLDING_RANGES_JSON,
                    startLineNumber,
                    new int[][]{
                            new int[]{startLineNumber, initialEndLineNumber},
                            new int[]{4, 21},
                            new int[]{4, 22},
                            new int[]{2, 23},
                    }
            );
        }

        // Test from the start of the last line in bar() and it should select that line plus the blank line before it
        // then all lines in the body of bar()
        testSelectioner(
                TEST_FILE_NAME,
                COMPLEX_TEST_FILE_BODY,
                COMPLEX_MOCK_SELECTION_RANGES_JSON,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                23,
                new int[][]{
                        new int[]{23, 23}, // No newline
                        new int[]{23, 23}, // Newline
                        new int[]{22, 23},
                        new int[]{2, 23},
                }
        );

        // Test from the start of the first line in the conditional block and it should select that line plus the blank
        // line after it then all lines in the conditional block
        testSelectioner(
                TEST_FILE_NAME,
                COMPLEX_TEST_FILE_BODY,
                COMPLEX_MOCK_SELECTION_RANGES_JSON,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                7,
                new int[][]{
                        new int[]{7, 7}, // No newline
                        new int[]{7, 7}, // Newline
                        new int[]{7, 8},
                        new int[]{7, 19}
                }
        );

        // Test from the start of any of the (simple) lines in the second larger statement group in the conditional
        // block and it should select the entire statement group then all lines in the conditional block of bar()
        for (int[] startAndInitialEndLineNumbers : new int[][]{
                new int[]{9, 9},
                // Line 10 is tested below
                new int[]{17, 17}
        }) {
            int startLineNumber = startAndInitialEndLineNumbers[0];
            int initialEndLineNumber = startAndInitialEndLineNumbers[1];
            testSelectioner(
                    TEST_FILE_NAME,
                    COMPLEX_TEST_FILE_BODY,
                    COMPLEX_MOCK_SELECTION_RANGES_JSON,
                    COMPLEX_MOCK_FOLDING_RANGES_JSON,
                    startLineNumber,
                    new int[][]{
                            new int[]{startLineNumber, initialEndLineNumber},
                            new int[]{9, 17},
                            new int[]{9, 18},
                            new int[]{7, 19}
                    }
            );
        }

        // Test from the "invokePromise()" call in the conditional block and should select the call followed by the
        // call plus the ".then" portion followed by that plus the ".catch" portion followed by all lines in the
        // statement group followed by all lines in the conditional block
        testSelectioner(
                TEST_FILE_NAME,
                COMPLEX_TEST_FILE_BODY,
                COMPLEX_MOCK_SELECTION_RANGES_JSON,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                10,
                new int[][]{
                        new int[]{10, 10},
                        new int[]{10, 13},
                        new int[]{10, 16},
                        new int[]{9, 17},
                        new int[]{9, 18},
                        new int[]{7, 19}
                }
        );

        // Test from the start of the last line in the conditional block and it should select that line plus the blank
        // line before it then all lines in the conditional block
        testSelectioner(
                TEST_FILE_NAME,
                COMPLEX_TEST_FILE_BODY,
                COMPLEX_MOCK_SELECTION_RANGES_JSON,
                COMPLEX_MOCK_FOLDING_RANGES_JSON,
                19,
                new int[][]{
                        new int[]{19, 19}, // No newline
                        new int[]{19, 19}, // Newline
                        new int[]{18, 19},
                        new int[]{7, 19}
                }
        );
    }
}

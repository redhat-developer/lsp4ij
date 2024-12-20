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

    public void testSimpleCodeBlocks() {
        // language=json
        String mockFoldingRangesJson = """
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

        assertCodeBlock(
                "demo.ts",
                """
                        export class Demo {
                            demo() <start>{
                                <caret>console.log('demo');
                            }<end>
                        }
                        """,
                mockFoldingRangesJson,
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

        assertCodeBlock(
                "demo.ts",
                """
                        export class Demo {<start>
                            <caret>demo() {
                                console.log('demo');
                            }
                        <end>}
                        """,
                mockFoldingRangesJson,
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

        assertCodeBlock(
                "demo.ts",
                """
                        export class Demo {<caret><start>
                            demo() {
                                console.log('demo');
                            }
                        <end>}
                        """,
                mockFoldingRangesJson,
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

        assertCodeBlock(
                "demo.ts",
                """
                        export class Demo <caret>{<start>
                            demo() {
                                console.log('demo');
                            }
                        <end>}
                        """,
                mockFoldingRangesJson,
                // language=json
                "[]");

        assertCodeBlock(
                "demo.ts",
                """
                        export class Demo {<start>
                            demo() {
                                console.log('demo');
                            }
                        <end><caret>}
                        """,
                mockFoldingRangesJson,
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

        assertCodeBlock(
                "demo.ts",
                """
                        export class Demo {<start>
                            demo() {
                                console.log('demo');
                            }
                        <end>}<caret>
                        """,
                mockFoldingRangesJson,
                // language=json
                "[]");

        assertCodeBlock(
                "demo.ts",
                """
                        export class <caret><start><end>Demo {
                            demo() {
                                console.log('demo');
                            }
                        }
                        """,
                mockFoldingRangesJson,
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

    public void testComplexCodeBlocks() {
        // language=json
        String mockFoldingRangesJson = """
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

        // OUTER-MOST BLOCK

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
                mockFoldingRangesJson,
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
                mockFoldingRangesJson,
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
                mockFoldingRangesJson,
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
                mockFoldingRangesJson,
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
                mockFoldingRangesJson,
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

        // OBJECT LITERAL BLOCK

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
                mockFoldingRangesJson,
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
                mockFoldingRangesJson,
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
                mockFoldingRangesJson,
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
                mockFoldingRangesJson,
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

        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise(<start>{foo: '', <caret>bar: 10}<end>)
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => {
                                    console.error(error);
                                });
                        }
                        """,
                mockFoldingRangesJson,
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

        // PROMISE THEN BLOCK

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
                mockFoldingRangesJson,
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
                mockFoldingRangesJson,
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
                mockFoldingRangesJson,
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
                mockFoldingRangesJson,
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

        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise({foo: '', bar: 10})
                                .then(() => <start>{
                                    <caret>console.log('demo');
                                }<end>)
                                .catch((error) => {
                                    console.error(error);
                                });
                        }
                        """,
                mockFoldingRangesJson,
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

        // PROMISE CATCH BLOCK

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
                mockFoldingRangesJson,
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
                mockFoldingRangesJson,
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
                mockFoldingRangesJson,
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
                mockFoldingRangesJson,
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

        assertCodeBlock(
                "demo.ts",
                """
                        if (condition) {
                            invokePromise({foo: '', bar: 10})
                                .then(() => {
                                    console.log('demo');
                                })
                                .catch((error) => <start>{
                                    <caret>console.error(error);
                                }<end>);
                        }
                        """,
                mockFoldingRangesJson,
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

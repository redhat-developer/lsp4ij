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
package com.redhat.devtools.lsp4ij.features.completion;

import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;

/**
 * Completion tests by emulating LSP 'textDocument/completion' responses
 * from the go language server which uses additionalTextEdits.
 */
public class GoCompletionTest extends LSPCompletionFixtureTestCase {

    public GoCompletionTest() {
        super("*.go");
    }

    public void testCompletionWithAdditionalTextEdits() {
        // 1. Test completion items response
        assertCompletion("test.go",
                """
                        package src
                         
                        import "math/rand"
                         
                        func foo() {
                          for<caret>
                        }
                        """, """                
                        {
                             "isIncomplete": true,
                             "items": [
                               {
                                 "label": "format",
                                 "kind": 9,
                                 "detail": "\\"go/format\\"",
                                 "documentation": {
                                   "kind": "markdown",
                                   "value": ""
                                 },
                                 "preselect": true,
                                 "sortText": "00000",
                                 "filterText": "format",
                                 "insertTextFormat": 2,
                                 "textEdit": {
                                   "range": {
                                     "start": {
                                       "line": 5,
                                       "character": 2
                                     },
                                     "end": {
                                       "line": 5,
                                       "character": 5
                                     }
                                   },
                                   "newText": "format"
                                 },
                                 "additionalTextEdits": [
                                   {
                                     "range": {
                                       "start": {
                                         "line": 2,
                                         "character": 7
                                       },
                                       "end": {
                                         "line": 2,
                                         "character": 7
                                       }
                                     },
                                     "newText": "(\\n\\t\\"go/format\\"\\n\\t"
                                   },
                                   {
                                     "range": {
                                       "start": {
                                         "line": 2,
                                         "character": 18
                                       },
                                       "end": {
                                         "line": 2,
                                         "character": 18
                                       }
                                     },
                                     "newText": "\\n)"
                                   }
                                 ]
                               },
                               {
                                 "label": "format",
                                 "kind": 9,
                                 "detail": "\\"mvdan.cc/gofumpt/format\\"",
                                 "documentation": {
                                   "kind": "markdown",
                                   "value": ""
                                 },
                                 "sortText": "00001",
                                 "filterText": "format",
                                 "insertTextFormat": 2,
                                 "textEdit": {
                                   "range": {
                                     "start": {
                                       "line": 5,
                                       "character": 2
                                     },
                                     "end": {
                                       "line": 5,
                                       "character": 5
                                     }
                                   },
                                   "newText": "format"
                                 },
                                 "additionalTextEdits": [
                                   {
                                     "range": {
                                       "start": {
                                         "line": 2,
                                         "character": 7
                                       },
                                       "end": {
                                         "line": 2,
                                         "character": 7
                                       }
                                     },
                                     "newText": "(\\n\\t"
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
                                     "newText": "\\n\\t\\"mvdan.cc/gofumpt/format\\"\\n)\\n"
                                   }
                                 ]
                               }
                             ]
                           }
                          """
                , "format");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, """
                package src
                 
                import (
                	"go/format"
                	"math/rand"
                )
                
                func foo() {
                  format<caret>
                }
                """);
    }

    public void testCompletionWithAdditionalTextEdits2() {
        // 1. Test completion items response
        assertCompletion("test.go",
                """
                        package src
                         
                        import (
                        	"math/rand"
                        )
                         
                        func foo() {
                         var s = "";
                         s.<caret>
                        }
                        """, """                
                        {
                              "isIncomplete": true,
                              "items": [
                                {
                                  "label": "print!",
                                  "kind": 15,
                                  "detail": "print to stdout",
                                  "documentation": {
                                    "kind": "markdown",
                                    "value": ""
                                  },
                                  "preselect": true,
                                  "sortText": "00000",
                                  "insertTextFormat": 2,
                                  "textEdit": {
                                    "range": {
                                      "start": {
                                        "line": 8,
                                        "character": 3
                                      },
                                      "end": {
                                        "line": 8,
                                        "character": 3
                                      }
                                    },
                                    "newText": "fmt.Printf(\\"s: %v\\\\n\\", s)"
                                  },
                                  "additionalTextEdits": [
                                    {
                                      "range": {
                                        "start": {
                                          "line": 8,
                                          "character": 1
                                        },
                                        "end": {
                                          "line": 8,
                                          "character": 3
                                        }
                                      },
                                      "newText": ""
                                    },
                                    {
                                      "range": {
                                        "start": {
                                          "line": 2,
                                          "character": 8
                                        },
                                        "end": {
                                          "line": 2,
                                          "character": 8
                                        }
                                      },
                                      "newText": "\\n\\t\\"fmt\\""
                                    }
                                  ]
                                },
                                {
                                  "label": "split!",
                                  "kind": 15,
                                  "detail": "split string",
                                  "documentation": {
                                    "kind": "markdown",
                                    "value": ""
                                  },
                                  "sortText": "00001",
                                  "insertTextFormat": 2,
                                  "textEdit": {
                                    "range": {
                                      "start": {
                                        "line": 8,
                                        "character": 3
                                      },
                                      "end": {
                                        "line": 8,
                                        "character": 3
                                      }
                                    },
                                    "newText": "strings.Split(s, \\"$0\\")"
                                  },
                                  "additionalTextEdits": [
                                    {
                                      "range": {
                                        "start": {
                                          "line": 8,
                                          "character": 1
                                        },
                                        "end": {
                                          "line": 8,
                                          "character": 3
                                        }
                                      },
                                      "newText": ""
                                    },
                                    {
                                      "range": {
                                        "start": {
                                          "line": 3,
                                          "character": 11
                                        },
                                        "end": {
                                          "line": 3,
                                          "character": 11
                                        }
                                      },
                                      "newText": "\\"\\n\\t\\"strings"
                                    }
                                  ]
                                },
                                {
                                  "label": "var!",
                                  "kind": 15,
                                  "detail": "assign to variable",
                                  "documentation": {
                                    "kind": "markdown",
                                    "value": ""
                                  },
                                  "sortText": "00002",
                                  "insertTextFormat": 2,
                                  "textEdit": {
                                    "range": {
                                      "start": {
                                        "line": 8,
                                        "character": 3
                                      },
                                      "end": {
                                        "line": 8,
                                        "character": 3
                                      }
                                    },
                                    "newText": "${1:} :\\u003d s"
                                  },
                                  "additionalTextEdits": [
                                    {
                                      "range": {
                                        "start": {
                                          "line": 8,
                                          "character": 1
                                        },
                                        "end": {
                                          "line": 8,
                                          "character": 3
                                        }
                                      },
                                      "newText": ""
                                    }
                                  ]
                                }
                              ]
                            }
                          """
                , "print!", "split!", "var!");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, """
                package src
                                
                import (
                	"fmt"
                	"math/rand"
                )
                                
                func foo() {
                 var s = "";
                 fmt.Printf("s: %v\\n", s)<caret>
                }
                """);
    }

    public void testCompletionWithAdditionalTextEdits3() {
        // 1. Test completion items response
        assertCompletion("test.go",
                """
                        package src
                         
                        import (
                        	
                        )
                         
                        func foo() {
                         const s = "";
                         s.<caret>
                        }
                        """, """                
                        {
                               "isIncomplete": true,
                               "items": [
                                 {
                                   "label": "print!",
                                   "kind": 15,
                                   "detail": "print to stdout",
                                   "documentation": {
                                     "kind": "markdown",
                                     "value": ""
                                   },
                                   "preselect": true,
                                   "sortText": "00000",
                                   "insertTextFormat": 2,
                                   "textEdit": {
                                     "range": {
                                       "start": {
                                         "line": 8,
                                         "character": 3
                                       },
                                       "end": {
                                         "line": 8,
                                         "character": 3
                                       }
                                     },
                                     "newText": "fmt.Printf(\\"s: %v\\\\n\\", s)"
                                   },
                                   "additionalTextEdits": [
                                     {
                                       "range": {
                                         "start": {
                                           "line": 8,
                                           "character": 1
                                         },
                                         "end": {
                                           "line": 8,
                                           "character": 3
                                         }
                                       },
                                       "newText": ""
                                     },
                                     {
                                       "range": {
                                         "start": {
                                           "line": 2,
                                           "character": 7
                                         },
                                         "end": {
                                           "line": 4,
                                           "character": 1
                                         }
                                       },
                                       "newText": "\\"fmt\\""
                                     }
                                   ]
                                 },
                                 {
                                   "label": "var!",
                                   "kind": 15,
                                   "detail": "assign to variable",
                                   "documentation": {
                                     "kind": "markdown",
                                     "value": ""
                                   },
                                   "sortText": "00001",
                                   "insertTextFormat": 2,
                                   "textEdit": {
                                     "range": {
                                       "start": {
                                         "line": 8,
                                         "character": 3
                                       },
                                       "end": {
                                         "line": 8,
                                         "character": 3
                                       }
                                     },
                                     "newText": "${1:} :\\u003d s"
                                   },
                                   "additionalTextEdits": [
                                     {
                                       "range": {
                                         "start": {
                                           "line": 8,
                                           "character": 1
                                         },
                                         "end": {
                                           "line": 8,
                                           "character": 3
                                         }
                                       },
                                       "newText": ""
                                     }
                                   ]
                                 }
                               ]
                             }
                          """
                , "print!", "var!");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, """
                package src
                                
                import "fmt"
                                
                func foo() {
                 const s = "";
                 fmt.Printf("s: %v\\n", s)<caret>
                }
                """);
    }

}

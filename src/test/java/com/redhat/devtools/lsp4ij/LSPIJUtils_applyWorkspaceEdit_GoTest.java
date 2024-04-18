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
package com.redhat.devtools.lsp4ij;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.WorkspaceEdit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.redhat.devtools.lsp4ij.LSP4IJAssert.assertApplyWorkspaceEdit;

/**
 * Tests for {@link LSPIJUtils#applyWorkspaceEdit(WorkspaceEdit)} with Go LS.
 */
public class LSPIJUtils_applyWorkspaceEdit_GoTest extends BasePlatformTestCase {

    public void testConvertToRawStringLiteral() throws IOException {
        Path filePath = Files.createTempFile(null, ".go");
        String content = """
                package src
                               
                import "fmt"
                               
                func main() {
                	fmt.Println("Hello, world")
                }
                """;
        // Rename "Hello, world" --> `Hello, world`
        String json = """
                {
                   "changes": {},
                   "documentChanges": [
                     {
                       "textDocument": {
                         "version": 8,
                         "uri": "%s"
                       },
                       "edits": [
                         {
                           "range": {
                             "start": {
                               "line": 5,
                               "character": 13
                             },
                             "end": {
                               "line": 5,
                               "character": 27
                             }
                           },
                           "newText": "`Hello, world`"
                         }
                       ]
                     }
                   ]
                 }
                """.formatted(filePath.toUri().toASCIIString());
        String expected = """
                package src
                               
                import "fmt"
                               
                func main() {
                	fmt.Println(`Hello, world`)
                }
                """;
        assertApplyWorkspaceEdit(filePath, content, json, expected,getProject());
    }

    public void testInlineCallToPrintln() throws IOException {
        Path filePath = Files.createTempFile(null, ".go");
        String content = """
                package src
                
                import "fmt"
                
                func main() {
                	fmt.Println("Hello, world")
                }
                """;
        // Rename "Hello, world" --> `Hello, world`
        String json = """
                {
                    "changes": {},
                    "documentChanges": [
                      {
                        "textDocument": {
                          "version": 30,
                          "uri": "%s"
                        },
                        "edits": [
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
                            "newText": "\\t\\"os\\""
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
                            "newText": ")\\n\\n"
                          },
                          {
                            "range": {
                              "start": {
                                "line": 5,
                                "character": 5
                              },
                              "end": {
                                "line": 5,
                                "character": 6
                              }
                            },
                            "newText": "Fp"
                          },
                          {
                            "range": {
                              "start": {
                                "line": 5,
                                "character": 13
                              },
                              "end": {
                                "line": 5,
                                "character": 13
                              }
                            },
                            "newText": "os.Stdout, []any{"
                          },
                          {
                            "range": {
                              "start": {
                                "line": 5,
                                "character": 27
                              },
                              "end": {
                                "line": 5,
                                "character": 27
                              }
                            },
                            "newText": "}..."
                          },
                          {
                            "range": {
                              "start": {
                                "line": 6,
                                "character": 1
                              },
                              "end": {
                                "line": 6,
                                "character": 1
                              }
                            },
                            "newText": "\\n"
                          }
                        ]
                      }
                    ]
                  }
                """.formatted(filePath.toUri().toASCIIString());
        String expected = """
                package src
                
                import (
                	"fmt"
                	"os"
                )
                
                func main() {
                	fmt.Fprintln(os.Stdout, []any{"Hello, world"}...)
                }
                
                """;
        assertApplyWorkspaceEdit(filePath, content, json, expected,getProject());
    }
}

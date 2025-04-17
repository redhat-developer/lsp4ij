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
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.redhat.devtools.lsp4ij.fixtures.LSPDiagnosticsFixtureTestCase;

/**
 * Diagnostics tests by emulating LSP 'textDocument/publishDiagnostic' notifications
 * and 'testDocument/diagnostic' both from the rust-analyzer.
 */
public class RustDiagnosticsTest extends LSPDiagnosticsFixtureTestCase {

    public RustDiagnosticsTest() {
        super("*.rs");
    }

    public void testPublishAndPullDiagnosticsBoth() {
        // On load --> This test publish 3 diagnostics and pull 2 diagnostics
        // It shows 4 errors instead of 5 errors because diagnostic with Hint are ignored
        assertDiagnostics("main.rs",
                """
                        fn main() {
                            println!(None);
                            let
                        }
                        """,
                // language=json
                """
                        [
                           {
                             "range": {
                               "start": {
                                 "line": 3,
                                 "character": 0
                               },
                               "end": {
                                 "line": 3,
                                 "character": 1
                               }
                             },
                             "severity": 1,
                             "source": "rustc",
                             "message": "expected pattern, found `}`\\nexpected pattern",
                             "data": {
                               "rendered": "error: expected pattern, found `}`\\n --\\u003e src/main.rs:4:1\\n  |\\n4 | }\\n  | ^ expected pattern\\n\\n"
                             }
                           },
                           {
                             "range": {
                               "start": {
                                 "line": 1,
                                 "character": 13
                               },
                               "end": {
                                 "line": 1,
                                 "character": 17
                               }
                             },
                             "severity": 1,
                             "source": "rustc",
                             "message": "format argument must be a string literal",
                             "relatedInformation": [
                               {
                                 "location": {
                                   "uri": "file:///sample-rust-project/src/main.rs",
                                   "range": {
                                     "start": {
                                       "line": 1,
                                       "character": 13
                                     },
                                     "end": {
                                       "line": 1,
                                       "character": 13
                                     }
                                   }
                                 },
                                 "message": "you might be missing a string literal to format with: `\\"{}\\", `"
                               }
                             ],
                             "data": {
                               "rendered": "error: format argument must be a string literal\\n --\\u003e src/main.rs:2:14\\n  |\\n2 |     println!(None);\\n  |              ^^^^\\n  |\\nhelp: you might be missing a string literal to format with\\n  |\\n2 |     println!(\\"{}\\", None);\\n  |              +++++\\n\\n"
                             }
                           },
                           {
                             "range": {
                               "start": {
                                 "line": 1,
                                 "character": 13
                               },
                               "end": {
                                 "line": 1,
                                 "character": 13
                               }
                             },
                             "severity": 4,
                             "source": "rustc",
                             "message": "you might be missing a string literal to format with: `\\"{}\\", `",
                             "relatedInformation": [
                               {
                                 "location": {
                                   "uri": "file:///sample-rust-project/src/main.rs",
                                   "range": {
                                     "start": {
                                       "line": 1,
                                       "character": 13
                                     },
                                     "end": {
                                       "line": 1,
                                       "character": 17
                                     }
                                   }
                                 },
                                 "message": "original diagnostic"
                               }
                             ]
                           }
                         ]
                        """,
                // language=JSON
                """
                        {
                          "kind": "full",
                          "resultId": "rust-analyzer",
                          "items": [
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
                              "severity": 1,
                              "code": "syntax-error",
                              "codeDescription": {
                                "href": "https://doc.rust-lang.org/stable/reference/"
                              },
                              "source": "rust-analyzer",
                              "message": "Syntax Error: expected pattern"
                            },
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
                              "severity": 1,
                              "code": "syntax-error",
                              "codeDescription": {
                                "href": "https://doc.rust-lang.org/stable/reference/"
                              },
                              "source": "rust-analyzer",
                              "message": "Syntax Error: expected SEMICOLON"
                            }
                          ]
                        }
                        """,
                """
                        fn main() {
                            println!(<error descr="format argument must be a string literal">None</error>);
                            <error descr="Syntax Error: expected SEMICOLON"><error descr="Syntax Error: expected pattern">let</error></error>
                        <error descr="expected pattern, found `}`
                        expected pattern">}</error>
                        """);

        // Publish diagnostics with empty diagnostics, it should keep 2 errors coming from the pull diagnostics
        publishAndAssertDiagnostics(
                // language=json
                """
                        []
                        """,
                """
                        fn main() {
                            println!(None);
                            <error descr="Syntax Error: expected SEMICOLON"><error descr="Syntax Error: expected pattern">let</error></error>
                        }
                        """);

    }
}

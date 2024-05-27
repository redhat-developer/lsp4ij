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
        // 1. Test completion items result
        assertCompletion("test.go",
                """
                        package src
                         
                        import "math/rand"
                         
                        func foo() {
                          for
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

}

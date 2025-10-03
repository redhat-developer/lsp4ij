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
package com.redhat.devtools.lsp4ij.features.completion;

import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;

/**
 * Completion tests with 'textDocument/completion' responses
 * which returns snippets with LSP standard variables}.
 *
 * @see {@link com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetVariableConstants}
 */
public class VariableCompletionTest extends LSPCompletionFixtureTestCase {

    public VariableCompletionTest() {
        super("*.ts");
    }

    public void testCompletionWith$TM_FILENAME() {
        // 1. Test completion items response
        assertCompletion("MyComponent.ts",
                "<caret>", // language=json
                """                
                
                        {
                        "isIncomplete": false,
                        "items": [
                          {
                            "label": "Type",
                            "kind": 15,
                            "insertTextFormat": 2,
                            "insertTextMode": 2,
                            "textEdit": {
                              "range": {
                                "start": {
                                  "line": 1,
                                  "character": 1
                                },
                                "end": {
                                  "line": 1,
                                  "character": 1
                                }
                              },
                              "newText": "Type ${1:$TM_FILENAME} {\\n    |\\n}"
                            }
                          }
                        ]
                      }"""
                ,
                "Type");
        // 2. Test new editor content after applying the first completion item

        // Apply completion with Type
        assertApplyCompletionItem(0,
                """
                        Type MyComponent.ts {
                            |
                        }""");
    }

    public void testCompletionWith$TM_FILENAME_BASE() {
        // 1. Test completion items response
        assertCompletion("MyComponent.ts",
                        "<caret>", // language=json
                        """                
                        
                                {
                                "isIncomplete": false,
                                "items": [
                                  {
                                    "label": "Type",
                                    "kind": 15,
                                    "insertTextFormat": 2,
                                    "insertTextMode": 2,
                                    "textEdit": {
                                      "range": {
                                        "start": {
                                          "line": 1,
                                          "character": 1
                                        },
                                        "end": {
                                          "line": 1,
                                          "character": 1
                                        }
                                      },
                                      "newText": "Type ${1:$TM_FILENAME_BASE} {\\n    |\\n}"
                                    }
                                  }
                                ]
                              }"""
                ,
                "Type");
        // 2. Test new editor content after applying the first completion item

        // Apply completion with Type
        assertApplyCompletionItem(0,
                """
                        Type MyComponent {
                            |
                        }""");
    }
}

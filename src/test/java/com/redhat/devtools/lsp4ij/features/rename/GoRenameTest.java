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
package com.redhat.devtools.lsp4ij.features.rename;

import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.fixtures.LSPRenameFixtureTestCase;

/**
 * Rename tests by emulating LSP 'textDocument/prepareRename' and 'textDocument/rename'
 * responses from the Go language server.
 */
public class GoRenameTest extends LSPRenameFixtureTestCase {

    public GoRenameTest() {
        super("*.go");
    }

    public void testRenameWithPrepareRenameResult() {
        // Go LS uses prepare rename response (range + placeholder)
        assertRename("test.go",
                """                
                        package src
                         
                        func m<caret>ain() {
                        }""",
                """ 
                        {
                          "range": {
                            "start": {
                              "line": 2,
                              "character": 5
                            },
                            "end": {
                              "line": 2,
                              "character": 9
                            }
                          },
                          "placeholder": "main"
                        }
                        """, // Prepare rename managed by Go LS
                "main", // expected placeholder returned by prepare rename
                """                
                        {
                              "changes": {},
                              "documentChanges": [
                                {
                                  "textDocument": {
                                    "version": 5,
                                    "uri": "%s"
                                  },
                                  "edits": [
                                    {
                                      "range": {
                                        "start": {
                                          "line": 2,
                                          "character": 5
                                        },
                                        "end": {
                                          "line": 2,
                                          "character": 9
                                        }
                                      },
                                      "newText": "foo"
                                    }
                                  ]
                                }
                              ]
                            }""",
                """                
                        package src
                         
                        func foo() {
                        }""");
    }

    public void testRenameWithPrepareRenameDefaultBehavior() {
        // Go LS uses prepare rename default behavior which delegates
        // prepare rename to the LSP client (word range at)
        assertRename("test.go",
                """                
                        package src
                         
                        func m<caret>ain() {
                        }""",
                """ 
                        {
                          "defaultBehavior": true
                        }
                        """, // Prepare rename managed by LSP4IJ (client side)
                "main", // expected placeholder returned by prepare rename
                """                
                        {
                              "changes": {},
                              "documentChanges": [
                                {
                                  "textDocument": {
                                    "version": 5,
                                    "uri": "%s"
                                  },
                                  "edits": [
                                    {
                                      "range": {
                                        "start": {
                                          "line": 2,
                                          "character": 5
                                        },
                                        "end": {
                                          "line": 2,
                                          "character": 9
                                        }
                                      },
                                      "newText": "foo"
                                    }
                                  ]
                                }
                              ]
                            }""",
                """                
                        package src
                         
                        func foo() {
                        }""");
    }

    public void testElementCannotBeRenamed() {
        // As Go LS supports prepare rename, we use it,
        // and it returns a null prepare rename when
        // user tries to rename keyword like 'func'
        assertRenameWithError("test.go",
                """                
                        package src
                         
                        f<caret>unc main() {
                        }""",
                PREPARE_RENAME_NO_RESULT,
                null,
                null,
                LanguageServerBundle.message("lsp.refactor.rename.cannot.be.renamed.error")); // "The element can't be renamed."
    }

    public void testRenameError() {
        // Emulate that Go LS doesn't support prepare rename and throws an exception
        // when element to rename cannot be renamed.
        assertRenameWithError("test.go",
                """                
                        package src
                         
                        f<caret>unc main() {
                        }""",
                null,
                "func",
                """ 
                        {
                           "code": -32602,
                           "message": "no identifier found"
                        }
                        """, // Go throws an error when user tries to rename 'func' keyword,
                "no identifier found");
    }
}

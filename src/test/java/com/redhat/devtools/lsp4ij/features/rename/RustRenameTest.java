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

import com.redhat.devtools.lsp4ij.fixtures.LSPRenameFixtureTestCase;

/**
 * Rename tests by emulating LSP 'textDocument/prepareRename' and 'textDocument/rename'
 * responses from the Rust language server.
 */
public class RustRenameTest extends LSPRenameFixtureTestCase {

    public RustRenameTest() {
        super("*.rs");
    }

    public void testRenameWithPrepareNameRange() {
        // Rust LS uses range as prepare rename response
        assertRename("test.rs",
                """                
                        fn m<caret>ain() {
                              
                        }""",
                """ 
                        {
                          "start": {
                            "line": 0,
                            "character": 3
                          },
                          "end": {
                            "line": 0,
                            "character": 7
                          }
                        }
                        """, // Prepare rename managed by Rust LS
                "main", // expected placeholder returned by prepare rename
                """                
                        {
                             "changes": {},
                             "documentChanges": [
                               {
                                 "textDocument": {
                                   "version": 1,
                                   "uri": "%s"
                                 },
                                 "edits": [
                                   {
                                     "range": {
                                       "start": {
                                         "line": 0,
                                         "character": 3
                                       },
                                       "end": {
                                         "line": 0,
                                         "character": 7
                                       }
                                     },
                                     "newText": "foo"
                                   }
                                 ]
                               }
                             ]
                           }""",
                """                
                        fn foo() {
                              
                        }""");
    }

    public void testErrorWhilePrepareRenaming() {
        // Rust LS throws a ResponseError exception when prepare rename is not valid.
        assertRenameWithError("test.rs",
                """                
                        f|n main() {
                              
                        }""",
                """ 
                        {
                           "code": -32602,
                           "message": "No references found at position"
                        }
                        """, // Rust throws an error when user tries to rename 'fn' keyword
                null,
                null,
                "No references found at position");
    }

}

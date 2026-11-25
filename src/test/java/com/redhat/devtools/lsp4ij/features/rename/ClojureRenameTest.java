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

import com.intellij.openapi.util.Disposer;
import com.redhat.devtools.lsp4ij.fixtures.LSPRenameFixtureTestCase;
import com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Rename tests by emulating LSP 'textDocument/prepareRename' and 'textDocument/rename'
 * responses from the Clojure language server.
 */
public class ClojureRenameTest extends LSPRenameFixtureTestCase {

    public ClojureRenameTest() {
        super("*.clj");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // The 5 s of the file operations setting are for a user waiting for a rename: once they are up, the file is
        // renamed without the workspace/willRenameFiles edit. The renames wait for it as long as a slow CI runner takes,
        // and the test's end restores the setting.
        var settings = GlobalLanguageServerSettings.getInstance();
        int fileOperationsTimeout = settings.getFileOperationsTimeout();
        Disposer.register(getTestRootDisposable(), () -> settings.setFileOperationsTimeout(fileOperationsTimeout));
        settings.setFileOperationsTimeout((int) TimeUnit.SECONDS.toMillis(30));
    }

    public void testRenameNamespaceInEditor() {
        // Clojure LSP uses range as prepare rename response
        assertWillRename("AAAA.clj",
                """                
                        ns( A<caret>AAA)""",
                // language=json
                """ 
                        {
                          "start": {
                            "line": 0,
                            "character": 4
                          },
                          "end": {
                            "line": 0,
                            "character": 8
                          }
                        }
                        """, // Prepare rename managed by Clojure LSP
                "AAAA", // expected placeholder returned by prepare rename
                // language=json
                """    
                        {
                            "changes": {},
                              "documentChanges": [
                                {
                                  "oldUri": "%sAAAA.clj",
                                  "newUri": "%sBBBB.clj",
                                  "kind": "rename"
                                }
                              ]
                        }""",
                // language=json
                """
                        {
                          "changes": {},
                          "documentChanges": [
                            {
                              "textDocument": {
                                "version": 0,
                                "uri": "%sAAAA.clj"
                              },
                              "edits": [
                                {
                                  "range": {
                                    "start": {
                                      "line": 0,
                                      "character": 4
                                    },
                                    "end": {
                                      "line": 0,
                                      "character": 8
                                    }
                                  },
                                  "newText": "BBBB"
                                }
                              ]
                            }
                          ]
                        }
                        """,
                "BBBB.clj",
                """                
                        ns( BBBB)""");
    }

    public void testRenameFile() throws IOException {
        // Clojure LSP uses range as prepare rename response
        assertRenameFile("AAAA.clj",
                """                
                        ns( AAAA)""",
                "BBBB.clj",
                // language=json
                """
                        {
                          "changes": {},
                          "documentChanges": [
                            {
                              "textDocument": {
                                "version": 0,
                                "uri": "%sAAAA.clj"
                              },
                              "edits": [
                                {
                                  "range": {
                                    "start": {
                                      "line": 0,
                                      "character": 4
                                    },
                                    "end": {
                                      "line": 0,
                                      "character": 8
                                    }
                                  },
                                  "newText": "BBBB"
                                }
                              ]
                            }
                          ]
                        }
                        """,
                """                
                        ns( BBBB)""");
    }
}

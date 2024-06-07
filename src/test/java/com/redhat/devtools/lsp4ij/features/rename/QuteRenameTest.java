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
 * Rename tests by emulating LSP 'textDocument/rename'
 * responses from the Qute language server.
 * The Qute Language Server doesn't support prepare rename.
 */
public class QuteRenameTest extends LSPRenameFixtureTestCase {

    public QuteRenameTest() {
        super("*.html");
    }

    public void testRenameWithoutPrepareRename() {
        // Qute LS don't support prepare rename
        assertRename("test.html",
                """                
                        {#for i<caret>tem in items}
                            {item}
                        {/for}""",
                null, // Qute doesn't support prepare rename
                "item", // expected placeholder computed from the caret
                """                
                        {
                            "changes": {
                              "%s": [
                                {
                                  "range": {
                                    "start": {
                                      "line": 0,
                                      "character": 6
                                    },
                                    "end": {
                                      "line": 0,
                                      "character": 10
                                    }
                                  },
                                  "newText": "foo"
                                },
                                {
                                  "range": {
                                    "start": {
                                      "line": 1,
                                      "character": 5
                                    },
                                    "end": {
                                      "line": 1,
                                      "character": 9
                                    }
                                  },
                                  "newText": "foo"
                                }
                              ]
                            }
                          }""",
                """                
                        {#for foo in items}
                            {foo}
                        {/for}""");
    }

    public void testElementCannotBeRenamed() {
        // As Qute LS doesn't support prepare rename
        // LSP4IJ uses the client word range at strategy to get the prepare rename.
        assertRenameWithError("test.html",
                """                
                        {<caret>#for item in items}
                            {item}
                        {/for}""",
                null,
                null,
                null,
                LanguageServerBundle.message("lsp.refactor.rename.cannot.be.renamed.error")); // "The element can't be renamed."
    }

}

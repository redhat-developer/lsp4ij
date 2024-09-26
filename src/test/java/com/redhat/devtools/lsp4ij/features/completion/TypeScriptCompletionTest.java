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
 * from the typescript-language-server.
 */
public class TypeScriptCompletionTest extends LSPCompletionFixtureTestCase {

    public TypeScriptCompletionTest() {
        super("*.ts");
    }

    public void testCompletionWithTextEdit() {
        // 1. Test completion items response
        assertCompletion("test.ts",
                "''.<caret>", """                
                        {
                           "isIncomplete": false,
                           "items": [
                             {
                               "label": "charAt",
                               "kind": 2,
                               "sortText": "11",
                               "filterText": ".charAt",
                               "textEdit": {
                                 "range": {
                                   "start": {
                                     "line": 0,
                                     "character": 2
                                   },
                                   "end": {
                                     "line": 0,
                                     "character": 3
                                   }
                                 },
                                 "newText": ".charAt"
                               }
                             }
                           ]
                         }"""
                , "charAt");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "''.charAt<caret>");
    }

}

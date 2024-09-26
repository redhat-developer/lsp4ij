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
 * from the Qute language server which provides complex snippets.
 */
public class QuteCompletionTest extends LSPCompletionFixtureTestCase {

    public QuteCompletionTest() {
        super("*.html");
    }

    public void testCompletion() {
        // 1. Test completion items response
        assertCompletion("test.html",
                """
                        <ul>
                            f<caret>
                        </ul>
                        """, """                
                        {
                               "isIncomplete": false,
                               "items": [
                                    {
                                     "label": "if",
                                     "kind": 15,
                                     "detail": "If section",
                                     "documentation": {
                                       "kind": "markdown",
                                       "value": "\\r\\n```\\r\\n{#if condition}\\n\\t\\n{/if}\\r\\n```\\r\\n\\r\\n\\r\\nSee [If section](https://quarkus.io/guides/qute-reference#if_section) for more information."
                                     },
                                     "filterText": "if",
                                     "insertTextFormat": 2,
                                     "insertTextMode": 2,
                                     "textEdit": {
                                       "range": {
                                         "start": {
                                           "line": 1,
                                           "character": 4
                                         },
                                         "end": {
                                           "line": 1,
                                           "character": 6
                                         }
                                       },
                                       "newText": "{#if ${1:condition}}\\n\\t$0\\n{/if}"
                                     }
                                   },
                                 {
                                   "label": "for",
                                   "kind": 15,
                                   "detail": "Loop section with alias",
                                   "documentation": {
                                     "kind": "markdown",
                                     "value": "\r\n```\r\n{#for item in items}\n\t{item.name}\n{/for}\r\n```\r\n\r\n\r\nSee [Loop section](https://quarkus.io/guides/qute-reference#loop_section) for more information."
                                   },
                                   "filterText": "for",
                                   "insertTextFormat": 2,
                                   "insertTextMode": 2,
                                   "textEdit": {
                                     "range": {
                                       "start": {
                                         "line": 1,
                                         "character": 4
                                       },
                                       "end": {
                                         "line": 1,
                                         "character": 6
                                       }
                                     },
                                     "newText": "{#for ${1:item} in ${2:items}}\n\t{${1:item}.${3:name}}$0\n{/for}"
                                   }
                                 }
                               ]
                             }"""
                ,
                "if",
                "for");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, """
                <ul>
                    {#for item in items}
                        {item.name}<caret>
                    {/for}
                </ul>
                """);
    }

}

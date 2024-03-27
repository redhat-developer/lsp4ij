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
 * from the clojure-lsp language server.
 */
public class ClojureCompletionTest extends LSPCompletionFixtureTestCase {

    public void testCompletionWithoutTextEditAndEmptyContent() {
        // clojure-lsp returns completion items without text edit.
        // 1. Test completion items result
        assertCompletion("test.ts",
                "<caret>", """                
                        [
                            {
                              "label": "let",
                              "kind": 15,
                              "detail": "Insert let",
                              "insertText": "(let [${1:binding} ${2:value}])",
                              "insertTextFormat": 2
                            },
                            {
                              "label": "letfn",
                              "kind": 15,
                              "detail": "Insert letfn",
                              "insertText": "(letfn [(${1:name} [${2:args}]\\n $0)])",
                              "insertTextFormat": 2
                            }
                          ]"""
                , "let", "letfn");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "(let [binding value])");
    }

    public void testCompletionWithoutTextEdit() {
        // clojure-lsp returns completion items without text edit.
        // 1. Test completion items result
        assertCompletion("test.ts",
                "let<caret>", """                
                        [
                            {
                              "label": "let",
                              "kind": 15,
                              "detail": "Insert let",
                              "insertText": "(let [${1:binding} ${2:value}])",
                              "insertTextFormat": 2
                            },
                            {
                              "label": "letfn",
                              "kind": 15,
                              "detail": "Insert letfn",
                              "insertText": "(letfn [(${1:name} [${2:args}]\\n $0)])",
                              "insertTextFormat": 2
                            }
                          ]"""
                , "let", "letfn");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "(let [binding value])");
    }

    public void testCompletionWithoutTextEditAndCaretInsideToken() {
        // clojure-lsp returns completion items without text edit.
        // 1. Test completion items result
        assertCompletion("test.ts",
                "le<caret>t", """                
                        [
                            {
                              "label": "let",
                              "kind": 15,
                              "detail": "Insert let",
                              "insertText": "(let [${1:binding} ${2:value}])",
                              "insertTextFormat": 2
                            },
                            {
                              "label": "letfn",
                              "kind": 15,
                              "detail": "Insert letfn",
                              "insertText": "(letfn [(${1:name} [${2:args}]\\n $0)])",
                              "insertTextFormat": 2
                            }
                          ]"""
                , "let", "letfn");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "(let [binding value])t");
    }

}

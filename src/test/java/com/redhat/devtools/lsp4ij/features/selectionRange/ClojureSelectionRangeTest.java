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
package com.redhat.devtools.lsp4ij.features.selectionRange;

import com.redhat.devtools.lsp4ij.fixtures.LSPSelectionRangeFixtureTestCase;

/**
 * Selection range tests by emulating LSP 'textDocument/selectionRange' responses from the clojure-lsp.
 */
public class ClojureSelectionRangeTest extends LSPSelectionRangeFixtureTestCase {

    public ClojureSelectionRangeTest() {
        super("*.clj");
    }

    public void testSelectionRanges_word() {
        assertSelectionRanges(
                "demo.clj",
                // language=clojure
                """
                (ns clojure-lsp.foo
                  (:require
                   [clojure.string :as string]))
                
                
                
                (string/split-lines "123")
                """,
                // Start on the qualifier
                "string/",
                // language=json
                """
                [
                   {
                     "range": {
                       "start": {
                         "line": 6,
                         "character": 1
                       },
                       "end": {
                         "line": 6,
                         "character": 19
                       }
                     },
                     "parent": {
                       "range": {
                         "start": {
                           "line": 6,
                           "character": 1
                         },
                         "end": {
                           "line": 6,
                           "character": 25
                         }
                       },
                       "parent": {
                         "range": {
                           "start": {
                             "line": 6,
                             "character": 0
                           },
                           "end": {
                             "line": 6,
                             "character": 26
                           }
                         }
                       }
                     }
                   }
                 ]
                """,
                "string/split-lines",
                "string/split-lines \"123\"",
                "(string/split-lines \"123\")"
        );
    }

}

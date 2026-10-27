/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.inlineCompletion

import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.redhat.devtools.lsp4ij.fixtures.LSPInlineCompletionTestCase
import com.redhat.devtools.lsp4ij.fixtures.testInlineCompletion
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Inline completion tests by emulating LSP 'textDocument/inlineCompletion' responses
 * from a Docker language server for docker-bake.hcl files.
 *
 * ## Inline Completion Response Formats
 *
 * The LSP inline completion response can be in two formats:
 *
 * ### 1. Direct array (used by Docker LS)
 * ```json
 * [
 *   {
 *     "insertText": "target \"stage2\" {...}",
 *     "range": {
 *       "start": { "line": 0, "character": 0 },
 *       "end": { "line": 0, "character": 1 }
 *     }
 *   }
 * ]
 * ```
 *
 * ### 2. InlineCompletionList with "items" field (used by Perl LS)
 * ```json
 * {
 *   "items": [
 *     {
 *       "insertText": "strict;",
 *       "filterText": "strict"
 *     }
 *   ]
 * }
 * ```
 */
@RunWith(JUnit4::class)
class DockerBakeInlineCompletionTest : LSPInlineCompletionTestCase("*.hcl") {

    @Ignore("Unstable when running all tests together")
    @Test
    fun `test inline completion with range`() {
        // language=json
        val json = """
            [
              {
                "insertText": "target \"stage2\" {\n  target = \"stage2\"\n}\n",
                "range": {
                  "start": { "line": 0, "character": 0 },
                  "end": { "line": 0, "character": 1 }
                }
              },
              {
                "insertText": "target \"stage\" {\n  target = \"stage\"\n}\n",
                "range": {
                  "start": { "line": 0, "character": 0 },
                  "end": { "line": 0, "character": 1 }
                }
              }
            ]
        """

        testInlineCompletion(myFixture, json) {
            try {
                registerServer()

                // Configure editor (empty file)
                myFixture.configureByText("docker-bake.hcl", "<caret>")

                // Simulate typing 't'
                typeChar('t')

                delay()

                // Verify first variant ghost text is shown
                // Expected: "arget \"stage2\" {...}" because 't' is already typed
                assertInlineRender("arget \"stage2\" {\n  target = \"stage2\"\n}\n")

                // Navigate to second variant
                nextVariant()
                assertInlineRender("arget \"stage\" {\n  target = \"stage\"\n}\n")

                // Accept the suggestion
                insert()

                // Verify final document text (range replaced 't' with full text)
                assertFileContent("target \"stage\" {\n  target = \"stage\"\n}\n<caret>")

                assertInlineHidden()
            } finally {
                unregisterServer()
            }
        }
    }

    @Ignore("Unstable when running all tests together")
    @Test
    fun `test inline completion without range`() {
        // language=json
        val json = """
            [
              {
                "insertText": "test text"
              }
            ]
        """

        testInlineCompletion(myFixture, json) {
            try {
                registerServer()

                myFixture.configureByText("docker-bake.hcl", "<caret>")

                // Simulate typing ' '
                typeChar(' ')

                delay()

                // Should show full text since no range specified
                assertInlineRender("test text")

                insert()

                // Verify final document text
                assertFileContent(" test text<caret>")

                assertInlineHidden()
            } finally {
                unregisterServer()
            }
        }
    }
}

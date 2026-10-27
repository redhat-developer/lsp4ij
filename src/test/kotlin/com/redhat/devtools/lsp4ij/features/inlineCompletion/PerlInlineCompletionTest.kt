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
 * from a Perl language server for .pl files.
 *
 * ## Inline Completion Response Formats
 *
 * The Perl language server returns responses in the **InlineCompletionList** format:
 *
 * ```json
 * {
 *   "items": [
 *     {
 *       "insertText": "strict;",
 *       "filterText": "strict"
 *     },
 *     {
 *       "insertText": "warnings;",
 *       "filterText": "warnings"
 *     },
 *     {
 *       "insertText": "feature ':5.36';",
 *       "filterText": "feature"
 *     }
 *   ]
 * }
 * ```
 *
 * Key differences from Docker format:
 * - Uses `{"items": [...]}` wrapper (InlineCompletionList)
 * - No `range` field (inserts at cursor position)
 * - Uses `filterText` for filtering suggestions
 */
@RunWith(JUnit4::class)
class PerlInlineCompletionTest : LSPInlineCompletionTestCase("*.pl") {

    @Ignore("Unstable when running all tests together")
    @Test
    fun `test perl inline completion with multiple items`() {
        // language=json
        val json = """
            {
              "items": [
                {
                  "insertText": "strict;",
                  "filterText": "strict"
                },
                {
                  "insertText": "warnings;",
                  "filterText": "warnings"
                },
                {
                  "insertText": "feature ':5.36';",
                  "filterText": "feature"
                }
              ]
            }
        """

        testInlineCompletion(myFixture, json) {
            try {
                registerServer()

                myFixture.configureByText("test.pl", "use <caret>")

                typeChar(' ')

                delay()

                // Should show first suggestion
                assertInlineRender("strict;")

                // Navigate to second variant
                nextVariant()
                assertInlineRender("warnings;")

                // Navigate to third variant
                nextVariant()
                assertInlineRender("feature ':5.36';")

                // Accept the suggestion
                insert()

                // Verify final document text
                assertFileContent("use  feature ':5.36';<caret>")
                assertInlineHidden()
            } finally {
                unregisterServer()
            }
        }
    }
}

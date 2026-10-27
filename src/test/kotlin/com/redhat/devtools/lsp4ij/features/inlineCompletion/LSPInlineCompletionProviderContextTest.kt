/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.inlineCompletion

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.lookup.LookupEvent
import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.InlineCompletionTriggerKind
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either

/** Native event and popup metadata must describe what would actually be inserted. */
class LSPInlineCompletionProviderContextTest : LSPCompletionFixtureTestCase("*.inline-test") {
    private val provider = LSPInlineCompletionProvider()

    fun testDirectInvocationIsInvoked() {
        myFixture.configureByText("test.inline-test", "con<caret>")
        val event = InlineCompletionEvent.DirectCall(myFixture.editor, myFixture.editor.caretModel.currentCaret)
        assertTrue("Explicit calls must replace an existing suggestion", provider.restartOn(event))
        val params = provider.createParams(event.toRequest()!!)
        assertEquals(InlineCompletionTriggerKind.Invoked, params.context.triggerKind)
        assertNull(params.context.selectedCompletionInfo)
        assertEquals(Position(0, 3), params.position)
    }

    fun testLookupChangeUsesInsertionTextAndExactEditRange() {
        val item = CompletionItem("consoleLabel").apply {
            filterText = "con"
            setTextEdit(Either.forLeft(TextEdit(Range(Position(0, 4), Position(0, 7)), "consoleValue")))
        }
        openLookup(item)
        val request = lookupRequest()
        assertTrue(provider.isEnabled(request.event))
        assertTrue("A new popup item must replace the previous selection's suggestion", provider.restartOn(request.event))
        val context = provider.createParams(request).context
        assertEquals(InlineCompletionTriggerKind.Automatic, context.triggerKind)
        assertEquals("consoleValue", context.selectedCompletionInfo.text)
        assertEquals(Range(Position(0, 4), Position(0, 7)), context.selectedCompletionInfo.range)
    }

    fun testLookupWithoutTextEditUsesLookupPrefixRange() {
        val item = CompletionItem("consoleLabel").apply {
            filterText = "con"
            insertText = "consoleValue"
        }
        openLookup(item)
        val context = provider.createParams(lookupRequest()).context
        assertEquals("consoleValue", context.selectedCompletionInfo.text)
        assertEquals(Range(Position(0, 4), Position(0, 7)), context.selectedCompletionInfo.range)
    }

    fun testExplicitInvocationRetainsVisiblePopupSelection() {
        openLookup(CompletionItem("console"))
        val event = InlineCompletionEvent.DirectCall(myFixture.editor, myFixture.editor.caretModel.currentCaret)
        val context = provider.createParams(event.toRequest()!!).context
        assertEquals(InlineCompletionTriggerKind.Invoked, context.triggerKind)
        assertEquals("console", context.selectedCompletionInfo.text)
    }

    fun testSnippetNotationIsNotReportedAsLiteralSelectedText() {
        openLookup(CompletionItem("console").apply {
            insertTextFormat = InsertTextFormat.Snippet
            insertText = "console(\${1:value})"
        })
        assertNull(provider.createParams(lookupRequest()).context.selectedCompletionInfo)
    }

    private fun openLookup(item: CompletionItem) {
        assertCompletion("test.inline-test", "obj.con<caret>",
            CompletionList(false, listOf(item, CompletionItem("constant"))), item.label, "constant")
        myFixture.lookup.currentItem = myFixture.lookupElements!!.first { it.lookupString == item.label }
    }

    private fun lookupRequest(): InlineCompletionRequest {
        val lookup = myFixture.lookup
        val event = InlineCompletionEvent.LookupChange(LookupEvent(lookup, lookup.currentItem, ' '))
        return event.toRequest()!!
    }
}

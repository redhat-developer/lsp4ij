/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.inlineCompletion

import com.intellij.codeInsight.inline.completion.DebouncedInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.redhat.devtools.lsp4ij.LSPFileSupport
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionProposal
import com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.InlineCompletionContext
import org.eclipse.lsp4j.InlineCompletionTriggerKind
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SelectedCompletionInfo
import org.eclipse.lsp4j.TextDocumentIdentifier

/**
 * LSP Inline Completion Provider that integrates with IntelliJ's inline completion API
 * to provide AI-assisted code completion via Language Server Protocol.
 *
 * Uses DebouncedInlineCompletionProvider for automatic debouncing of requests.
 * The provider waits for typing to pause before requesting completions from LSP,
 * avoiding excessive server calls during rapid typing.
 *
 * @since LSP 3.18.0
 */
class LSPInlineCompletionProvider : DebouncedInlineCompletionProvider() {

    companion object {
        private val logger = Logger.getInstance(LSPInlineCompletionProvider::class.java)
        private val DEBOUNCE_DELAY = 300.milliseconds
    }

    override val id: InlineCompletionProviderID = InlineCompletionProviderID("LSPInlineCompletionProvider")

    override suspend fun getDebounceDelay(request: InlineCompletionRequest): Duration {
        // No delay for explicit invocations (e.g., manual trigger)
        if (request.event is InlineCompletionEvent.DirectCall) {
            return Duration.ZERO
        }
        return DEBOUNCE_DELAY
    }

    override suspend fun getSuggestionDebounced(request: InlineCompletionRequest): InlineCompletionSuggestion {
        // Check if any language server supports inline completion for this file
        val file = request.file
        val project = file.project
        if (!LanguageServiceAccessor.getInstance(project).hasAny(
                file,
                // The inline completion feature must be enabled and supported for the file's language server
                { ls ->
                    ls.clientFeatures.inlineCompletionFeature.isEnabled(file) &&
                            ls.clientFeatures.inlineCompletionFeature.isSupported(file)
                }
            )
        ) {
            return InlineCompletionSuggestion.Empty
        }

        // Create LSP params (must be done in read action)
        val document = request.document
        val offset = request.endOffset
        // Lookup selection is UI state. Capture it together with the document position
        // on EDT before starting the asynchronous server request. createParams has no side
        // effects, so a cancelled read is simply repeated.
        val params = withContext(Dispatchers.EDT) {
            readAction { createParams(request) }
        }

        // Get inline completions from language servers via LSPFileSupport
        val support = LSPFileSupport.getSupport(file).inlineCompletionSupport
        val future = support.getInlineCompletions(params)

        try {
            ProgressIndicatorUtils.awaitWithCheckCanceled(future)

            if (!isDoneNormally(future)) {
                return InlineCompletionSuggestion.Empty
            }

            val completions = future.get()
            if (completions.isEmpty()) {
                return InlineCompletionSuggestion.Empty
            }

            // Convert LSP completions to InlineCompletionVariants
            val allItems = completions.flatMap { completion ->
                if (completion.inlineCompletion().isLeft) {
                    completion.inlineCompletion().left
                } else {
                    completion.inlineCompletion().right.items
                }
            }

            if (allItems.isEmpty()) {
                return InlineCompletionSuggestion.Empty
            }

            // Create variants from LSP items (supports multiple alternative suggestions)
            val variants = allItems.mapNotNull { item ->
                val insertText = when {
                    item.insertText.isLeft -> item.insertText.left
                    item.insertText.isRight -> item.insertText.right.value
                    else -> null
                }

                val selected = params.context.selectedCompletionInfo
                if (insertText.isNullOrBlank() ||
                    (selected != null && (item.range != selected.range || !insertText.startsWith(selected.text)))) {
                    null
                } else {
                    // Build elements list considering the range
                    val elements = buildList {
                        var textToDisplay = insertText

                        if (item.range != null) {
                            // Get the range that will be replaced
                            val range = item.range
                            val (startOffset, endOffset) = readAction {
                                val startOffset = LSPIJUtils.toOffset(range.start, document)
                                val endOffset = LSPIJUtils.toOffset(range.end, document)
                                Pair(startOffset, endOffset)
                            }

                            // If the range starts before current offset,
                            // the insertText likely contains text already typed
                            if (startOffset < offset && offset <= endOffset) {
                                val alreadyTypedLength = offset - startOffset
                                if (alreadyTypedLength < insertText.length) {
                                    // Skip the part of insertText that corresponds to already typed text
                                    textToDisplay = insertText.substring(alreadyTypedLength)
                                }
                            }

                            // If the range ends after current offset, skip that existing text (it will be replaced)
                            if (endOffset > offset) {
                                val skipText = readAction {
                                    document.getText(com.intellij.openapi.util.TextRange(offset, endOffset))
                                }
                                if (skipText.isNotEmpty()) {
                                    add(InlineCompletionSkipTextElement(skipText))
                                }
                            }
                        }

                        // Add the new text (adjusted if range starts before offset)
                        if (textToDisplay.isNotEmpty()) {
                            add(InlineCompletionGrayTextElement(textToDisplay))
                        }
                    }

                    if (elements.isEmpty()) {
                        null
                    } else {
                        InlineCompletionVariant.build(elements = elements.asFlow())
                    }
                }
            }
            if (variants.isEmpty()) {
                return InlineCompletionSuggestion.Empty
            }

            // Return a suggestion that can provide multiple variants
            return object : InlineCompletionSuggestion {
                override suspend fun getVariants(): List<InlineCompletionVariant> = variants
            }

        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: CancellationException) {
            throw e;
        } catch (e: ExecutionException) {
            logger.error("Error while consuming LSP 'textDocument/inlineCompletion' request", e)
            return InlineCompletionSuggestion.Empty
        }
    }

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
        // This runs on EDT — keep it fast, no I/O
        // Accept typing events and direct calls
        // The real check for language server support happens in getSuggestionDebounced
        return event is InlineCompletionEvent.DocumentChange ||
                event is InlineCompletionEvent.DirectCall ||
                event is InlineCompletionEvent.LookupChange
    }

    override fun restartOn(event: InlineCompletionEvent): Boolean {
        // The platform otherwise retains the current session at the same caret position.
        // A different lookup item or explicit invocation needs fresh server alternatives.
        return event is InlineCompletionEvent.DirectCall || event is InlineCompletionEvent.LookupChange
    }

    /** Capture the native invocation kind and the replacement represented by the popup selection. */
    internal fun createParams(request: InlineCompletionRequest): LSPInlineCompletionParams {
        val document = request.document
        val position = LSPIJUtils.toPosition(request.endOffset, document)
        val textDocument = TextDocumentIdentifier(LSPIJUtils.toUri(request.file).toString())
        val kind = if (request.event is InlineCompletionEvent.DirectCall) {
            InlineCompletionTriggerKind.Invoked
        } else {
            InlineCompletionTriggerKind.Automatic
        }
        val context = InlineCompletionContext(kind)
        val lookup = LookupManager.getActiveLookup(request.editor)
        val selected = request.lookupElement ?: lookup?.currentItem
        if (selected != null && lookup != null) {
            // Completion ranking wraps LSP proposals in lookup decorators. The platform
            // accessor unwraps them so insertion text/ranges and snippet format survive.
            val proposal = selected.`as`(LSPCompletionProposal::class.java)
            val item = proposal?.item
            // A snippet can resolve editor variables and live placeholders on insertion.
            // Do not describe its source notation as literal selected completion text.
            if (item?.insertTextFormat != InsertTextFormat.Snippet) {
                val edit = item?.textEdit
                val text = when {
                    edit?.isLeft == true -> edit.left.newText
                    edit?.isRight == true -> edit.right.newText
                    else -> item?.insertText ?: selected.lookupString
                }
                val range = proposal?.textEditRange ?: Range(
                    LSPIJUtils.toPosition(lookup.lookupStart, document), position
                )
                context.selectedCompletionInfo = SelectedCompletionInfo(range, text)
            }
        }
        return LSPInlineCompletionParams(textDocument, position, request.endOffset).apply {
            this.context = context
        }
    }
}

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

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiDocumentManager
import com.redhat.devtools.lsp4ij.LSPFileSupport
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import kotlinx.coroutines.future.await
import org.eclipse.lsp4j.TextDocumentIdentifier

/**
 * LSP Inline Completion Provider that integrates with IntelliJ's inline completion API
 * to provide AI-assisted code completion via Language Server Protocol.
 *
 * @since LSP 3.18.0
 */
class LSPInlineCompletionProvider : InlineCompletionProvider {

    override val id: InlineCompletionProviderID = InlineCompletionProviderID("LSPInlineCompletionProvider")

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
        val editor = request.editor
        val file = request.file
        val project = file.project
        val offset = request.endOffset

        // Check if LSP is available for this file
        val psiFile =
            PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
         ?: return InlineCompletionSuggestion.Empty

        // Create LSP inline completion params
        val position = LSPIJUtils.toPosition(offset, editor.document)
        val textDocument = TextDocumentIdentifier(LSPIJUtils.toUri(file).toString())

        val params = LSPInlineCompletionParams(textDocument, position, offset)

        // Get inline completions from language servers via LSPFileSupport
        val support = LSPFileSupport.getSupport(psiFile).inlineCompletionSupport

        try {
            val completions = support.getInlineCompletions(params).await()

            if (completions.isEmpty()) {
                return InlineCompletionSuggestion.Empty
            }

            // Get the first completion item
            val firstCompletion = completions.first()
            val items = if (firstCompletion.inlineCompletion().isLeft) {
                firstCompletion.inlineCompletion().left
            } else {
                firstCompletion.inlineCompletion().right.items
            }

            if (items.isEmpty()) {
                return InlineCompletionSuggestion.Empty
            }

            // Get the insert text from the first item
            val firstItem = items.first()
            val insertText = when {
                firstItem.insertText.isLeft -> firstItem.insertText.left
                firstItem.insertText.isRight -> firstItem.insertText.right.value
                else -> null
            }

            if (insertText.isNullOrBlank()) {
                return InlineCompletionSuggestion.Empty
            }

            // Return single inline completion element
            return InlineCompletionSingleSuggestion.build {
                emit(InlineCompletionGrayTextElement(insertText))
            }

        } catch (e: Exception) {
            // Log error and return empty suggestion
            return InlineCompletionSuggestion.Empty
        }
    }

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
        // Check if the file type supports LSP inline completion
        val request = event.toRequest() ?: return false
        val file = request.file
        val psiFile = PsiDocumentManager.getInstance(file.project).getPsiFile(request.editor.document)
            ?: return false

        // Check if any language server supports inline completion for this file
        return LanguageServiceAccessor.getInstance(file.project).hasAny(psiFile) {
            it.clientFeatures.inlineCompletionFeature.isSupported(psiFile)
        }
    }

    override fun restartOn(event: InlineCompletionEvent): Boolean {
        // Restart suggestion on typing events
        return event is InlineCompletionEvent.DocumentChange
    }
}

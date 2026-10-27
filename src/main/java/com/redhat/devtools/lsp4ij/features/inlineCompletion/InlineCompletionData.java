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
package com.redhat.devtools.lsp4ij.features.inlineCompletion;

import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.eclipse.lsp4j.InlineCompletionItem;
import org.eclipse.lsp4j.InlineCompletionList;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * LSP inline completion data.
 *
 * @param inlineCompletion the LSP inline completion response
 * @param languageServer   the language server which has created the inline completion response.
 */
record InlineCompletionData(@NotNull Either<List<InlineCompletionItem>, InlineCompletionList> inlineCompletion,
                            @NotNull LanguageServerItem languageServer) {
}

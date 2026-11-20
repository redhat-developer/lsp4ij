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
package com.redhat.devtools.lsp4ij.features.semanticTokens.inspector;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Semantic tokens inspector data.
 *
 * @param document       the document of the semantic tokens.
 * @param file           the file of the semantic tokens.
 * @param highlightInfos the semantic tokens, with their offsets in the text.
 * @param text           the text of the document when the semantic tokens were computed, which their offsets are
 *                       in: the inspector shows them later, when the document can have changed.
 */
public record SemanticTokensInspectorData(@NotNull Document document,
                                          @NotNull PsiFile file,
                                          @NotNull List<SemanticTokensHighlightInfo> highlightInfos,
                                          @NotNull String text) {

    /**
     * Creates the data of the given semantic tokens, whose offsets are in the current text of the document.
     *
     * @param document       the document of the semantic tokens.
     * @param file           the file of the semantic tokens.
     * @param highlightInfos the semantic tokens, with their offsets in the current text of the document.
     */
    public SemanticTokensInspectorData(@NotNull Document document,
                                       @NotNull PsiFile file,
                                       @NotNull List<SemanticTokensHighlightInfo> highlightInfos) {
        this(document, file, highlightInfos, document.getText());
    }
}

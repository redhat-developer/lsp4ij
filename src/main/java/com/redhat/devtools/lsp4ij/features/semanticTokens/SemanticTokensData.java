/*******************************************************************************
 * Copyright (c) 2024-2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * FalsePattern - Performance improvements for huge files
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.semanticTokens;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.semanticTokens.inspector.SemanticTokensHighlightInfo;
import com.redhat.devtools.lsp4ij.features.semanticTokens.inspector.SemanticTokensInspectorData;
import com.redhat.devtools.lsp4ij.features.semanticTokens.inspector.SemanticTokensInspectorManager;
import com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider.LSPSemanticTokensFileViewProvider;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * Semantic data.
 */
public class SemanticTokensData {

    private final SemanticTokens semanticTokens;
    private final SemanticTokensLegend semanticTokensLegend;
    private final SemanticTokensColorsProvider semanticTokensColorsProvider;

    public SemanticTokensData(@NotNull SemanticTokens semanticTokens,
                              @NotNull SemanticTokensLegend semanticTokensLegend,
                              @NotNull SemanticTokensColorsProvider semanticTokensColorsProvider) {
        this.semanticTokens = semanticTokens;
        this.semanticTokensLegend = semanticTokensLegend;
        this.semanticTokensColorsProvider = semanticTokensColorsProvider;
    }

    public SemanticTokens getSemanticTokens() {
        return semanticTokens;
    }

    /**
     * Highlight the given file / document with the current semanticTokens.
     *
     * @param file     the file.
     * @param document the document.
     * @param addInfo  callback to collect {@link HighlightInfo} created from the semantic tokens data.
     */
    public void highlight(@NotNull PsiFile file,
                          @NotNull Document document,
                          @NotNull LazyHighlightInfo.Consumer addInfo) {
        // Try to populate the file's view provider with these tokens if possible
        LSPSemanticTokensFileViewProvider semanticTokensFileViewProvider = LSPSemanticTokensFileViewProvider.getInstance(file);

        var inspector = SemanticTokensInspectorManager.getInstance(file.getProject());
        boolean notifyInspector = inspector.hasSemanticTokensInspectorListener();
        List<SemanticTokensHighlightInfo> highlightInfos = notifyInspector ? new ArrayList<>() : null;

        try {
            var dataStream = semanticTokens.getData();
            if (dataStream == null || dataStream.isEmpty()) {
                return;
            }

            int idx = 0;
            int prevLine = 0;
            int line = 0;
            int offset = 0;
            int length = 0;
            String tokenType = null;
            int cancelCounter = 0;
            for (Integer data : dataStream) {
                // Cancel LSP semantic tokens support as soon as possible.
                cancelCounter++;
                if (cancelCounter >= 100) {
                    cancelCounter = 0;
                    ProgressManager.checkCanceled();
                }

                switch (idx % 5) {
                    case 0: // line
                        line += data;
                        break;
                    case 1: // offset
                        if (line == prevLine) {
                            offset += data;
                        } else {
                            offset = LSPIJUtils.toOffset(line, data, document);
                        }
                        break;
                    case 2: // length
                        length = data;
                        break;
                    case 3: // token type
                        tokenType = tokenType(data, semanticTokensLegend.getTokenTypes());
                        break;
                    case 4: // token modifier
                        prevLine = line;
                        List<String> tokenModifiers = tokenModifiers(data, semanticTokensLegend.getTokenModifiers());
                        int colorIndex = 0;//UsedColors.getOrAddColorIndex((UserDataHolderEx) context, tokenType, highlighter.getColorsCount());
                        int start = offset;
                        int end = offset + length;
                        TextAttributesKey colorKey = tokenType != null ? semanticTokensColorsProvider.getTextAttributesKey(tokenType, tokenModifiers, file) : null;
                        if (colorKey != null) {
                            addInfo.accept(start, end, colorKey);
                        }

                        // If this file uses a view provider based on semantic tokens, add this one
                        if (semanticTokensFileViewProvider != null) {
                            semanticTokensFileViewProvider.addSemanticToken(TextRange.create(start, end), tokenType, tokenModifiers);
                        }

                        if (notifyInspector) {
                            highlightInfos.add(new SemanticTokensHighlightInfo(tokenType, tokenModifiers, start, end, colorKey));
                        }
                        break;
                }
                idx++;
            }
        } finally {
            if (notifyInspector) {
                inspector.notify(new SemanticTokensInspectorData(document, file, highlightInfos));
            }
        }
    }

    private List<String> tokenModifiers(Integer data, List<String> legend) {
        if (data.intValue() == 0) {
            return Collections.emptyList();
        }
        final var bitSet = BitSet.valueOf(new long[]{data});
        final var tokenModifiers = new ArrayList<String>();
        for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
            try {
                tokenModifiers.add(legend.get(i));
            } catch (IndexOutOfBoundsException e) {
                // no match
            }
        }
        return tokenModifiers;
    }

    private String tokenType(Integer index, List<String> tokenTypes) {
        if (index == null || index >= tokenTypes.size()) {
            return null;
        }
        return tokenTypes.get(index);
    }
}

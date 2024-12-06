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
package com.redhat.devtools.lsp4ij.features.foldingRange;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP folding range builder.
 */
public class LSPFoldingRangeBuilder extends CustomFoldingBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPFoldingRangeBuilder.class);

    // TODO: Can this be derived from the language server? If not, these are the standard for modern languages
    private static final Map<Character, Character> BRACE_PAIR_CHARS_FWD = Map.of(
            '{', '}',
            '[', ']',
            '(', ')'
    );
    private static final Map<Character, Character> BRACE_PAIR_CHARS_BWD = ContainerUtil.reverseMap(BRACE_PAIR_CHARS_FWD);

    @Override
    protected void buildLanguageFoldRegions(@NotNull List<FoldingDescriptor> descriptors,
                                            @NotNull PsiElement root,
                                            @NotNull Document document, boolean quick) {
        // if quick flag is set, we do nothing here
        if (quick) {
            return;
        }

        PsiFile file = root.getContainingFile();
        List<FoldingRange> foldingRanges = getFoldingRanges(file);
        if (!ContainerUtil.isEmpty(foldingRanges)) {
            for (FoldingRange foldingRange : foldingRanges) {
                TextRange textRange = getTextRange(foldingRange, document);
                if ((textRange != null) && (textRange.getLength() > 0)) {
                    String collapsedText = foldingRange.getCollapsedText();
                    if (collapsedText != null) {
                        descriptors.add(new FoldingDescriptor(root.getNode(), textRange, null, collapsedText));
                    } else {
                        descriptors.add(new FoldingDescriptor(root.getNode(), textRange));
                    }
                }
            }
        }
    }

    @NotNull
    static List<FoldingRange> getFoldingRanges(@Nullable PsiFile file) {
        if (ProjectIndexingManager.canExecuteLSPFeature(file) != ExecuteLSPFeatureStatus.NOW) {
            return Collections.emptyList();
        }

        if (file == null) {
            return Collections.emptyList();
        }

        // Consume LSP 'textDocument/foldingRanges' request
        LSPFoldingRangeSupport foldingRangeSupport = LSPFileSupport.getSupport(file).getFoldingRangeSupport();
        var params = new FoldingRangeRequestParams(LSPIJUtils.toTextDocumentIdentifier(file.getVirtualFile()));
        CompletableFuture<List<FoldingRange>> foldingRangesFuture = foldingRangeSupport.getFoldingRanges(params);
        try {
            waitUntilDone(foldingRangesFuture, file);
        } catch (
                ProcessCanceledException e) {//Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
            //TODO delete block when minimum required version is 2024.2
            foldingRangeSupport.cancel();
            return Collections.emptyList();
        } catch (CancellationException e) {
            // cancel the LSP requests textDocument/foldingRanges
            foldingRangeSupport.cancel();
            return Collections.emptyList();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/foldingRanges' request", e);
            return Collections.emptyList();
        }

        if (!isDoneNormally(foldingRangesFuture)) {
            return Collections.emptyList();
        }

        // textDocument/foldingRanges has been collected correctly, create list of IJ FoldingDescriptor from LSP FoldingRange list
        return foldingRangesFuture.getNow(null);
    }

    @Nullable
    private static TextRange getTextRange(@NotNull FoldingRange foldingRange, @NotNull Document document) {
        return getTextRange(foldingRange, document, null, null);
    }

    /**
     * Returns the IDE text range for the LSP folding range, optionally bounded by the expected paired open/close braces.
     *
     * @param foldingRange   the LSP folding range
     * @param document       the document
     * @param openBraceChar  the optional open brace character
     * @param closeBraceChar the optional paired close brace character
     * @return the corresponding IDE text range, or null if no valid text range could be derived
     */
    @Nullable
    static TextRange getTextRange(
            @NotNull FoldingRange foldingRange,
            @NotNull Document document,
            @Nullable Character openBraceChar,
            @Nullable Character closeBraceChar
    ) {
        TextRange textRange = null;

        CharSequence documentChars = document.getCharsSequence();
        int documentLength = documentChars.length();

        int start = getStartOffset(foldingRange, document);
        Character startChar = start > 0 ? documentChars.charAt(start - 1) : null;
        if ((startChar != null) && ((openBraceChar == null) || (startChar == openBraceChar))) {
            // If necessary, infer the braces for this block
            if ((openBraceChar == null) && isOpenBraceChar(startChar)) {
                openBraceChar = startChar;
                closeBraceChar = getCloseBraceChar(openBraceChar);
            }

            int end = getEndOffset(foldingRange, document);
            // The end offsets can fall a bit short, so look for the closing brace character
            if (closeBraceChar != null) {
                while ((end < documentLength) && (documentChars.charAt(end) != closeBraceChar)) {
                    end++;
                }
            }
            if (end >= start) {
                Character endChar = end < documentLength ? documentChars.charAt(end) : null;
                if ((endChar != null) && ((closeBraceChar == null) || (endChar == closeBraceChar))) {
                    textRange = TextRange.create(start, end);
                }
            }
        }

        return textRange;
    }

    static boolean isOpenBraceChar(@Nullable Character character) {
        return (character != null) && BRACE_PAIR_CHARS_FWD.containsKey(character);
    }

    static boolean isCloseBraceChar(@Nullable Character character) {
        return (character != null) && BRACE_PAIR_CHARS_BWD.containsKey(character);
    }

    @Nullable
    static Character getOpenBraceChar(@Nullable Character closeBraceChar) {
        return closeBraceChar != null ? BRACE_PAIR_CHARS_BWD.get(closeBraceChar) : null;
    }

    @Nullable
    static Character getCloseBraceChar(@Nullable Character openBraceChar) {
        return openBraceChar != null ? BRACE_PAIR_CHARS_FWD.get(openBraceChar) : null;
    }

    private static int getStartOffset(@NotNull FoldingRange foldingRange, @NotNull Document document) {
        if (foldingRange.getStartCharacter() == null) {
            return document.getLineEndOffset(foldingRange.getStartLine());
        }
        return LSPIJUtils.toOffset(new Position(foldingRange.getStartLine(), foldingRange.getStartCharacter()), document);
    }

    private static int getEndOffset(@NotNull FoldingRange foldingRange, @NotNull Document document) {
        if (foldingRange.getEndCharacter() == null) {
            return document.getLineEndOffset(foldingRange.getEndLine());
        }
        return LSPIJUtils.toOffset(new Position(foldingRange.getEndLine(), foldingRange.getEndCharacter()), document);
    }

    @Override
    protected String getLanguagePlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
        return null;
    }

    @Override
    protected boolean isRegionCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }

    @Override
    public boolean isDumbAware() {
        return false;
    }
}

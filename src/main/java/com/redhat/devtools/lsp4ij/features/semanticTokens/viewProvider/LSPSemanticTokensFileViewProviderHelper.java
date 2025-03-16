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

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.features.EditorBehaviorFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for {@link LSPSemanticTokensFileViewProvider} implementations to help fulfill its interface.
 */
public class LSPSemanticTokensFileViewProviderHelper implements LSPSemanticTokensContainer {

    private final LSPSemanticTokensFileViewProvider fileViewProvider;
    private final ThreadLocal<Integer> effectiveOffsetPtr = new InheritableThreadLocal<>();

    /**
     * Creates a helper for the provided semantic tokens file view provider.
     *
     * @param fileViewProvider the semantic tokens file view provider
     */
    public LSPSemanticTokensFileViewProviderHelper(@NotNull LSPSemanticTokensFileViewProvider fileViewProvider) {
        this.fileViewProvider = fileViewProvider;
    }

    @Nullable
    private PsiFile getFile() {
        // There should only be one PSI file
        List<PsiFile> allFiles = fileViewProvider.getAllFiles();
        PsiFile file = allFiles.size() == 1 ? ContainerUtil.getFirstItem(allFiles) : null;
        return (file != null) && file.isValid() && EditorBehaviorFeature.enableSemanticTokensFileViewProvider(file) ? file : null;
    }

    @Override
    public boolean isEnabled() {
        return getFile() != null;
    }

    @Override
    public boolean isKeyword(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return (semanticToken != null) && (semanticToken.getElementType() == LSPSemanticTokenElementType.KEYWORD);
    }

    @Override
    public boolean isOperator(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return (semanticToken != null) && (semanticToken.getElementType() == LSPSemanticTokenElementType.OPERATOR);
    }

    @Override
    public boolean isStringLiteral(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return (semanticToken != null) && (semanticToken.getElementType() == LSPSemanticTokenElementType.STRING);
    }

    @Override
    public boolean isNumericLiteral(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return (semanticToken != null) && (semanticToken.getElementType() == LSPSemanticTokenElementType.NUMBER);
    }

    @Override
    public boolean isRegularExpression(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return (semanticToken != null) && (semanticToken.getElementType() == LSPSemanticTokenElementType.REGEXP);
    }

    @Override
    public boolean isComment(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return (semanticToken != null) && (semanticToken.getElementType() == LSPSemanticTokenElementType.COMMENT);
    }

    @Override
    public boolean isDeclaration(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return (semanticToken != null) && (semanticToken.getElementType() == LSPSemanticTokenElementType.DECLARATION);
    }

    @Override
    public boolean isReference(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return (semanticToken != null) && (semanticToken.getElementType() == LSPSemanticTokenElementType.REFERENCE);
    }

    @Override
    public boolean isUnknown(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return (semanticToken != null) && (semanticToken.getElementType() == LSPSemanticTokenElementType.UNKNOWN);
    }

    @NotNull
    public ThreeState isIdentifier(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return (semanticToken != null) ? semanticToken.isIdentifier() :
                isWhitespace(offset) ? ThreeState.NO :
                        ThreeState.UNSURE;
    }

    @Override
    @NotNull
    public ThreeState isType(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return (semanticToken != null) ? semanticToken.isType() : ThreeState.UNSURE;
    }

    @Override
    public boolean isWhitespace(int offset) {
        PsiFile file = getFile();
        Document document = (file != null) && (offset >= 0) && (offset < file.getTextLength()) ? LSPIJUtils.getDocument(file) : null;
        return (document != null) && Character.isWhitespace(document.getCharsSequence().charAt(offset));
    }

    @Nullable
    @Override
    public TextRange getSemanticTokenTextRange(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return semanticToken != null ? semanticToken.getTextRange() : null;
    }

    // Store the file's semantic tokens so that we have constant-time lookup of an element for a given offset
    @Nullable
    private Map<Integer, LSPSemanticToken> getSemanticTokensByOffset() {
        PsiFile file = getFile();
        if (file == null) return null;

        // By caching the storage on the file this way, it's automatically evicted when the file changes
        return CachedValuesManager.getCachedValue(file, new CachedValueProvider<>() {
            @Override
            @NotNull
            public Result<Map<Integer, LSPSemanticToken>> compute() {
                Map<Integer, LSPSemanticToken> semanticTokensByOffset = new ConcurrentHashMap<>();
                return Result.create(semanticTokensByOffset, file);
            }
        });
    }

    @Override
    public void addSemanticToken(@NotNull TextRange textRange,
                                 @Nullable String tokenType,
                                 @Nullable List<String> tokenModifiers) {
        PsiFile file = getFile();
        if (file == null) return;

        Map<Integer, LSPSemanticToken> semanticTokensByOffset = getSemanticTokensByOffset();
        if (semanticTokensByOffset != null) {
            LSPSemanticToken semanticToken = new LSPSemanticToken(file, textRange, tokenType, tokenModifiers);

            // Index the token for its text range
            for (int offset = textRange.getStartOffset(); offset <= textRange.getEndOffset(); offset++) {
                semanticTokensByOffset.put(offset, semanticToken);
            }
        }
    }

    /**
     * Returns the semantic token for the offset.
     *
     * @param offset the offset
     * @return the semantic token or null if no semantic token exists at the offset
     */
    @Nullable
    LSPSemanticToken getSemanticToken(int offset) {
        PsiFile file = getFile();
        if (file == null) return null;

        // If this file has semantic tokens, use them
        Map<Integer, LSPSemanticToken> semanticTokensByOffset = getSemanticTokensByOffset();
        if (!ContainerUtil.isEmpty(semanticTokensByOffset)) {
            LSPSemanticToken semanticToken = semanticTokensByOffset.get(offset);
            // Update the view provider's effective offset as appropriate
            setEffectiveOffset(semanticToken == null ? offset : -1);
            return semanticToken;
        }
        // Otherwise stub a semantic token for the entire file so that it won't highlight as a link on mouse hover
        else {
            // By caching the stub on the file this way, it's automatically evicted when the file changes
            LSPSemanticToken fileLevelSemanticToken = CachedValuesManager.getCachedValue(file, new CachedValueProvider<>() {
                @Override
                @NotNull
                public Result<LSPSemanticToken> compute() {
                    LSPSemanticToken stubSemanticToken = new LSPSemanticToken(file, file.getTextRange(), null, null);
                    return Result.create(stubSemanticToken, file);
                }
            });
            // Update the file-level token's requested offset
            fileLevelSemanticToken.setLastRequestedOffset(offset);
            return fileLevelSemanticToken;
        }
    }

    /**
     * Stores the effective offset as a thread local.
     *
     * @param offset the effective offset
     */
    private void setEffectiveOffset(int offset) {
        effectiveOffsetPtr.set(offset);
    }

    @Override
    public int getEffectiveOffset(@NotNull PsiElement element) {
        PsiFile file = getFile();
        if (file != null) {
            int effectiveOffset = -1;

            // First try to get it from the element; this will generally be for a file with no semantic tokens
            if ((element instanceof LSPSemanticTokenPsiElement semanticTokenElement) &&
                    (element.getContainingFile().getViewProvider() == fileViewProvider)) {
                effectiveOffset = semanticTokenElement.getEffectiveOffset();
            }

            // Failing that, try to get it from the view provider; this will generally for a file with semantic tokens
            // but the provided element doesn't correspond to one
            Integer viewProviderEffectiveOffset = effectiveOffsetPtr.get();
            if (viewProviderEffectiveOffset != null) {
                effectiveOffset = viewProviderEffectiveOffset;
            }

            // If we have a valid offset for the file, return it
            if ((effectiveOffset > -1) && file.getTextRange().contains(effectiveOffset)) {
                return effectiveOffset;
            }
        }

        return -1;
    }
}

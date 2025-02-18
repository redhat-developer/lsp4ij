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

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link FileViewProvider} for an LSP-backed TextMate file where elements are derived dynamically from reported
 * semantic tokens.
 */
@ApiStatus.Internal
public class LSPSemanticTokensFileViewProvider extends SingleRootFileViewProvider {

    /**
     * Creates a new file view provider.
     *
     * @param psiManager         the PSI manager
     * @param virtualFile        the virtual file
     * @param eventSystemEnabled whether or not the event system is enabled
     * @param language           the language which should always be TextMate
     */
    LSPSemanticTokensFileViewProvider(@NotNull PsiManager psiManager,
                                      @NotNull VirtualFile virtualFile,
                                      boolean eventSystemEnabled,
                                      @NotNull Language language) {
        super(psiManager, virtualFile, eventSystemEnabled, language);
    }

    @Override
    public boolean supportsIncrementalReparse(@NotNull Language rootLanguage) {
        // If this file is supported by LSP, there's no possibility of incremental reparse
        if (isSupported()) {
            return false;
        }

        // Otherwise defer to the inherited behavior
        return super.supportsIncrementalReparse(rootLanguage);
    }

    // File status

    private boolean isSupported() {
        return isSupported(getFile());
    }

    @Contract("null -> false")
    private boolean isSupported(@Nullable PsiFile file) {
        // Again, this is only ever active for TextMate files associated with a language server definition
        return (file != null) && file.isValid() && LSPIJEditorUtils.isSupportedTextMateFile(file);
    }

    @Nullable
    private PsiFile getFile() {
        // There should only be one PSI file
        return ContainerUtil.getFirstItem(getAllFiles());
    }

    // This is a convenience that combines the notion of both isSupported() and getFile() where a single null check
    // is sufficient to know whether or not logic should proceed
    @Nullable
    private PsiFile getSupportedFile() {
        PsiFile file = getFile();
        return isSupported(file) ? file : null;
    }

    // Element and reference resolution

    /**
     * Returns whether or not the semantic token/element at the offset is for a declaration.
     *
     * @param offset the offset
     * @return whether or not the semantic token/element at the offset is for a declaration
     */
    @ApiStatus.Internal
    public boolean isDeclaration(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return (semanticToken != null) && (semanticToken.getElementType() == LSPSemanticTokenElementType.DECLARATION);
    }

    /**
     * Returns whether or not the semantic token/element at the offset is for a reference.
     *
     * @param offset the offset
     * @return whether or not the semantic token/element at the offset is for a reference
     */
    @ApiStatus.Internal
    public boolean isReference(int offset) {
        LSPSemanticToken semanticToken = getSemanticToken(offset);
        return (semanticToken != null) && (semanticToken.getElementType() == LSPSemanticTokenElementType.REFERENCE);
    }

    // NOTE: These are really the core of what makes this all work. Basically when any external caller needs to
    // find an element or reference for a given offset in the file, we use the semantic token information that
    // was populated the last time that semantic tokens were returned by the language server to return an element
    // at that offset (or not). In all cases, we take great care to delegate to the inherited behavior if we
    // don't know for a fact that we can/should respond ourselves. This ensures that non-LSP4IJ TextMate files
    // see no change in behavior.

    @Override
    public PsiElement findElementAt(int offset) {
        if (isSupported()) {
            LSPSemanticToken semanticToken = getSemanticToken(offset);
            return semanticToken != null ? semanticToken.getElement() : super.findElementAt(offset);
        } else {
            return super.findElementAt(offset);
        }
    }

    @Override
    public PsiElement findElementAt(int offset, @NotNull Class<? extends Language> lang) {
        return isSupported() ? findElementAt(offset) : super.findElementAt(offset, lang);
    }

    @Override
    public PsiElement findElementAt(int offset, @NotNull Language language) {
        return isSupported() ? findElementAt(offset) : super.findElementAt(offset, language);
    }

    @Override
    public PsiReference findReferenceAt(int offset) {
        if (isSupported()) {
            LSPSemanticToken semanticToken = getSemanticToken(offset);
            LSPSemanticTokenElementType elementType = semanticToken != null ? semanticToken.getElementType() : null;
            return elementType == LSPSemanticTokenElementType.REFERENCE ? new LSPSemanticTokenPsiReference(semanticToken) : null;
        } else {
            return super.findReferenceAt(offset);
        }
    }

    @Override
    @Nullable
    public PsiReference findReferenceAt(int offset, @NotNull Language language) {
        return isSupported() ? findReferenceAt(offset) : super.findReferenceAt(offset, language);
    }

    // Element description

    /**
     * Returns the description for the specified element, optionally formatted as appropriate from the perspective of
     * the provided reference element. For example, if both are elements are in the same file, there's no reason to
     * include the target element's file name in the description, but if they're in different files, it's useful to
     * know where the target element resides.
     *
     * @param element          the element for which a description should be returned
     * @param referenceElement the reference element from which the description is being requested
     * @return the element description
     */
    @Nullable
    @ApiStatus.Internal
    public String getElementDescription(@NotNull PsiElement element, @Nullable PsiElement referenceElement) {
        if (findElementAt(element.getTextOffset()) instanceof LSPSemanticTokenPsiElement semanticTokenElement) {
            return semanticTokenElement.getDescription(referenceElement);
        }
        return null;
    }

    // Semantic tokens management

    // Store the file's semantic tokens so that we have constant-time lookup of an element for a given offset
    @Nullable
    private Map<Integer, LSPSemanticToken> getSemanticTokensByOffset() {
        PsiFile file = getSupportedFile();
        if (file != null) {
            // By caching the storage on the file this way, it's automatically evicted when the file changes
            return CachedValuesManager.getCachedValue(file, new CachedValueProvider<>() {
                @Override
                @NotNull
                public Result<Map<Integer, LSPSemanticToken>> compute() {
                    Map<Integer, LSPSemanticToken> semanticTokensByOffset = Collections.synchronizedMap(new HashMap<>());
                    return Result.create(semanticTokensByOffset, file);
                }
            });
        }
        return null;
    }

    /**
     * Adds a semantic token for the file.
     *
     * @param textRange      the token's text range in the file
     * @param tokenType      the token type
     * @param tokenModifiers the token modifiers
     */
    @ApiStatus.Internal
    public void addSemanticToken(@NotNull TextRange textRange,
                                 @Nullable String tokenType,
                                 @Nullable List<String> tokenModifiers) {
        PsiFile file = getSupportedFile();
        if (file != null) {
            Map<Integer, LSPSemanticToken> semanticTokensByOffset = getSemanticTokensByOffset();
            if (semanticTokensByOffset != null) {
                LSPSemanticToken semanticToken = new LSPSemanticToken(file, textRange, tokenType, tokenModifiers);

                // Index the token for its text range
                for (int offset = textRange.getStartOffset(); offset <= textRange.getEndOffset(); offset++) {
                    semanticTokensByOffset.put(offset, semanticToken);
                }
            }
        }
    }

    @Nullable
    private LSPSemanticToken getSemanticToken(int offset) {
        PsiFile file = getSupportedFile();
        if (file != null) {
            // If this file has semantic tokens, use them
            Map<Integer, LSPSemanticToken> semanticTokensByOffset = getSemanticTokensByOffset();
            if (!ContainerUtil.isEmpty(semanticTokensByOffset)) {
                return semanticTokensByOffset.get(offset);
            }
            // Otherwise stub a semantic token for the entire file so that it won't highlight as a link on mouse hover
            else {
                // By caching the stub on the file this way, it's automatically evicted when the file changes
                return CachedValuesManager.getCachedValue(file, new CachedValueProvider<>() {
                    @Override
                    @NotNull
                    public Result<LSPSemanticToken> compute() {
                        LSPSemanticToken stubSemanticToken = new LSPSemanticToken(file, file.getTextRange(), null, null);
                        return Result.create(stubSemanticToken, file);
                    }
                });
            }
        }

        return null;
    }
}

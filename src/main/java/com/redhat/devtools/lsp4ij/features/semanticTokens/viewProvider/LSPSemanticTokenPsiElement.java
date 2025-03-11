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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import com.redhat.devtools.lsp4ij.ui.IconMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Objects;

/**
 * A PSI element for a semantic token in a {@link LSPSemanticTokensFileViewProvider} file.
 */
public class LSPSemanticTokenPsiElement extends LSPPsiElement implements PsiNameIdentifierOwner {
    private final LSPSemanticToken semanticToken;
    private final Icon icon;
    private volatile LeafPsiElement node = null;
    private volatile String text = null;

    /**
     * Creates a new PSI element for the provided semantic token.
     *
     * @param semanticToken the semantic token
     */
    LSPSemanticTokenPsiElement(@NotNull LSPSemanticToken semanticToken) {
        super(semanticToken.getFile(), semanticToken.getTextRange());
        this.semanticToken = semanticToken;
        // If this is a declaration or reference, try to use an appropriate icon
        LSPSemanticTokenElementType elementType = semanticToken.getElementType();
        this.icon = (elementType == LSPSemanticTokenElementType.DECLARATION) || (elementType == LSPSemanticTokenElementType.REFERENCE) ?
                IconMapper.getIcon(semanticToken.getTokenType()) :
                null;
    }

    /**
     * Returns the effective offset for the element.
     *
     * @return the element's effective offset
     */
    int getEffectiveOffset() {
        int lastRequestedOffset = semanticToken.getLastRequestedOffset();
        return lastRequestedOffset > -1 ? lastRequestedOffset : getTextOffset();
    }

    @Override
    public boolean isPhysical() {
        // These do represent real text ranges in physical files
        return true;
    }

    @Override
    public boolean isValid() {
        // Tie the validity of this element to that of its containing file
        return semanticToken.getFile().isValid();
    }

    @Override
    public boolean canNavigate() {
        // References can be navigated
        return semanticToken.getElementType() == LSPSemanticTokenElementType.REFERENCE;
    }

    @Override
    public String getName() {
        return semanticToken.isFileLevel() ? getContainingFile().getName() : super.getName();
    }

    @Override
    @Nullable
    public PsiElement getNameIdentifier() {
        // If this is a declaration, return this element as the name identifier
        return semanticToken.getElementType() == LSPSemanticTokenElementType.DECLARATION ? this : null;
    }

    @Override
    @Nullable
    public Icon getIcon(boolean open) {
        return icon != null ? icon : super.getIcon(open);
    }

    @Override
    @Nullable
    public ASTNode getNode() {
        // This is lazy-initialized because it needs the element text which is itself lazy-initialized
        if (node == null) {
            synchronized (this) {
                if (node == null) {
                    node = new LeafPsiElement(semanticToken.getElementType(), getText());
                }
            }
        }

        return node;
    }

    /**
     * Returns the element's semantic token type.
     *
     * @return the element's semantic token type, or null if the element doesn't have a token type
     */
    @Nullable
    public String getType() {
        return semanticToken.getTokenType();
    }

    @Override
    @NotNull
    public String getText() {
        // Optimization for full-file elements to avoid copying the full file text
        if (semanticToken.isFileLevel()) return getContainingFile().getText();

        // This is lazy-initialized because to avoid having to derive it until/unless needed
        if (text == null) {
            synchronized (this) {
                if (text == null) {
                    String workingText = "";
                    TextRange textRange = getTextRange();
                    if (textRange != null) {
                        Document document = LSPIJUtils.getDocument(this);
                        CharSequence documentChars = document != null ? document.getCharsSequence() : null;
                        CharSequence textChars;
                        if (documentChars != null) {
                            int startOffset = Math.max(textRange.getStartOffset(), 0);
                            int endOffset = Math.min(textRange.getEndOffset(), documentChars.length());
                            textChars = documentChars.subSequence(startOffset, endOffset);
                        } else {
                            textChars = null;
                        }
                        if (textChars != null) {
                            workingText = textChars.toString();
                        }
                    }
                    text = workingText;
                }
            }
        }

        return text;
    }

    @Override
    public boolean equals(Object o) {
        if ((o == null) || (getClass() != o.getClass())) return false;
        LSPSemanticTokenPsiElement that = (LSPSemanticTokenPsiElement) o;
        return Objects.equals(semanticToken, that.semanticToken);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(semanticToken);
    }
}

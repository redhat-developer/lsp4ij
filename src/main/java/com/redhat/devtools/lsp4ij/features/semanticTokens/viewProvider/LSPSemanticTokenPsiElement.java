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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A PSI element for a semantic token in a {@link LSPSemanticTokensFileViewProvider} file.
 */
public class LSPSemanticTokenPsiElement extends LSPPsiElement implements PsiNameIdentifierOwner {
    private final LSPSemanticToken semanticToken;
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
    }

    @NotNull
    LSPSemanticToken getSemanticToken() {
        return semanticToken;
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
    @Nullable
    public PsiElement getNameIdentifier() {
        // If this is a declaration, return this element as the name identifier
        return getSemanticToken().getElementType() == LSPSemanticTokenElementType.DECLARATION ? this : null;
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

    @Override
    @NotNull
    public String getText() {
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

    @Nullable
    public String getDescription(@Nullable PsiElement referenceElement) {
        // Only declaration elements have description text
        if (semanticToken.getElementType() == LSPSemanticTokenElementType.DECLARATION) {
            String tokenType = semanticToken.getTokenType();
            PsiFile file = getContainingFile();
            return "<html>" +
                    (StringUtil.isNotEmpty(tokenType) ? StringUtil.capitalize(tokenType) + " " : "") +
                    "<code>" + StringUtil.escapeXmlEntities(getText()) + "</code>" +
                    // If the reference is in another file, include this element's file name in the description
                    ((referenceElement != null) && !Objects.equals(file, referenceElement.getContainingFile()) ? " in <code>" + file.getName() + "</code>" : "") +
                    "</html>";
        }

        return null;
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

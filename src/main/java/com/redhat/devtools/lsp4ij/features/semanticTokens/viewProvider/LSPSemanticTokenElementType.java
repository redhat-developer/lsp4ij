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

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * LSP semantic token element types.
 */
class LSPSemanticTokenElementType extends IElementType {
    // These are the concrete element types that we can derive from semantic token types and modifiers
    static final LSPSemanticTokenElementType DECLARATION = new LSPSemanticTokenElementType("DECLARATION");
    static final LSPSemanticTokenElementType REFERENCE = new LSPSemanticTokenElementType("REFERENCE");
    static final LSPSemanticTokenElementType KEYWORD = new LSPSemanticTokenElementType("KEYWORD");
    static final LSPSemanticTokenElementType COMMENT = new LSPSemanticTokenElementType("COMMENT");
    static final LSPSemanticTokenElementType STRING = new LSPSemanticTokenElementType("STRING");
    static final LSPSemanticTokenElementType NUMBER = new LSPSemanticTokenElementType("NUMBER");
    static final LSPSemanticTokenElementType REGEXP = new LSPSemanticTokenElementType("REGEXP");
    static final LSPSemanticTokenElementType OPERATOR = new LSPSemanticTokenElementType("OPERATOR");
    static final LSPSemanticTokenElementType UNKNOWN = new LSPSemanticTokenElementType("UNKNOWN");

    LSPSemanticTokenElementType(@NonNls @NotNull String debugName) {
        super(debugName, LSPSemanticTokenLanguage.INSTANCE);
    }
}

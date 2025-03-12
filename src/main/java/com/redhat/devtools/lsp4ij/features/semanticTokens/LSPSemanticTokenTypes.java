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

package com.redhat.devtools.lsp4ij.features.semanticTokens;

import org.eclipse.lsp4j.SemanticTokenTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom semantic token types.
 */
@ApiStatus.Internal
public class LSPSemanticTokenTypes {

    // https://code.visualstudio.com/api/language-extensions/semantic-highlight-guide#standard-token-types-and-modifiers
    private static final LSPSemanticTokenType Label = new LSPSemanticTokenType(
            "label",
            true,
            false,
            false,
            false,
            null,
            SemanticTokensHighlightingColors.LABEL.getExternalName()
    );

    // TypeScript "member"
    private static final LSPSemanticTokenType Member = new LSPSemanticTokenType(
            "member",
            true,
            false,
            false,
            false,
            SemanticTokenTypes.Method,
            null
    );

    // SourceKit "identifier" which should be considered a declaration
    private static final LSPSemanticTokenType Identifier = new LSPSemanticTokenType(
            "identifier",
            true,
            true,
            false,
            false,
            SemanticTokenTypes.Variable,
            null
    );

    // All custom semantic token types
    private static final LSPSemanticTokenType[] values = new LSPSemanticTokenType[]{
            Label,
            Member,
            Identifier
    };

    // An index for finding a custom semantic token type by its name
    private static final Map<String, LSPSemanticTokenType> nameIndex = new LinkedHashMap<>();

    static {
        for (LSPSemanticTokenType value : values) {
            nameIndex.put(value.getName(), value);
        }
    }

    /**
     * Returns all custom semantic token types.
     *
     * @return all custom semantic token types
     */
    @NotNull
    @ApiStatus.Internal
    public static LSPSemanticTokenType[] values() {
        return values;
    }

    /**
     * Returns the custom semantic token type with the specified name.
     *
     * @param name the custom semantic token type name
     * @return the custom semantic token type, or null if none exists with the specified name
     */
    @Nullable
    @ApiStatus.Internal
    public static LSPSemanticTokenType valueOf(@NotNull String name) {
        return nameIndex.get(name);
    }
}

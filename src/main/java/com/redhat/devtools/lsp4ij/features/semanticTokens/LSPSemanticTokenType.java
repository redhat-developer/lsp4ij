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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A custom semantic token type.
 */
@ApiStatus.Internal
public class LSPSemanticTokenType {

    private final String name;
    private final boolean identifier;
    private final boolean declaration;
    private final boolean type;
    private final boolean keyword;
    private final String inheritFrom;
    private final String textAttributesKey;

    /**
     * Creates a custom semantic token type.
     *
     * @param name              the name
     * @param identifier        whether or not the token represents an identifier
     * @param declaration       whether or not the token represents a declaration
     * @param type              whether or not the token represents a type
     * @param keyword           whether or not the token represents a keyword
     * @param inheritFrom       an optional existing semantic token type from which this one should inherit its behavior
     * @param textAttributesKey an optional text attributes key that should be used for syntax highlighting
     */
    LSPSemanticTokenType(@NotNull String name,
                         boolean identifier,
                         boolean declaration,
                         boolean type,
                         boolean keyword,
                         @Nullable String inheritFrom,
                         @Nullable String textAttributesKey) {
        this.name = name;
        this.identifier = identifier;
        this.declaration = declaration;
        this.type = type;
        this.keyword = keyword;
        this.inheritFrom = inheritFrom;
        this.textAttributesKey = textAttributesKey;
    }

    /**
     * The semantic token type name.
     */
    @NotNull
    @ApiStatus.Internal
    public String getName() {
        return name;
    }

    /**
     * Whether or not the semantic token should be interpreted as being an identifier.
     */
    @ApiStatus.Internal
    public boolean isIdentifier() {
        return identifier;
    }

    /**
     * Whether or not the semantic token should be interpreted as being a declaration.
     */
    @ApiStatus.Internal
    public boolean isDeclaration() {
        return declaration;
    }

    /**
     * Whether or not the semantic token should be interpreted as being a type.
     */
    @ApiStatus.Internal
    public boolean isType() {
        return type;
    }

    /**
     * Whether or not the semantic token should be interpreted as being a keyword.
     */
    @ApiStatus.Internal
    public boolean isKeyword() {
        return keyword;
    }

    /**
     * The name of the existing semantic token type from which this one should inherit its behavior.
     */
    @Nullable
    @ApiStatus.Internal
    public String getInheritFrom() {
        return inheritFrom;
    }

    /**
     * The text attributes key that should be used for syntax highlighting of this semantic token.
     */
    @Nullable
    @ApiStatus.Internal
    public String getTextAttributesKey() {
        return textAttributesKey;
    }
}
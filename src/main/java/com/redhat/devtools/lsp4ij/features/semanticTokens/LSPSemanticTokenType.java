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
    /**
     * The semantic token type name
     */
    public String name;
    /**
     * Whether or not the semantic token should be interpreted as being an identifier
     */
    public boolean identifier;
    /**
     * Whether or not the semantic token should be interpreted as being a type
     */
    public boolean type;
    /**
     * Whether or not the semantic token should be interpreted as being a keyword
     */
    public boolean keyword;
    /**
     * The name of the existing semantic token type from which this one should inherit its behavior
     */
    public String inheritFrom;
    /**
     * The text attributes key that should be used for syntax highlighting of this semantic token
     */
    public String textAttributesKey;

    /**
     * Creates a custom semantic token type.
     *
     * @param name              the name
     * @param identifier        whether or not the token represents an identifier
     * @param type              whether or not the token represents a type
     * @param keyword           whether or not the token represents a keyword
     * @param inheritFrom       an optional existing semantic token type from which this one should inherit its behavior
     * @param textAttributesKey an optional text attributes key that should be used for syntax highlighting
     */
    LSPSemanticTokenType(@NotNull String name,
                         boolean identifier,
                         boolean type,
                         boolean keyword,
                         @Nullable String inheritFrom,
                         @Nullable String textAttributesKey) {
        this.name = name;
        this.identifier = identifier;
        this.type = type;
        this.keyword = keyword;
        this.inheritFrom = inheritFrom;
        this.textAttributesKey = textAttributesKey;
    }
}
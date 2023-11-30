/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet;

import java.util.function.Function;

/**
 * Abstract class LSP snippet handler (aka SAXHandler).
 *
 * @author Angelo ZERR
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax">https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax</a>
 */
public abstract class AbstractLspSnippetHandler implements LspSnippetHandler {

    private final Function<String, String> variableResolver;

    private final LspSnippetIndentOptions indentOptions;

    /**
     * Abstract LSP snippet handler constructor.
     *
     * @param variableResolver the variable resolver.
     * @param indentOptions the indent options to use to format text block which contains '\n' and '\t'.
     */
    public AbstractLspSnippetHandler(Function<String, String> variableResolver, LspSnippetIndentOptions indentOptions) {
        this.variableResolver = variableResolver;
        this.indentOptions = indentOptions;
    }

    /**
     * Replace '\n' and '\t' declared in the snippets according to the LSP client settings.
     *
     * @param text the text to format according to the LSP client settings.
     *
     * @return the result of '\n' and '\t' replacement declared in the snippets according to the LSP client settings.
     */
    protected String formatText(String text) {
        return indentOptions != null ? indentOptions.formatText(text) : text;
    }

    /**
     * Return the result of the resolved LSP variable and null otherwise.
     *
     * @param variableName the variable name to resolve.
     * @return the result of the resolved LSP variable and null otherwise.
     */
    protected String resolveVariable(String variableName) {
        if (variableResolver == null || variableName == null || variableName.isEmpty()) {
            return variableName;
        }
        return variableResolver.apply(variableName);
    }

}

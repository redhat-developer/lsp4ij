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

import java.util.List;
import java.util.function.Function;

/**
 * Default LSP snippet handler which provides:
 *
 * <ul>
 * <li>the snippet content without the placeholders.</li>
 * <li>the start offset of each placeholders</li>
 * </ul>
 *
 * @author Angelo ZERR
 */
public class DefaultLspSnippetHandler extends AbstractLspSnippetHandler {

    private final StringBuilder templateContent;

    /**
     * Default LSP snippet handler constructor.
     *
     * @param variableResolver the variable resolver.
     * @param indentOptions the indent options to use to format text block which contains '\n' and '\t'.
     */
    public DefaultLspSnippetHandler(Function<String, String> variableResolver, LspSnippetIndentOptions indentOptions) {
        super(variableResolver,  indentOptions);
        this.templateContent = new StringBuilder();
    }

    @Override
    public void startSnippet() {

    }

    @Override
    public void endSnippet() {

    }

    @Override
    public void text(String text) {
        appendContent(formatText(text));
    }

    @Override
    public void tabstop(int index) {

    }

    @Override
    public void choice(int index, List<String> choices) {
        String value = choices.isEmpty() ? "" : choices.get(0);
        appendContent(value);
    }

    @Override
    public void choice(String name, List<String> choices) {
        appendContent(name);
    }

    @Override
    public void startPlaceholder(int index, String name, int level) {
        appendContent(name);
    }

    @Override
    public void endPlaceholder(int level) {

    }

    @Override
    public void variable(String name) {
        appendContent(name);
    }

    public String getTemplateContent() {
        return templateContent.toString();
    }

    public int getCurrentOffset() {
        return templateContent.length();
    }

    protected void appendContent(String content) {
        if (content != null) {
            templateContent.append(content);
        }
    }
}

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
package com.redhat.devtools.intellij.lsp4ij.operations.completion;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.AbstractLspSnippetHandler;
import com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.LspSnippetHandler;
import com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.LspSnippetIndentOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * {@link LspSnippetHandler} implementation to load Intellij Template instance by using LSP snippet syntax content.
 *
 * @author Angelo ZERR
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax>https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax</a>
 */
public class SnippetTemplateLoader extends AbstractLspSnippetHandler {

    private final Template template;

    private final List<String> existingVariables;

    /**
     * Load the given Intellij template from a LSP snippet content by using the given variable resolver.
     *
     * @param template         the Intellij template to load.
     * @param variableResolver the variable resolver (ex : resolve value of TM_SELECTED_TEXT when snippet declares ${TM_SELECTED_TEXT})
     * @param indentOptions the LSP indent options to replace '\t' and '\n' characters according the code style settings of the language.
     */
    public SnippetTemplateLoader(Template template, Function<String, String> variableResolver, LspSnippetIndentOptions indentOptions) {
        super(variableResolver, indentOptions);
        this.template = template;
        this.existingVariables = new ArrayList<>();
    }

    @Override
    public void startSnippet() {

    }

    @Override
    public void endSnippet() {

    }

    @Override
    public void text(String text) {
        template.addTextSegment(formatText(text));
    }

    @Override
    public void tabstop(int index) {
        if (index == 0) {
            template.addEndVariable();
        } else {
            template.addVariable(new ConstantNode(""), true);
        }
    }

    @Override
    public void choice(int index, List<String> choices) {
        String value = choices.isEmpty() ? "" : choices.get(0);
        choice(value, choices);
    }

    @Override
    public void choice(String name, List<String> choices) {
        template.addVariable(new ConstantNode(name).withLookupStrings(choices), true);
    }

    @Override
    public void startPlaceholder(int index, String name, int level) {
        variable(name);
    }

    @Override
    public void endPlaceholder(int level) {

    }

    @Override
    public void variable(String name) {
        String resolvedValue = super.resolveVariable(name);
        if (resolvedValue != null) {
            // ex : ${TM_SELECTED_TEXT}
            // the TM_SELECTED_TEXT is resolved, we do a simple replacement
            template.addVariable(new ConstantNode(resolvedValue), false);
        } else {
            if (existingVariables.contains(name)) {
                // The variable (ex : ${name}) has already been declared, add a simple variable segment
                // which will be updated by the previous add variable
                template.addVariableSegment(name);
            } else {
                // The variable doesn't exists, add a variable which can be updated
                // and which will replace other variables with the same name.
                existingVariables.add(name);
                template.addVariable(name, new ConstantNode(name), null, true, false);
            }
        }
    }

}

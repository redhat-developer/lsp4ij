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

package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.util.text.StringUtil;
import com.redhat.devtools.lsp4ij.features.completion.snippet.AbstractLspSnippetHandler;
import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetHandler;
import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetIndentOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

/**
 * {@link LspSnippetHandler} implementation to load Intellij Template instance by using LSP snippet syntax content.
 *
 * @author Angelo ZERR
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax>https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax</a>
 */
public class SnippetTemplateLoader extends AbstractLspSnippetHandler {

    // NOTE: These are quite simple now and basically help look for a segment that ends with "(" (ignoring trailing
    // white space) to determine that an invocation has started and one that begins with ")" (again, ignore leading
    // white space) to determine that an invocation has ended. If LSP languages with more complex invocation patterns
    // are found these will need to be updated accordingly.
    private static final char INVOCATION_START = '(';
    private static final char INVOCATION_END = ')';

    private final Template template;
    private final boolean useTemplateForInvocationOnlySnippet;

    private final List<SnippetTemplateSegment> templateSegments = new LinkedList<>();
    private final List<String> existingVariables = new LinkedList<>();

    /**
     * Load the given Intellij template from a LSP snippet content by using the given variable resolver.
     *
     * @param template                            the Intellij template to load.
     * @param variableResolver                    the variable resolver (ex : resolve value of TM_SELECTED_TEXT when snippet declares ${TM_SELECTED_TEXT})
     * @param indentOptions                       the LSP indent options to replace '\t' and '\n' characters according the code style settings of the language.
     * @param useTemplateForInvocationOnlySnippet whether or not a template should be used for an invocation-only snippet
     */
    public SnippetTemplateLoader(@NotNull Template template,
                                 @Nullable Function<String, String> variableResolver,
                                 @Nullable LspSnippetIndentOptions indentOptions,
                                 boolean useTemplateForInvocationOnlySnippet) {
        super(variableResolver, indentOptions);
        this.template = template;
        this.useTemplateForInvocationOnlySnippet = useTemplateForInvocationOnlySnippet;
    }

    @Override
    public void startSnippet() {

    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public void endSnippet() {
        List<SnippetTemplateSegment> effectiveTemplateSegments = templateSegments;

        // If we should not use a template for an invocation-only snippet, see whether this fits the pattern
        if (!useTemplateForInvocationOnlySnippet) {
            int topLevelInvocationCount = 0;
            // While it's unlikely to happen in a typical invocation arg list scenario, this helps ensure we're only
            // reacting to the contents of balanced parentheses
            int nestedInvocationCount = 0;
            boolean hasVariablesOutsideInvocation = false;
            List<SnippetTemplateSegment> simplifiedTemplateSegments = new ArrayList<>(templateSegments.size());
            for (SnippetTemplateSegment templateSegment : templateSegments) {
                // Keep track of invocations
                if (templateSegment.isTextSegment()) {
                    String textSegment = templateSegment.getTextSegment();
                    if (StringUtil.trimTrailing(textSegment).endsWith(String.valueOf(INVOCATION_START))) {
                        // Increment the invocation depth
                        nestedInvocationCount += StringUtil.countChars(textSegment, INVOCATION_START);

                        // Only add segments for the top-level invocation
                        if (nestedInvocationCount == 1) {
                            topLevelInvocationCount++;

                            // Only add the portion starting with the first open paren
                            int firstOpenParenIndex = textSegment.indexOf(INVOCATION_START);
                            if (firstOpenParenIndex > -1) {
                                simplifiedTemplateSegments.add(SnippetTemplateSegment.textSegment(textSegment.substring(0, firstOpenParenIndex + 1)));
                            } else {
                                topLevelInvocationCount = 0;
                                break;
                            }

                            simplifiedTemplateSegments.add(SnippetTemplateSegment.endVariable());
                        }
                    } else {
                        if ((nestedInvocationCount > 0)) {
                            if (StringUtil.trimLeading(textSegment).startsWith(String.valueOf(INVOCATION_END))) {
                                // Decrement the invocation depth
                                nestedInvocationCount -= StringUtil.countChars(textSegment, INVOCATION_END);

                                // Add the invocation end text segment if this completes a top-level invocation
                                if (nestedInvocationCount == 0) {
                                    // Only add the portion starting with the last close paren
                                    int lastCloseParenIndex = textSegment.lastIndexOf(INVOCATION_END);
                                    if (lastCloseParenIndex > -1) {
                                        simplifiedTemplateSegments.add(SnippetTemplateSegment.textSegment(textSegment.substring(lastCloseParenIndex)));
                                    } else {
                                        topLevelInvocationCount = 0;
                                        break;
                                    }
                                }
                            }
                        }
                        // Otherwise only add text segments outside an invocation as invocations should only contain an end variable
                        else if (nestedInvocationCount == 0) {
                            simplifiedTemplateSegments.add(templateSegment);
                        }
                    }
                }
                // If we find a variable not inside an invocation, we can't simplify this template
                else if ((templateSegment.isVariable() || templateSegment.isNamedVariable()) && (nestedInvocationCount == 0)) {
                    hasVariablesOutsideInvocation = true;
                    break;
                }
            }

            // If we found exactly one top-level invocation (including balanced parens) and all variables were inside
            // of it, use the simplified template segments
            if ((topLevelInvocationCount == 1) && (nestedInvocationCount == 0) && !hasVariablesOutsideInvocation) {
                effectiveTemplateSegments = simplifiedTemplateSegments;
            }
        }

        // Build the template
        for (SnippetTemplateSegment templateSegment : effectiveTemplateSegments) {
            if (templateSegment.isTextSegment()) {
                template.addTextSegment(templateSegment.getTextSegment());
            } else if (templateSegment.isVariableSegment()) {
                template.addVariableSegment(templateSegment.getVariableSegment());
            } else if (templateSegment.isVariable()) {
                template.addVariable(templateSegment.getVariable(), templateSegment.isAlwaysStopAt());
            } else if (templateSegment.isNamedVariable()) {
                template.addVariable(
                        templateSegment.getVariableName(),
                        templateSegment.getVariable(),
                        templateSegment.getDefaultValueExpression(),
                        templateSegment.isAlwaysStopAt(),
                        templateSegment.isSkipOnStart()
                );
            } else if (templateSegment.isEndVariable()) {
                template.addEndVariable();
            }
        }
    }

    @Override
    public void text(String text) {
        templateSegments.add(SnippetTemplateSegment.textSegment(formatText(text)));
    }

    @Override
    public void tabstop(int index) {
        if (index == 0) {
            templateSegments.add(SnippetTemplateSegment.endVariable());
        } else {
            templateSegments.add(SnippetTemplateSegment.variable(new ConstantNode(""), true));
        }
    }

    @Override
    public void choice(int index, List<String> choices) {
        String value = choices.isEmpty() ? "" : choices.get(0);
        choice(value, choices);
    }

    @Override
    public void choice(String name, List<String> choices) {
        templateSegments.add(SnippetTemplateSegment.variable(new ConstantNode(name).withLookupStrings(choices), true));
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
            templateSegments.add(SnippetTemplateSegment.variable(new ConstantNode(resolvedValue), false));
        } else {
            if (existingVariables.contains(name)) {
                // The variable (ex : ${name}) has already been declared, add a simple variable segment
                // which will be updated by the previous add variable
                templateSegments.add(SnippetTemplateSegment.variableSegment(name));
            } else {
                // The variable doesn't exist, add a variable which can be updated
                // and which will replace other variables with the same name.
                existingVariables.add(name);
                templateSegments.add(SnippetTemplateSegment.namedVariable(name, new ConstantNode(name), null, true, false));
            }
        }
    }
}

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

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.util.text.StringUtil;
import com.redhat.devtools.lsp4ij.features.completion.snippet.AbstractLspSnippetHandler;
import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetDefaults;
import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetDefaults.Part;
import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetDefaults.TabStop;
import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetDefaults.Text;
import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetHandler;
import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetIndentOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
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
    // The default of each index, which its tab stops, placeholders and choices repeat (#2073)
    private final LspSnippetDefaults defaults = new LspSnippetDefaults();

    // The text of the outermost placeholder being read, with the text of the placeholders nested in it. A variable of
    // an IntelliJ template can't contain another, so a placeholder is one variable, whose text is its whole text, and
    // its first stop selects that text, as it does in VS Code (#2074). Its nested placeholders have no stops of their
    // own
    private @Nullable List<Part> placeholderParts;
    private int placeholderIndex;
    // The nested placeholders being read, inside out
    private final Deque<NestedPlaceholder> nestedPlaceholders = new ArrayDeque<>();

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
        linkTabStops();
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
                // If we find a variable not inside an invocation, or a tab stop that repeats one, we can't simplify
                // this template
                else if ((templateSegment.isVariable() || templateSegment.isNamedVariable() || templateSegment.isVariableSegment())
                        && (nestedInvocationCount == 0)) {
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

    /**
     * Gives each index one variable of the template, which all its tab stops, placeholders and choices share: the
     * first one in the snippet is the variable, and the others repeat it. Typing in one updates the others, as the LSP
     * specification says: "Multiple tab stops are linked and updated in sync" (#2073).
     */
    private void linkTabStops() {
        Set<Integer> declared = new HashSet<>();
        for (ListIterator<SnippetTemplateSegment> segments = templateSegments.listIterator(); segments.hasNext(); ) {
            SnippetTemplateSegment segment = segments.next();
            if (segment.isTabStop()) {
                int index = segment.getTabStopIndex();
                // An index isn't a name of a snippet variable, which starts with a letter or '_'
                String name = String.valueOf(index);
                segments.set(declared.add(index)
                        ? SnippetTemplateSegment.namedVariable(name, defaultOf(index), null, true, false)
                        : SnippetTemplateSegment.variableSegment(name));
            }
        }
    }

    /**
     * Returns the default of the given index, with the values of the choice that gave it.
     */
    private @NotNull Expression defaultOf(int index) {
        var node = new ConstantNode(defaults.getText(index));
        List<String> choices = defaults.getChoices(index);
        return choices != null ? node.withLookupStrings(choices) : node;
    }

    @Override
    public void text(String text) {
        if (placeholderParts != null) {
            appendToPlaceholder(new Text(formatText(text)));
            return;
        }
        templateSegments.add(SnippetTemplateSegment.textSegment(formatText(text)));
    }

    @Override
    public void tabstop(int index) {
        if (placeholderParts != null) {
            // A tab stop in a placeholder has no stop of its own: it repeats the default of its index in the text of
            // the placeholder. As in VS Code, $0 repeats none
            if (index != 0) {
                appendToPlaceholder(new TabStop(index));
            }
            return;
        }
        if (index == 0) {
            templateSegments.add(SnippetTemplateSegment.endVariable());
        } else {
            templateSegments.add(SnippetTemplateSegment.tabStop(index));
        }
    }

    @Override
    public void choice(int index, List<String> choices) {
        defaults.putChoice(index, choices);
        if (placeholderParts != null) {
            // A choice in a placeholder gives its first value to the text of the placeholder
            appendToPlaceholder(new Text(choices.isEmpty() ? "" : choices.get(0)));
            return;
        }
        templateSegments.add(SnippetTemplateSegment.tabStop(index));
    }

    @Override
    public void choice(String name, List<String> choices) {
        if (placeholderParts != null) {
            // A choice in a placeholder gives its first value to the text of the placeholder
            appendToPlaceholder(new Text(name));
            return;
        }
        templateSegments.add(SnippetTemplateSegment.variable(new ConstantNode(name).withLookupStrings(choices), true));
    }

    @Override
    public void startPlaceholder(int index, String name, int level) {
        // The text of a placeholder can start with a variable, such as $TM_FILENAME_BASE, which is resolved before other
        // text joins it, as in ${1:${TM_FILENAME_BASE} suffix}: the whole text, as a variable, is not
        String value = super.resolveVariable(name);
        Text text = new Text(value != null ? value : name);
        if (placeholderParts != null) {
            // A nested placeholder gives its text to the text of the placeholder that contains it
            nestedPlaceholders.push(new NestedPlaceholder(index, placeholderParts.size()));
            appendToPlaceholder(text);
            return;
        }
        placeholderIndex = index;
        placeholderParts = new ArrayList<>();
        appendToPlaceholder(text);
    }

    /**
     * Appends the given part to the text of the placeholder being read. An empty text adds none, so a placeholder
     * without a text, as ${1:}, has no parts.
     */
    private void appendToPlaceholder(@NotNull Part part) {
        if (placeholderParts != null && !(part instanceof Text text && text.value().isEmpty())) {
            placeholderParts.add(part);
        }
    }

    @Override
    public void endPlaceholder(int level) {
        if (placeholderParts == null) {
            return;
        }
        if (level != 1) {
            // The default of a nested placeholder is its part of the text of the placeholder that contains it
            NestedPlaceholder nested = nestedPlaceholders.poll();
            if (nested != null) {
                defaults.putPlaceholder(nested.index(), placeholderParts.subList(nested.start(), placeholderParts.size()));
            }
            return;
        }
        List<Part> parts = placeholderParts;
        placeholderParts = null;
        nestedPlaceholders.clear();
        if (parts.isEmpty()) {
            // As ${1:}, a tab stop
            tabstop(placeholderIndex);
        } else {
            defaults.putPlaceholder(placeholderIndex, parts);
            templateSegments.add(SnippetTemplateSegment.tabStop(placeholderIndex));
        }
    }

    @Override
    public void variable(String name) {
        if (placeholderParts != null) {
            // A variable in a placeholder gives its value, or its name, to the text of the placeholder
            String value = super.resolveVariable(name);
            appendToPlaceholder(new Text(value != null ? value : name));
            return;
        }
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

    /**
     * A nested placeholder being read, whose text starts at the given part of the text of its outermost placeholder.
     */
    private record NestedPlaceholder(int index, int start) {
    }
}

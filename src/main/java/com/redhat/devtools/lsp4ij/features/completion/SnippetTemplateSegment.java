/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is a simple wrapper for a {@link com.intellij.codeInsight.template.Template} segment that allows deferred
 * creation of the corresponding template with all segment information retained.
 */
class SnippetTemplateSegment {
    private String textSegment;
    private String variableSegment;
    private Expression variable;
    private boolean alwaysStopAt;
    private String variableName;
    private Expression defaultValueExpression;
    private boolean skipOnStart;
    private boolean endVariable;

    @NotNull
    static SnippetTemplateSegment textSegment(@NotNull String text) {
        SnippetTemplateSegment snippetTemplateSegment = new SnippetTemplateSegment();
        snippetTemplateSegment.textSegment = text;
        return snippetTemplateSegment;
    }

    @NotNull
    static SnippetTemplateSegment variableSegment(@NotNull String text) {
        SnippetTemplateSegment snippetTemplateSegment = new SnippetTemplateSegment();
        snippetTemplateSegment.variableSegment = text;
        return snippetTemplateSegment;
    }

    @NotNull
    static SnippetTemplateSegment variable(@NotNull Expression variable, boolean alwaysStopAt) {
        SnippetTemplateSegment snippetTemplateSegment = new SnippetTemplateSegment();
        snippetTemplateSegment.variable = variable;
        snippetTemplateSegment.alwaysStopAt = alwaysStopAt;
        return snippetTemplateSegment;
    }

    @SuppressWarnings("SameParameterValue")
    @NotNull
    static SnippetTemplateSegment namedVariable(@NotNull String variableName,
                                                @NotNull Expression variable,
                                                @Nullable Expression defaultValueExpression,
                                                boolean alwaysStopAt,
                                                boolean skipOnStart) {
        SnippetTemplateSegment snippetTemplateSegment = new SnippetTemplateSegment();
        snippetTemplateSegment.variableName = variableName;
        snippetTemplateSegment.variable = variable;
        snippetTemplateSegment.defaultValueExpression = defaultValueExpression;
        snippetTemplateSegment.alwaysStopAt = alwaysStopAt;
        snippetTemplateSegment.skipOnStart = skipOnStart;
        return snippetTemplateSegment;
    }

    @NotNull
    static SnippetTemplateSegment endVariable() {
        SnippetTemplateSegment snippetTemplateSegment = new SnippetTemplateSegment();
        snippetTemplateSegment.endVariable = true;
        return snippetTemplateSegment;
    }

    boolean isTextSegment() {
        return textSegment != null;
    }

    @Nullable
    String getTextSegment() {
        return textSegment;
    }

    boolean isVariableSegment() {
        return variableSegment != null;
    }

    @Nullable
    String getVariableSegment() {
        return variableSegment;
    }

    boolean isVariable() {
        return (variable != null) && (variableName == null);
    }

    @Nullable
    Expression getVariable() {
        return variable;
    }

    boolean isAlwaysStopAt() {
        return alwaysStopAt;
    }

    boolean isNamedVariable() {
        return (variable != null) && (variableName != null);
    }

    @Nullable
    String getVariableName() {
        return variableName;
    }

    @Nullable
    Expression getDefaultValueExpression() {
        return defaultValueExpression;
    }

    boolean isSkipOnStart() {
        return skipOnStart;
    }

    boolean isEndVariable() {
        return endVariable;
    }
}

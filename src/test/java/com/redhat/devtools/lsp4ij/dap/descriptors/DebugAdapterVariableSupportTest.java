/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.descriptors;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.eclipse.lsp4j.debug.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * The values of DAP variables in the Variables view: a string is shown as the DAP server sends it, with its quotes and
 * escapes (#1992).
 */
public class DebugAdapterVariableSupportTest {

    @Test
    public void testAStringIsShownWithTheQuotesAndEscapesOfTheDAPServer() {
        // the strings of #1992, from a DAP server which sends a string in double quotes, with backslash escapes
        for (String value : List.of("\"test\"", "\"'test'\"", "\"''test''\"", "\"\"", "\"'\"", "\"\\\"test\\\"\"")) {
            assertEquals(value, List.of(asString(value)), render(variable(value, "string")));
        }
    }

    @Test
    public void testAStringInSingleQuotesKeepsThem() {
        // #812: vscode-js-debug puts a string in single quotes, and one with a single quote in double quotes
        assertEquals(List.of(asString("'name'")), render(variable("'name'", "string")));
        assertEquals(List.of(asString("\"'name'\"")), render(variable("\"'name'\"", "string")));
    }

    @Test
    public void testATypeStringInAnyCaseIsAString() {
        assertEquals(List.of(asString("\"text\"")), render(variable("\"text\"", "String")));
    }

    @Test
    public void testOtherValuesAreShownAsBefore() {
        assertEquals(List.of("number 42"), render(variable("42", "int")));
        assertEquals(List.of("keyword true"), render(variable("true", "boolean")));
        assertEquals(List.of("value {a: 1}"), render(variable("{a: 1}", "object")));
    }

    @Test
    public void testTheFormattedValueIsTheValueOfTheDAPServer() {
        assertEquals("\"\\\"test\\\"\"",
                new DebugAdapterVariableSupport().formatValue(variable("\"\\\"test\\\"\"", "string")));
    }

    /**
     * Returns how the renderer is asked to show the given value as a string: in the string color, with no quotes
     * around it. The renderer puts the value of renderStringValue in double quotes of its own.
     */
    private static @NotNull String asString(@NotNull String value) {
        return "value " + value + " in DEFAULT_STRING";
    }

    private static @NotNull Variable variable(@NotNull String value, @NotNull String type) {
        var variable = new Variable();
        variable.setName("v");
        variable.setValue(value);
        variable.setType(type);
        return variable;
    }

    /**
     * Returns what the Variables view is asked to render for the given variable, one entry per call of the renderer.
     */
    private static @NotNull List<String> render(@NotNull Variable variable) {
        List<String> rendered = new ArrayList<>();
        new DebugAdapterVariableSupport().getValuePresentation(variable).renderValue(new XValuePresentation.XValueTextRenderer() {
            @Override
            public void renderValue(@NotNull String value) {
                rendered.add("value " + value);
            }

            @Override
            public void renderStringValue(@NotNull String value) {
                rendered.add("quoted " + value);
            }

            @Override
            public void renderNumericValue(@NotNull String value) {
                rendered.add("number " + value);
            }

            @Override
            public void renderKeywordValue(@NotNull String value) {
                rendered.add("keyword " + value);
            }

            @Override
            public void renderValue(@NotNull String value, @NotNull TextAttributesKey key) {
                rendered.add("value " + value + " in " + key.getExternalName());
            }

            @Override
            public void renderStringValue(@NotNull String value, @Nullable String additionalSpecialCharsToHighlight,
                                          int maxLength) {
                rendered.add("quoted " + value);
            }

            @Override
            public void renderComment(@NotNull String comment) {
                rendered.add("comment " + comment);
            }

            @Override
            public void renderSpecialSymbol(@NotNull String symbol) {
                rendered.add("symbol " + symbol);
            }

            @Override
            public void renderError(@NotNull String error) {
                rendered.add("error " + error);
            }
        });
        return rendered;
    }
}

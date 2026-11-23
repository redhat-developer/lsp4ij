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
package com.redhat.devtools.lsp4ij.features.completion.snippet;

import com.redhat.devtools.lsp4ij.features.completion.snippet.handler.LspSnippetNode;
import org.junit.Test;

import static com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetAssert.*;

public class PlaceholderTest {

    @Test
    public void placeholder() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1:name}");
        assertEquals(actual, LspSnippetAssert.placeholder(1, "name", 1));
    }

    @Test
    public void placeholderWithoutName() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1:}");
        assertEquals(actual, LspSnippetAssert.tabstop(1));
    }

    @Test
    public void nestedPlaceholderEndsAtItsClosingBrace() {
        // The '}' of the outer placeholder is no text
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1:a ${2:b} c} d");
        assertEquals(actual, LspSnippetAssert.placeholder(1, "a ", 1),
                LspSnippetAssert.placeholder(2, "b", 2),
                LspSnippetAssert.text(" c"),
                LspSnippetAssert.text(" d"));
    }

    @Test
    public void nestedPlaceholderWithEscapedClosingBrace() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1:a ${2:b} \\} c} d");
        assertEquals(actual, LspSnippetAssert.placeholder(1, "a ", 1),
                LspSnippetAssert.placeholder(2, "b", 2),
                LspSnippetAssert.text(" } c"),
                LspSnippetAssert.text(" d"));
    }

    @Test
    public void placeholderThatStartsWithANestedPlaceholder() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1:${2:b} c} d");
        assertEquals(actual, LspSnippetAssert.placeholder(1, "", 1),
                LspSnippetAssert.placeholder(2, "b", 2),
                LspSnippetAssert.text(" c"),
                LspSnippetAssert.text(" d"));
    }

    @Test
    public void placeholderThatStartsWithATabstopOrAChoice() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1:$2 c}${3:${4|x,y|}} d");
        assertEquals(actual, LspSnippetAssert.placeholder(1, "", 1),
                LspSnippetAssert.tabstop(2),
                LspSnippetAssert.text(" c"),
                LspSnippetAssert.placeholder(3, "", 1),
                LspSnippetAssert.choice(4, "x", "y"),
                LspSnippetAssert.text(" d"));
    }

    @Test
    public void placeholderThatStartsWithAVariableKeepsItInItsText() {
        // The handlers resolve the variable of the text (#1313)
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1:$TM_FILENAME_BASE} x");
        assertEquals(actual, LspSnippetAssert.placeholder(1, "$TM_FILENAME_BASE", 1),
                LspSnippetAssert.text(" x"));
    }

    @Test
    public void placeholderThatStartsWithABracedVariableKeepsItInItsText() {
        // The '}' of the variable doesn't end the placeholder
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1:${TM_FILENAME_BASE}} x");
        assertEquals(actual, LspSnippetAssert.placeholder(1, "$TM_FILENAME_BASE", 1),
                LspSnippetAssert.text(" x"));
    }

    @Test
    public void placeholderThatStartsWithAnEscapedDollarHasItInItsText() {
        // An escaped $ starts a text, which the handlers don't resolve as a variable
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1:\\$TM_FILENAME_BASE} x");
        assertEquals(actual, LspSnippetAssert.placeholder(1, "", 1),
                LspSnippetAssert.text("$TM_FILENAME_BASE"),
                LspSnippetAssert.text(" x"));
    }

    @Test
    public void leadingVariableOfAPlaceholderEndsWithItsName() {
        LspSnippetNode[] unbraced = LspSnippetAssert.parse("${1:$TM_FILENAME_BASE suffix} x");
        assertEquals(unbraced, LspSnippetAssert.placeholder(1, "$TM_FILENAME_BASE", 1),
                LspSnippetAssert.text(" suffix"),
                LspSnippetAssert.text(" x"));
        LspSnippetNode[] braced = LspSnippetAssert.parse("${1:${TM_FILENAME_BASE} suffix} x");
        assertEquals(braced, LspSnippetAssert.placeholder(1, "$TM_FILENAME_BASE", 1),
                LspSnippetAssert.text(" suffix"),
                LspSnippetAssert.text(" x"));
    }

    @Test
    public void tabstopAndVariableInPlaceholder() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1:a $2 $name} b");
        assertEquals(actual, LspSnippetAssert.placeholder(1, "a ", 1),
                LspSnippetAssert.tabstop(2),
                LspSnippetAssert.text(" "),
                LspSnippetAssert.variable("name"),
                LspSnippetAssert.text(" b"));
    }

    @Test
    public void placeholderWithEscapeDollar() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("\\$${1:var}");
        assertEquals(actual, LspSnippetAssert.text("$"),
                LspSnippetAssert.placeholder(1, "var", 1));
    }
}

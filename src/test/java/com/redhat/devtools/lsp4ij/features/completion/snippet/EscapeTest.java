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
package com.redhat.devtools.lsp4ij.features.completion.snippet;

import com.redhat.devtools.lsp4ij.features.completion.snippet.handler.LspSnippetNode;
import org.junit.Test;

import static com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetAssert.assertEquals;

/**
 * Tests comes from https://github.com/hcengineering/lsp4ij/blob/6d264fd997152b387dbf9717db27cda9a49a5b79/src/test/java/com/redhat/devtools/lsp4ij/features/completion/snippet/EscapeTest.java#L8
 */
public class EscapeTest {

    @Test
    public void escapedFreestandingDollar() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("te\\$xt");
        assertEquals(actual, LspSnippetAssert.text("te$xt"));
    }

    @Test
    public void escapedTabstopDollar() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("a = \\$0");
        assertEquals(actual, LspSnippetAssert.text("a = $0"));
    }

    @Test
    public void escapedClosingCurlyBracket() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("func foo() {\\}");
        assertEquals(actual, LspSnippetAssert.text("func foo() {}"));
    }

    @Test
    public void escapedClosingCurlyBracketWithTabstop() {
        // Actual snippet from gopls
        LspSnippetNode[] actual = LspSnippetAssert.parse("func() (success bool) {$0\\}");
        assertEquals(actual, LspSnippetAssert.text("func() (success bool) {"), LspSnippetAssert.tabstop(0), LspSnippetAssert.text("}"));
    }

    @Test(expected = ParseException.class)
    public void escapedMismatchEscapedClosingCurlyBracket() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("func foo() {${0\\}");
    }

    @Test
    public void escapedSlash() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("te\\\\xt");
        assertEquals(actual, LspSnippetAssert.text("te\\xt"));
    }

    @Test
    public void escapedCommaInChoice() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1|one,two,thr\\,ee|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "thr,ee"));
        actual = LspSnippetAssert.parse("${1|one,two,\\,three|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", ",three"));
        actual = LspSnippetAssert.parse("${1|one,two,three\\,|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "three,"));
        actual = LspSnippetAssert.parse("${1|one,two,three\\,,four|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "three,", "four"));
    }

    @Test
    public void escapedBarInChoice() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1|one,two,thr\\|ee|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "thr|ee"));
        actual = LspSnippetAssert.parse("${1|one,two,\\|three|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "|three"));
        actual = LspSnippetAssert.parse("${1|one,two,three\\||}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "three|"));
        actual = LspSnippetAssert.parse("${1|one,two,three\\|,four|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "three|", "four"));
    }

    @Test
    public void escapedLine() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("fmt.Printf(\"s: %v\\n\", s)");
        assertEquals(actual, LspSnippetAssert.text("fmt.Printf(\"s: %v\\n\", s)"));
    }

}
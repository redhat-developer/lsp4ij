/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion.snippet;

import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetDefaults.Part;
import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetDefaults.TabStop;
import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetDefaults.Text;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The defaults that the tab stops of a snippet repeat (#2073).
 */
public class LspSnippetDefaultsTest {

    @Test
    public void theFirstPlaceholderOrChoiceWithAnIndexGivesItsDefault() {
        var defaults = new LspSnippetDefaults();
        defaults.putPlaceholder(1, List.of(new Text("a")));
        defaults.putPlaceholder(1, List.of(new Text("b")));
        defaults.putChoice(1, List.of("c", "d"));
        defaults.putChoice(2, List.of("one", "two"));
        defaults.putPlaceholder(2, List.of(new Text("three")));

        assertEquals("a", defaults.getText(1));
        assertNull(defaults.getChoices(1));
        assertEquals("one", defaults.getText(2));
        assertEquals(List.of("one", "two"), defaults.getChoices(2));
    }

    @Test
    public void aPlaceholderWithoutATextGivesNoDefault() {
        var defaults = new LspSnippetDefaults();
        defaults.putPlaceholder(1, List.of());
        defaults.putPlaceholder(1, List.of(new Text("a")));

        assertEquals("a", defaults.getText(1));
        assertEquals("", defaults.getText(2));
    }

    @Test
    public void aTabStopRepeatsAChainOfDefaults() {
        // ${1:$2} ${2:$3} ${3:z}
        var defaults = new LspSnippetDefaults();
        defaults.putPlaceholder(1, List.of(new TabStop(2)));
        defaults.putPlaceholder(2, List.of(new TabStop(3)));
        defaults.putPlaceholder(3, List.of(new Text("z")));

        assertEquals("z", defaults.getText(1));
        assertEquals("z z z z", defaults.getText(List.of(new TabStop(1), new Text(" "), new TabStop(2),
                new Text(" "), new TabStop(3), new Text(" "), new TabStop(1))));
    }

    @Test
    public void aDefaultThatRepeatsItselfRepeatsNothing() {
        // ${1:x$1$1}, and ${2:a$3} ${3:b$2}
        var defaults = new LspSnippetDefaults();
        defaults.putPlaceholder(1, List.of(new Text("x"), new TabStop(1), new TabStop(1)));
        defaults.putPlaceholder(2, List.of(new Text("a"), new TabStop(3)));
        defaults.putPlaceholder(3, List.of(new Text("b"), new TabStop(2)));

        assertEquals("x", defaults.getText(1));
        assertEquals("ab", defaults.getText(2));
    }

    @Test(timeout = 10_000)
    public void aDefaultIsResolvedOnce() {
        // ${1:$2$2} ${2:$3$3} ... ${60:$61$61}, without a default for 61: each tab stop repeats the same chain
        var defaults = new LspSnippetDefaults();
        for (int index = 1; index <= 60; index++) {
            defaults.putPlaceholder(index, List.of(new TabStop(index + 1), new TabStop(index + 1)));
        }

        assertEquals("", defaults.getText(1));
    }

    @Test(timeout = 10_000)
    public void theTextThatADefaultRepeatsStopsAtTheLimit() {
        // ${1:$2$2} ${2:$3$3} ... ${60:x}, whose default of 1 would have 2^59 characters
        var defaults = new LspSnippetDefaults();
        for (int index = 1; index < 60; index++) {
            defaults.putPlaceholder(index, List.of(new Text("<"), new TabStop(index + 1), new TabStop(index + 1)));
        }
        defaults.putPlaceholder(60, List.of(new Text("x")));

        String text = defaults.getText(1);
        assertEquals("<", text.substring(0, 1));
        assertEquals(1 + LspSnippetDefaults.MAX_REPEATED_LENGTH, text.length());
    }

    @Test
    public void theTextOfAPlaceholderAndOfTheSnippetHaveNoLimit() {
        String longText = "x".repeat(LspSnippetDefaults.MAX_REPEATED_LENGTH + 1);
        var defaults = new LspSnippetDefaults();
        defaults.putPlaceholder(1, List.of(new Text(longText)));

        assertEquals(longText, defaults.getText(1));
        List<Part> snippet = Collections.nCopies(3, new TabStop(1));
        assertEquals(longText.repeat(3), defaults.getText(snippet));
    }
}

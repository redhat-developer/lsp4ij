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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The defaults that the tab stops of a snippet repeat (#2073).
 */
public class LspSnippetDefaultsTest {

    @Test
    public void theFirstPlaceholderOrChoiceWithAnIndexGivesItsDefault() {
        // ${1:a} ${1:b} ${1|c,d|} ${2|one,two|} ${2:three}
        var defaults = new LspSnippetDefaults();
        placeholder(defaults, 1, new Text("a"));
        assertFalse(defaults.open(1));
        assertFalse(defaults.choice(1, List.of("c", "d")));
        assertTrue(defaults.choice(2, List.of("one", "two")));
        assertFalse(defaults.open(2));

        assertEquals("a", defaults.getText(1));
        assertNull(defaults.getChoices(1));
        assertEquals("one", defaults.getText(2));
        assertEquals(List.of("one", "two"), defaults.getChoices(2));
    }

    @Test
    public void anOuterPlaceholderGivesTheDefaultBeforeOneNestedInIt() {
        // ${1:a${1:b}}: the outer placeholder opens first, and the nested one, which closes first, repeats its default
        var defaults = new LspSnippetDefaults();
        assertTrue(defaults.open(1));
        assertFalse(defaults.open(1));
        assertTrue(defaults.close(1, List.of(new Text("a"), new TabStop(1))));

        assertEquals("a", defaults.getText(1));
    }

    @Test
    public void aPlaceholderWithoutATextGivesNoDefault() {
        // ${1:} ${1:a}
        var defaults = new LspSnippetDefaults();
        assertTrue(defaults.open(1));
        assertFalse(defaults.close(1, List.of()));
        placeholder(defaults, 1, new Text("a"));

        assertEquals("a", defaults.getText(1));
        assertEquals("", defaults.getText(2));
    }

    @Test
    public void aChoiceAfterAPlaceholderWithItsIndexGivesNoDefault() {
        // ${1:a ${1|x,y|}} ${1|z|}: neither a choice in the placeholder that holds the default, nor one after it
        var defaults = new LspSnippetDefaults();
        assertTrue(defaults.open(1));
        assertFalse(defaults.choice(1, List.of("x", "y")));
        assertTrue(defaults.close(1, List.of(new Text("a "), new TabStop(1))));
        assertFalse(defaults.choice(1, List.of("z")));

        assertEquals("a ", defaults.getText(1));
        assertNull(defaults.getChoices(1));
    }

    @Test
    public void aChoiceGivesTheDefaultBeforeAPlaceholderWithItsIndex() {
        // ${1|x,y|} ${1:a}
        var defaults = new LspSnippetDefaults();
        assertTrue(defaults.choice(1, List.of("x", "y")));
        assertFalse(defaults.open(1));

        assertEquals("x", defaults.getText(1));
        assertEquals(List.of("x", "y"), defaults.getChoices(1));
    }

    @Test
    public void aTabStopRepeatsAChainOfDefaults() {
        // ${1:$2} ${2:$3} ${3:z}
        var defaults = new LspSnippetDefaults();
        placeholder(defaults, 1, new TabStop(2));
        placeholder(defaults, 2, new TabStop(3));
        placeholder(defaults, 3, new Text("z"));

        assertEquals("z", defaults.getText(1));
        assertEquals("z z z z", defaults.getText(List.of(new TabStop(1), new Text(" "), new TabStop(2),
                new Text(" "), new TabStop(3), new Text(" "), new TabStop(1))));
    }

    @Test
    public void aDefaultThatRepeatsItselfRepeatsNothing() {
        // ${1:x$1$1}, and ${2:a$3} ${3:b$2}
        var defaults = new LspSnippetDefaults();
        placeholder(defaults, 1, new Text("x"), new TabStop(1), new TabStop(1));
        placeholder(defaults, 2, new Text("a"), new TabStop(3));
        placeholder(defaults, 3, new Text("b"), new TabStop(2));

        assertEquals("x", defaults.getText(1));
        assertEquals("ab", defaults.getText(2));
    }

    @Test
    public void eachDefaultInACycleIsResolvedFromItself() {
        // ${1:a$2} ${2:b$1}: each default repeats the other, which repeats nothing at the tab stop of the first
        LspSnippetDefaults oneFirst = mutualCycle();
        assertEquals("ab", oneFirst.getText(1));
        assertEquals("ba", oneFirst.getText(2));
        assertEquals("ab", oneFirst.getText(1));

        LspSnippetDefaults twoFirst = mutualCycle();
        assertEquals("ba", twoFirst.getText(2));
        assertEquals("ab", twoFirst.getText(1));
        assertEquals("ba", twoFirst.getText(2));
    }

    @Test
    public void eachDefaultInACycleOfThreeIsResolvedFromItself() {
        // ${1:a$2} ${2:b$3} ${3:c$1}
        var defaults = new LspSnippetDefaults();
        placeholder(defaults, 1, new Text("a"), new TabStop(2));
        placeholder(defaults, 2, new Text("b"), new TabStop(3));
        placeholder(defaults, 3, new Text("c"), new TabStop(1));

        assertEquals("abc", defaults.getText(1));
        assertEquals("bca", defaults.getText(2));
        assertEquals("cab", defaults.getText(3));
        assertEquals("abc", defaults.getText(1));
        // In the text of the snippet too
        assertEquals("abc bca cab", defaults.getText(List.of(new TabStop(1), new Text(" "), new TabStop(2),
                new Text(" "), new TabStop(3))));
    }

    @Test(timeout = 10_000)
    public void aCycleOfDefaultsThatRepeatEachOtherManyTimesEnds() {
        // ${1:$2$2$1} ${2:$3$3$1} ... ${30:$31$31$1}, without a default for 31: each default is in a cycle with 1, so
        // it isn't kept, and each tab stop resolves it again: about 2^30 times without a bound. Their text stays
        // empty, so its limit doesn't bound them
        var defaults = new LspSnippetDefaults();
        for (int index = 1; index <= 30; index++) {
            placeholder(defaults, index, new TabStop(index + 1), new TabStop(index + 1), new TabStop(1));
        }

        for (int index = 1; index <= 30; index++) {
            assertEquals("", defaults.getText(index));
        }
    }

    @Test(timeout = 10_000)
    public void anIndexHasTheSameTextOnceTheBoundIsReached() {
        // ${1:a$2} ${2:b$1}, then ${3:$4$4$3} ... ${32:$33$33$3}, which reach the bound, then $1 $2
        LspSnippetDefaults defaults = cycleThenBound();
        assertEquals("ab", defaults.getText(1));
        assertEquals("ba", defaults.getText(2));
        defaults.getText(3);

        assertEquals("ab", defaults.getText(1));
        assertEquals("ba", defaults.getText(2));
    }

    @Test(timeout = 10_000)
    public void aTabStopOfTheSnippetHasTheSameTextOnceTheBoundIsReached() {
        // ${1:a$2} ${2:b$1} ${3:$4$4$3} ... ${32:$33$33$3} $1 $2
        LspSnippetDefaults defaults = cycleThenBound();
        List<Part> snippet = new ArrayList<>(List.of(new TabStop(1), new Text(" "), new TabStop(2)));
        for (int index = 3; index <= 32; index++) {
            snippet.add(new Text(" "));
            snippet.add(new TabStop(index));
        }
        snippet.addAll(List.of(new Text(" "), new TabStop(1), new Text(" "), new TabStop(2)));

        assertEquals("ab ba" + " ".repeat(30) + " ab ba", defaults.getText(snippet));
    }

    @Test(timeout = 10_000)
    public void aDefaultIsResolvedOnce() {
        // ${1:$2$2} ${2:$3$3} ... ${60:$61$61}, without a default for 61: each tab stop repeats the same chain
        var defaults = new LspSnippetDefaults();
        for (int index = 1; index <= 60; index++) {
            placeholder(defaults, index, new TabStop(index + 1), new TabStop(index + 1));
        }

        assertEquals("", defaults.getText(1));
    }

    @Test(timeout = 10_000)
    public void theTextThatADefaultRepeatsStopsAtTheLimit() {
        // ${1:$2$2} ${2:$3$3} ... ${60:x}, whose default of 1 would have 2^59 characters
        var defaults = new LspSnippetDefaults();
        for (int index = 1; index < 60; index++) {
            placeholder(defaults, index, new Text("<"), new TabStop(index + 1), new TabStop(index + 1));
        }
        placeholder(defaults, 60, new Text("x"));

        String text = defaults.getText(1);
        assertEquals("<", text.substring(0, 1));
        assertEquals(1 + LspSnippetDefaults.MAX_REPEATED_LENGTH, text.length());
    }

    @Test
    public void theTextOfAPlaceholderAndOfTheSnippetHaveNoLimit() {
        String longText = "x".repeat(LspSnippetDefaults.MAX_REPEATED_LENGTH + 1);
        var defaults = new LspSnippetDefaults();
        placeholder(defaults, 1, new Text(longText));

        assertEquals(longText, defaults.getText(1));
        List<Part> snippet = Collections.nCopies(3, new TabStop(1));
        assertEquals(longText.repeat(3), defaults.getText(snippet));
    }

    /**
     * Returns the defaults of {@code ${1:a$2} ${2:b$1}}, then {@code ${3:$4$4$3} ... ${32:$33$33$3}}, without a default
     * for 33: resolving 3 resolves the defaults in its cycle again until the bound.
     */
    private static LspSnippetDefaults cycleThenBound() {
        LspSnippetDefaults defaults = mutualCycle();
        for (int index = 3; index <= 32; index++) {
            placeholder(defaults, index, new TabStop(index + 1), new TabStop(index + 1), new TabStop(3));
        }
        return defaults;
    }

    private static LspSnippetDefaults mutualCycle() {
        var defaults = new LspSnippetDefaults();
        placeholder(defaults, 1, new Text("a"), new TabStop(2));
        placeholder(defaults, 2, new Text("b"), new TabStop(1));
        return defaults;
    }

    /**
     * Reads a placeholder with the given index and text, the first with its index, outside any other.
     */
    private static void placeholder(LspSnippetDefaults defaults, int index, Part... parts) {
        assertTrue(defaults.open(index));
        assertTrue(defaults.close(index, List.of(parts)));
    }
}

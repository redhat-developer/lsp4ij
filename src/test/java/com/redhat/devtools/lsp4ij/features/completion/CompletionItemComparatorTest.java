/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.NameUtil.MatchingCaseSensitivity;
import org.eclipse.lsp4j.CompletionItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompletionItemComparatorTest {

    private static final LSPCompletionItemComparator caseInsensitiveComparator = new LSPCompletionItemComparator(null, null, false);
    private static final LSPCompletionItemComparator caseSensitiveComparator = new LSPCompletionItemComparator(null, null, true);

    // Simple tests

    private static final CompletionItem one = newItem("one", "1");
    private static final CompletionItem nil = newItem("", null);
    private static final CompletionItem two = newItem("two", "2");
    private static final CompletionItem three = newItem("three", "3");
    private static final CompletionItem firstthree = newItem("first three", "3");

    @Test
    public void orderList() {
        var items = new ArrayList<>(List.of(three, one, nil, two, firstthree));
        var expected = List.of(nil, one, two, firstthree, three);
        items.sort(caseInsensitiveComparator);
        assertEquals(expected, items);
    }

    @Test
    public void compareSortText() {
        assertTrue(caseInsensitiveComparator.compare(one, two) < 0);
        assertTrue(caseInsensitiveComparator.compare(two, one) > 0);
    }

    @Test
    public void compareLabels() {
        assertTrue(caseInsensitiveComparator.compare(newItem("one", null), newItem("two", null)) < 0);
        assertTrue(caseInsensitiveComparator.compare(newItem("two", null), newItem("three", null)) > 0);
    }

    @Test
    public void compareNulls() {
        assertEquals(1, caseInsensitiveComparator.compare(one, null));
        assertEquals(0, caseInsensitiveComparator.compare(nil, nil));
        assertEquals(1, caseInsensitiveComparator.compare(nil, null));
        assertEquals(0, caseInsensitiveComparator.compare(null, null));
    }

    // Case-sensitivity tests

    private static final CompletionItem lowerCaseTestItem = newItem("name", null);
    private static final CompletionItem upperCaseTestItem = newItem("NAME", null);
    private static final CompletionItem capitalizedTestItem = newItem("Name", null);
    private static final List<CompletionItem> caseSensitivityTestItems = List.of(
            lowerCaseTestItem,
            upperCaseTestItem,
            capitalizedTestItem
    );

    @Test
    public void compareLabelsCaseInsensitive() {
        List<CompletionItem> items = new ArrayList<>(caseSensitivityTestItems);
        items.sort(caseInsensitiveComparator);
        // Should be no change in order
        assertSortOrder(items, caseSensitivityTestItems.toArray(new CompletionItem[0]));
    }

    @Test
    public void compareLabelsCaseSensitive() {
        List<CompletionItem> items = new ArrayList<>(caseSensitivityTestItems);
        items.sort(caseSensitiveComparator);
        assertSortOrder(items, upperCaseTestItem, capitalizedTestItem, lowerCaseTestItem);
    }

    // Prefix tests

    private static final CompletionItem fooItem = newItem("foo", null);
    private static final CompletionItem barItem = newItem("bar", null);
    private static final CompletionItem bazItem = newItem("Baz", null);
    private static final CompletionItem feItem = newItem("Fe", null);
    private static final CompletionItem fiItem = newItem("FI", null);
    private static final CompletionItem foItem = newItem("fo", null);
    private static final CompletionItem fumItem = newItem("Fum", null);
    private static final List<CompletionItem> items = List.of(fooItem, barItem, bazItem, feItem, fiItem, foItem, fumItem);

    @Test
    public void compareLabelsAgainstPrefixCaseInsensitive() {
        boolean caseSensitive = false;

        // First with a single lower-cased letter
        List<CompletionItem> mutableItems = new ArrayList<>(items);
        PrefixMatcher prefixMatcher = createPrefixMatcher("f", caseSensitive);
        LSPCompletionItemComparator comparator = new LSPCompletionItemComparator(prefixMatcher, null, caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, foItem, fooItem, feItem, fiItem, fumItem, barItem, bazItem);

        // Then with a single upper-cased letter which should yield the exact same results
        mutableItems = new ArrayList<>(items);
        prefixMatcher = createPrefixMatcher("F", caseSensitive);
        comparator = new LSPCompletionItemComparator(prefixMatcher, null, caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, feItem, fiItem, fumItem, foItem, fooItem, barItem, bazItem);

        // Then with a second letter with mixed case
        mutableItems = new ArrayList<>(items);
        prefixMatcher = createPrefixMatcher("fO", caseSensitive);
        comparator = new LSPCompletionItemComparator(prefixMatcher, null, caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, foItem, fooItem, barItem, bazItem, feItem, fiItem, fumItem);
    }

    @Test
    public void compareLabelsAgainstPrefixCaseSensitive() {
        boolean caseSensitive = true;

        // First with a single lower-cased letter
        List<CompletionItem> mutableItems = new ArrayList<>(items);
        PrefixMatcher prefixMatcher = createPrefixMatcher("f", caseSensitive);
        LSPCompletionItemComparator comparator = new LSPCompletionItemComparator(prefixMatcher, null, caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, foItem, fooItem, bazItem, fiItem, feItem, fumItem, barItem);

        // Then with a single upper-cased letter
        mutableItems = new ArrayList<>(items);
        prefixMatcher = createPrefixMatcher("F", caseSensitive);
        comparator = new LSPCompletionItemComparator(prefixMatcher, null, caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, fiItem, feItem, fumItem, bazItem, barItem, foItem, fooItem);

        // Then with a second letter with mixed case
        mutableItems = new ArrayList<>(items);
        prefixMatcher = createPrefixMatcher("Fe", caseSensitive);
        comparator = new LSPCompletionItemComparator(prefixMatcher, null, caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, feItem, bazItem, fiItem, fumItem, barItem, foItem, fooItem);
    }

    @Test
    public void compareLabelsAgainstComplexPrefixCaseSensitive() {
        boolean caseSensitive = true;

        CompletionItem toLocaleLowerCaseItem = newItem("toLocaleLowerCase", null);
        CompletionItem toLocaleUpperCaseItem = newItem("toLocaleUpperCase", null);
        CompletionItem toLowerCaseItem = newItem("toLowerCase", null);
        CompletionItem toStringItem = newItem("toString", null);
        CompletionItem toUpperCaseItem = newItem("toUpperCase", null);
        List<CompletionItem> items = List.of(toLocaleLowerCaseItem, toLocaleUpperCaseItem, toLowerCaseItem, toStringItem, toUpperCaseItem);

        // First with a prefix of "to"
        List<CompletionItem> mutableItems = new ArrayList<>(items);
        PrefixMatcher prefixMatcher = createPrefixMatcher("to", caseSensitive);
        LSPCompletionItemComparator comparator = new LSPCompletionItemComparator(prefixMatcher, null, caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, toLocaleLowerCaseItem, toLocaleUpperCaseItem, toLowerCaseItem, toStringItem, toUpperCaseItem);

        // Then with a prefix of "toU"
        mutableItems = new ArrayList<>(items);
        prefixMatcher = createPrefixMatcher("toU", caseSensitive);
        comparator = new LSPCompletionItemComparator(prefixMatcher, null, caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, toUpperCaseItem, toLocaleUpperCaseItem, toLocaleLowerCaseItem, toLowerCaseItem, toStringItem);

        // Then with a prefix of "toUC"
        mutableItems = new ArrayList<>(items);
        prefixMatcher = createPrefixMatcher("toUC", caseSensitive);
        comparator = new LSPCompletionItemComparator(prefixMatcher, null, caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, toUpperCaseItem, toLocaleUpperCaseItem, toLocaleLowerCaseItem, toLowerCaseItem, toStringItem);
    }

    // Current word tests

    @Test
    public void compareLabelsAgainstCurrentWordCaseInsensitive() {
        boolean caseSensitive = false;

        List<CompletionItem> mutableItems = new ArrayList<>(items);
        // Use a different case and confirm that it still works properly
        LSPCompletionItemComparator comparator = new LSPCompletionItemComparator(null, bazItem.getLabel().toLowerCase(), caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, bazItem, barItem, feItem, fiItem, foItem, fooItem, fumItem);
    }

    @Test
    public void compareLabelsAgainstCurrentWordCaseSensitive() {
        boolean caseSensitive = true;

        // First confirm that it matches with the same case
        List<CompletionItem> mutableItems = new ArrayList<>(items);
        LSPCompletionItemComparator comparator = new LSPCompletionItemComparator(null, fumItem.getLabel(), caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, fumItem, bazItem, fiItem, feItem, barItem, foItem, fooItem);

        // Next confirm that it doesn't match with a different case
        mutableItems = new ArrayList<>(items);
        comparator = new LSPCompletionItemComparator(null, fumItem.getLabel().toLowerCase(), caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, bazItem, fiItem, feItem, fumItem, barItem, foItem, fooItem);
    }

    // Prefix and current word tests

    @Test
    public void compareLabelsAgainstPrefixAndCurrentWordCaseInsensitive() {
        boolean caseSensitive = false;

        List<CompletionItem> mutableItems = new ArrayList<>(items);
        PrefixMatcher prefixMatcher = createPrefixMatcher(foItem.getLabel().toUpperCase(), caseSensitive);
        LSPCompletionItemComparator comparator = new LSPCompletionItemComparator(prefixMatcher, fooItem.getLabel().toUpperCase(), caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, fooItem, foItem, barItem, bazItem, feItem, fiItem, fumItem);
    }

    @Test
    public void compareLabelsAgainstPrefixAndCurrentWordCaseSensitive() {
        boolean caseSensitive = true;

        List<CompletionItem> mutableItems = new ArrayList<>(items);
        PrefixMatcher prefixMatcher = createPrefixMatcher(foItem.getLabel(), caseSensitive);
        LSPCompletionItemComparator comparator = new LSPCompletionItemComparator(prefixMatcher, fooItem.getLabel(), caseSensitive);
        mutableItems.sort(comparator);
        assertSortOrder(mutableItems, fooItem, foItem, bazItem, fiItem, feItem, fumItem, barItem);
    }

    // Test utilities

    private static CompletionItem newItem(@Nullable String label, @Nullable String sortText) {
        CompletionItem item = new CompletionItem(label);
        item.setSortText(sortText);
        return item;
    }

    private static void assertSortOrder(@NotNull List<CompletionItem> actualItems, @NotNull CompletionItem... expectedItems) {
        assertEquals(expectedItems.length, actualItems.size(), "The two lists have different lengths.");
        for (int i = 0; i < expectedItems.length; i++) {
            assertEquals(expectedItems[i], actualItems.get(i), "The items at index " + i + " are different.");
        }
    }

    @NotNull
    private static PrefixMatcher createPrefixMatcher(@NotNull String prefix, boolean caseSensitive) {
        // NOTE: This allows us to avoid a dependency on the application while running these tests
        MinusculeMatcher minusculeMatcher = NameUtil
                .buildMatcher(prefix)
                .withCaseSensitivity(caseSensitive ? MatchingCaseSensitivity.FIRST_LETTER : MatchingCaseSensitivity.NONE)
                .build();
        return new PrefixMatcher(prefix) {
            @Override
            public boolean prefixMatches(@NotNull String name) {
                return minusculeMatcher.isStartMatch(name);
            }

            @Override
            public int matchingDegree(String string) {
                return minusculeMatcher.matchingDegree(string);
            }

            @Override
            @NotNull
            public PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
                fail("This matcher should not be cloned.");
                // Have to return something for the compiler
                return this;
            }
        };
    }
}
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

import org.eclipse.lsp4j.CompletionItem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompletionItemComparatorTest {
    private final CompletionItemComparator comparator = new CompletionItemComparator();

    private final CompletionItem one = newItem("one", "1");
    private final CompletionItem nil = newItem("", null);
    private final CompletionItem two = newItem("two", "2");
    private final CompletionItem three = newItem("three", "3");
    private final CompletionItem firstthree = newItem("first three", "3");

    @Test
    public void orderList() {
        var items = new ArrayList<>(List.of(three, one, nil, two, firstthree));
        var expected = List.of(nil, one, two, firstthree, three);
        items.sort(comparator);
        assertEquals(expected, items);
    }

    @Test
    public void compareSortText() {
        assertTrue(comparator.compare(one, two) < 0);
        assertTrue(comparator.compare(two, one) > 0);
    }

    @Test
    public void compareLabels() {
        assertTrue(comparator.compare(newItem("one", null), newItem("two", null)) < 0);
        assertTrue(comparator.compare(newItem("two", null), newItem("three", null)) > 0);
    }

    @Test
    public void compareNulls() {
        assertEquals(1,comparator.compare(one, null));
        assertEquals(0,comparator.compare(nil, nil));
        assertEquals(1,comparator.compare(nil, null));
        assertEquals(0,comparator.compare(null, null));
    }

    private CompletionItem newItem(String label, String sortText) {
        CompletionItem item = new CompletionItem(label);
        item.setSortText(sortText);
        return item;
    }

}
/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

/**
 * A language server can preselect a completion item ('CompletionItem.preselect'): the lookup selects it, and keeps
 * the order of the items.
 */
public class CompletionPreselectTest extends LSPCompletionFixtureTestCase {

    public CompletionPreselectTest() {
        super("*.ts");
    }

    public void testThePreselectedItemIsSelectedAndTheOrderIsKept() {
        assertCompletion("test.ts", "f<caret>", """
                [
                  {"label": "fa", "sortText": "1"},
                  {"label": "fb", "sortText": "2"},
                  {"label": "fc", "sortText": "3", "preselect": true}
                ]""", "fa", "fb", "fc");

        assertEquals(List.of("fa", "fb", "fc"), lookupStrings());
        assertEquals("fc", myFixture.getLookup().getCurrentItem().getLookupString());
    }

    public void testTheFirstItemIsSelectedWhenNoItemIsPreselected() {
        assertCompletion("test.ts", "f<caret>", """
                [
                  {"label": "fa", "sortText": "1"},
                  {"label": "fb", "sortText": "2"},
                  {"label": "fc", "sortText": "3"}
                ]""", "fa", "fb", "fc");

        assertEquals(List.of("fa", "fb", "fc"), lookupStrings());
        assertEquals("fa", myFixture.getLookup().getCurrentItem().getLookupString());
    }

    public void testAnItemIsStillSelectedWhenTheTypedTextFiltersOutThePreselectedItem() {
        assertCompletion("test.ts", "f<caret>", """
                [
                  {"label": "fa1", "sortText": "1"},
                  {"label": "fa2", "sortText": "2"},
                  {"label": "fb1", "sortText": "3", "preselect": true}
                ]""", "fa1", "fa2", "fb1");
        assertEquals("fb1", myFixture.getLookup().getCurrentItem().getLookupString());

        // 'fb1' no longer matches
        myFixture.type("a");

        assertNotNull("the lookup is still shown", myFixture.getLookup());
        assertEquals(List.of("fa1", "fa2"), lookupStrings());
        assertEquals("fa1", myFixture.getLookup().getCurrentItem().getLookupString());
    }

    public void testAnExactMatchOfTheTypedTextIsSelectedOverThePreselectedItem() {
        assertCompletion("test.ts", "Zebra<caret>", """
                [
                  {"label": "Zebra", "sortText": "1"},
                  {"label": "ZebraFish", "sortText": "2", "preselect": true}
                ]""", "Zebra", "ZebraFish");

        assertEquals("Zebra", myFixture.getLookup().getCurrentItem().getLookupString());
    }

    public void testAnItemOfAnotherCompletionContributorIsNotSkipped() {
        var skipper = new LSPCompletionPreselectSkipper();
        LookupElement other = LookupElementBuilder.create("other");

        assertFalse(skipper.skipElement(other, null));
    }

    private @NotNull List<String> lookupStrings() {
        return Stream.of(myFixture.getLookupElements())
                .map(LookupElement::getLookupString)
                .toList();
    }
}

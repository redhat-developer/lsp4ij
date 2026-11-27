/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionContributorEP;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

/**
 * The item that the language server preselects, with the item of another completion contributor below the LSP items:
 * the other contributor's item is never selected over an LSP item.
 */
public class CompletionPreselectOtherContributorTest extends LSPCompletionFixtureTestCase {

    public CompletionPreselectOtherContributorTest() {
        super("*.ts");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        var extension = new CompletionContributorEP("any", OtherContributor.class.getName(),
                new DefaultPluginDescriptor("otherCompletionContributor"));
        CompletionContributor.EP.getPoint().registerExtension(extension, getTestRootDisposable());
    }

    public void testThePreselectedItemIsSelected() {
        assertCompletion("test.ts", "f<caret>", """
                [
                  {"label": "fa1", "sortText": "1"},
                  {"label": "fa2", "sortText": "2"},
                  {"label": "fb1", "sortText": "3", "preselect": true}
                ]""", "fa1", "fa2", "fb1", "fa_other");

        assertEquals(List.of("fa1", "fa2", "fb1", "fa_other"), lookupStrings());
        assertEquals("fb1", currentItem());
    }

    public void testTheFirstLspItemIsSelectedWhenTheTypedTextFiltersOutThePreselectedItem() {
        assertCompletion("test.ts", "f<caret>", """
                [
                  {"label": "fa1", "sortText": "1"},
                  {"label": "fa2", "sortText": "2"},
                  {"label": "fb1", "sortText": "3", "preselect": true}
                ]""", "fa1", "fa2", "fb1", "fa_other");
        assertEquals("fb1", currentItem());

        // 'fb1' no longer matches, 'fa_other' still does
        myFixture.type("a");

        assertEquals(List.of("fa1", "fa2", "fa_other"), lookupStrings());
        assertEquals("the first LSP item, as when no item is preselected", "fa1", currentItem());
    }

    public void testTheFirstLspItemIsSelectedWhenNoItemIsPreselected() {
        assertCompletion("test.ts", "f<caret>", """
                [
                  {"label": "fa1", "sortText": "1"},
                  {"label": "fa2", "sortText": "2"},
                  {"label": "fb1", "sortText": "3"}
                ]""", "fa1", "fa2", "fb1", "fa_other");
        myFixture.type("a");

        assertEquals(List.of("fa1", "fa2", "fa_other"), lookupStrings());
        assertEquals("fa1", currentItem());
    }

    private @NotNull String currentItem() {
        assertNotNull("the lookup is shown", myFixture.getLookup());
        return myFixture.getLookup().getCurrentItem().getLookupString();
    }

    private @NotNull List<String> lookupStrings() {
        return Stream.of(myFixture.getLookupElements())
                .map(LookupElement::getLookupString)
                .toList();
    }

    /**
     * Another completion contributor, whose item comes below those of the language server.
     */
    public static final class OtherContributor extends CompletionContributor {
        @Override
        public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
            result.addElement(LookupElementBuilder.create("fa_other"));
        }
    }
}

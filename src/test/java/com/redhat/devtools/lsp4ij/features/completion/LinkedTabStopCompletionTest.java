/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InsertTextFormat;

import java.util.List;

/**
 * The tab stops and placeholders of a completion snippet that have the same index are linked: they have the default of
 * the first placeholder or choice with that index, and typing in one updates the others. Those with different indices
 * aren't linked, even if they have the same text (#2073).
 */
public class LinkedTabStopCompletionTest extends LSPCompletionFixtureTestCase {

    public LinkedTabStopCompletionTest() {
        super("*.snippet-test");
    }

    public void testATabStopAfterItsPlaceholderMirrorsIt() {
        complete("${1:name} = $1$0");

        assertApplyCompletionItem(0, "name = name");
        assertEquals("name", myFixture.getEditor().getSelectionModel().getSelectedText());
        assertTyped("x = x");
    }

    public void testATabStopBeforeItsPlaceholderMirrorsIt() {
        complete("$1 = ${1:name}$0");

        assertApplyCompletionItem(0, "name = name");
        assertEquals("name", myFixture.getEditor().getSelectionModel().getSelectedText());
        assertTyped("x = x");
    }

    public void testAPlaceholderTakesTheDefaultOfTheFirstPlaceholderWithItsIndex() {
        complete("${1:a} ${1:b}$0");

        assertApplyCompletionItem(0, "a a");
        assertTyped("x x");
    }

    public void testATabStopMirrorsTheChoiceWithItsIndex() {
        complete("$1 ${1|one,two|}$0");

        assertApplyCompletionItem(0, "one one");
        assertTyped("x x");
    }

    public void testATabStopMirrorsAPlaceholderWhoseTextIsAVariable() {
        complete("${1:$TM_FILENAME_BASE} = $1$0");

        assertApplyCompletionItem(0, "test = test");
        assertTyped("x = x");
    }

    public void testPlaceholdersWithTheSameTextAndOtherIndicesAreNotLinked() {
        complete("${1:a} ${2:a}$0");

        assertApplyCompletionItem(0, "a a");
        assertTyped("x a");
    }

    public void testPlaceholdersWithNestedTextAndOtherIndicesAreNotLinked() {
        complete("${1:a ${2:b}} ${3:a b}$0");

        assertApplyCompletionItem(0, "a b a b");
        assertEquals("a b", myFixture.getEditor().getSelectionModel().getSelectedText());
        assertTyped("x a b");
    }

    public void testATabStopInAPlaceholderRepeatsTheDefaultOfItsIndex() {
        complete("${1:${2:x}-$2} tail$0");

        assertApplyCompletionItem(0, "x-x tail");
        assertEquals("x-x", myFixture.getEditor().getSelectionModel().getSelectedText());
    }

    public void testATabStopInAPlaceholderRepeatsADefaultThatComesLater() {
        complete("${1:$2} ${2:y}$0");

        assertApplyCompletionItem(0, "y y");
        assertTyped("x y");
    }

    public void testADefaultThatRepeatsItselfRepeatsNothing() {
        complete("${1:a$1} = $1$0");

        assertApplyCompletionItem(0, "a = a");
        assertTyped("x = x");
    }

    public void testATabStopRepeatsTheTextOfTheOuterPlaceholderWithItsIndex() {
        complete("${1:hello ${2:you}} = $1, $2$0");

        assertApplyCompletionItem(0, "hello you = hello you, you");
        assertTyped("x = x, you");
    }

    private void complete(String snippet) {
        TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
        var item = new CompletionItem("block");
        item.setInsertText(snippet);
        item.setInsertTextFormat(InsertTextFormat.Snippet);
        var other = new CompletionItem("blockOther");
        assertCompletion("test.snippet-test", "blo<caret>",
                new CompletionList(false, List.of(item, other)), "block", "blockOther");
    }

    /**
     * Types {@code x} over the selected text of the first stop of the template, and checks the text of the document.
     */
    private void assertTyped(String expected) {
        assertNotNull("the snippet must start as a template", TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
        myFixture.type("x");
        assertEquals(expected, myFixture.getEditor().getDocument().getText());
    }
}

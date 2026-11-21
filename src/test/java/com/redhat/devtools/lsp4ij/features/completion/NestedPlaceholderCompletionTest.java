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
 * A completion snippet with a nested placeholder inserts the text of both placeholders, without the closing brace of
 * the outer one, and its first stop selects the whole text of the outer placeholder (#2074).
 */
public class NestedPlaceholderCompletionTest extends LSPCompletionFixtureTestCase {

    public NestedPlaceholderCompletionTest() {
        super("*.snippet-test");
    }

    public void testTheFirstStopOfANestedPlaceholderSelectsItsWholeText() {
        complete("${1:a ${2:b} c} d$0");

        assertApplyCompletionItem(0, "a b c d");
        assertNotNull("the snippet must start as a template", TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
        assertEquals("a b c", myFixture.getEditor().getSelectionModel().getSelectedText());
        myFixture.type("x");
        assertEquals("x d", myFixture.getEditor().getDocument().getText());
    }

    public void testAPlaceholderThatStartsWithANestedPlaceholderSelectsItsWholeText() {
        complete("${1:${2:b} c} d$0");

        assertApplyCompletionItem(0, "b c d");
        assertNotNull("the snippet must start as a template", TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
        assertEquals("b c", myFixture.getEditor().getSelectionModel().getSelectedText());
    }

    public void testANestedTabStopAndChoiceGiveTheirTextToTheirPlaceholder() {
        complete("${1:a $2${3|x,y|}} ${4:e} d$0");

        assertApplyCompletionItem(0, "a x e d");
        assertEquals("a x", myFixture.getEditor().getSelectionModel().getSelectedText());
    }

    public void testAPlaceholderThatIsABracedVariableInsertsItsValue() {
        complete("${1:${TM_FILENAME_BASE}} x$0");

        assertApplyCompletionItem(0, "test x");
    }

    public void testANestedPlaceholderThatIsAVariableGivesItsValueToItsPlaceholder() {
        complete("${1:hello ${2:$TM_FILENAME_BASE}} x$0");

        assertApplyCompletionItem(0, "hello test x");
        assertEquals("hello test", myFixture.getEditor().getSelectionModel().getSelectedText());
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
}

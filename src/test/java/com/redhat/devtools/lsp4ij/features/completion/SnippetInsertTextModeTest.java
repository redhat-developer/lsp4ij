/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.actionSystem.IdeActions;
import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;
import com.redhat.devtools.lsp4ij.internal.capabilities.ClientCapabilitiesFactory;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;

import java.util.List;

/** Exercises actual completion insertion and live templates, rather than only parsed snippet text. */
public class SnippetInsertTextModeTest extends LSPCompletionFixtureTestCase {

    public SnippetInsertTextModeTest() {
        super("*.snippet-test");
    }

    public void testAsIsPreservesIndentationAndTabCharacters() {
        insert(InsertTextMode.AsIs, "block(${1:value})\n\t${2:other}\nend$0",
                "    block(value)\n\tother\nend");
        assertEquals("value", myFixture.getEditor().getSelectionModel().getSelectedText());
        // This is the native action bound to Tab while a live template is active.
        // EditorTab itself inserts indentation and bypasses keymap action selection.
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE);
        assertEquals("other", myFixture.getEditor().getSelectionModel().getSelectedText());
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE);
        assertNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
        assertEquals("    block(value)\n\tother\nend", myFixture.getEditor().getDocument().getText());
        assertEquals(myFixture.getEditor().getDocument().getTextLength(), myFixture.getEditor().getCaretModel().getOffset());
    }

    public void testAsIsDoesNotIndentAlreadyIndentedSnippetAgain() {
        insert(InsertTextMode.AsIs, "block(${1:value})\n        ${2:other}\n    end$0",
                "    block(value)\n        other\n    end");
    }

    public void testAdjustIndentationUsesTheInsertionLine() {
        insert(InsertTextMode.AdjustIndentation, "block(${1:value})\n  ${2:other}\nend$0",
                "    block(value)\n      other\n    end");
    }

    public void testOmittedModeUsesTheAdvertisedClientDefault() {
        assertEquals(InsertTextMode.AdjustIndentation,
                ClientCapabilitiesFactory.create(null).getTextDocument().getCompletion().getInsertTextMode());
        insert(null, "block(${1:value})\n  ${2:other}\nend$0",
                "    block(value)\n      other\n    end");
    }

    public void testAsIsInsertionIsUndoable() {
        insert(InsertTextMode.AsIs, "block(${1:value})\n  ${2:other}\nend$0",
                "    block(value)\n  other\nend");
        myFixture.performEditorAction(IdeActions.ACTION_UNDO);
        assertEquals("    blo", myFixture.getEditor().getDocument().getText());
    }

    private void insert(InsertTextMode mode, String text, String expected) {
        TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
        var item = new CompletionItem("block");
        item.setInsertText(text);
        item.setInsertTextFormat(InsertTextFormat.Snippet);
        item.setInsertTextMode(mode);
        var other = new CompletionItem("blockOther");
        assertCompletion("test.snippet-test", "    blo<caret>",
                new CompletionList(false, List.of(item, other)), "block", "blockOther");
        assertApplyCompletionItem(0, expected);
        assertNotNull("Snippet placeholders must remain active", TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
    }
}

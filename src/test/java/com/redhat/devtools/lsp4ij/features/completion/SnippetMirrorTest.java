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
 * Tests that a tab stop is linked to the placeholder with the same index
 * (see https://github.com/redhat-developer/lsp4ij/issues/2073): typing in one
 * updates the other, whichever comes first in the snippet.
 */
public class SnippetMirrorTest extends LSPCompletionFixtureTestCase {

    public SnippetMirrorTest() {
        super("*.snippet-test");
    }

    public void testATabStopAfterItsPlaceholderMirrorsIt() {
        insert("${1:name} = $1$0");
    }

    public void testATabStopBeforeItsPlaceholderMirrorsIt() {
        insert("$1 = ${1:name}$0");
    }

    private void insert(String text) {
        TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
        var item = new CompletionItem("block");
        item.setInsertText(text);
        item.setInsertTextFormat(InsertTextFormat.Snippet);
        var other = new CompletionItem("blockOther");
        assertCompletion("test.snippet-test", "blo<caret>",
                new CompletionList(false, List.of(item, other)), "block", "blockOther");
        assertApplyCompletionItem(0, "name = name");
        // The first stop selects the placeholder's default; typing there must
        // update the mirrored tab stop too.
        assertEquals("name", myFixture.getEditor().getSelectionModel().getSelectedText());
        myFixture.type('x');
        assertEquals("x = x", myFixture.getEditor().getDocument().getText());
    }
}

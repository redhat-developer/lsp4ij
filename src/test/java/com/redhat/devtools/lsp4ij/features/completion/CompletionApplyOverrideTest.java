/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionFeature;
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionProposal;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An adopter subclasses {@link LSPCompletionProposal} and overrides {@code apply(Document, int)}: the override is still
 * called when an item is accepted, and the item is applied with the insert or the replace range of the key which
 * accepts it.
 */
public class CompletionApplyOverrideTest extends LSPCodeInsightFixtureTestCase {

    // 'foo|Bar': insert covers 'foo' (columns 0 to 3), replace covers the whole word 'fooBar' (columns 0 to 6)
    // language=json
    private static final String INSERT_REPLACE_JSON = """
            {
              "isIncomplete": false,
              "items": [
                {
                  "label": "fooBaz",
                  "textEdit": {
                    "newText": "fooBaz",
                    "insert":  { "start": {"line": 0, "character": 0}, "end": {"line": 0, "character": 3} },
                    "replace": { "start": {"line": 0, "character": 0}, "end": {"line": 0, "character": 6} }
                  }
                }
              ]
            }""";

    private final AtomicBoolean overrideCalled = new AtomicBoolean();

    public CompletionApplyOverrideTest() {
        super("*.ts");
    }

    public void testTheOverrideOfApplyIsCalledAndTabStillReplacesTheWholeWord() {
        assertApplyWithOverride("fooBaz", Lookup.REPLACE_SELECT_CHAR);
    }

    public void testTheOverrideOfApplyIsCalledAndEnterStillInserts() {
        assertApplyWithOverride("fooBazBar", Lookup.NORMAL_SELECT_CHAR);
    }

    private void assertApplyWithOverride(@NotNull String expectedText, char completionChar) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(200);
        MockLanguageServer.INSTANCE.setCompletionList(JSONUtils.getLsp4jGson().fromJson(INSERT_REPLACE_JSON, CompletionList.class));
        PsiFile file = myFixture.configureByText("test.ts", "foo<caret>Bar");
        LanguageServerItem languageServer = awaitLanguageServers(file).get(0);
        languageServer.getClientFeatures().setCompletionFeature(new LSPCompletionFeature() {
            @Override
            public @Nullable LookupElement createLookupElement(@NotNull CompletionItem item,
                                                               @NotNull LSPCompletionContext context) {
                return new LSPCompletionProposal(item, context, this) {
                    @Override
                    protected void apply(Document document, int offset) {
                        overrideCalled.set(true);
                        super.apply(document, offset);
                    }
                };
            }
        });

        myFixture.completeBasic();
        assertNotNull("Completion should be not null", myFixture.getLookup());
        myFixture.selectItem(myFixture.getLookupElements()[0], completionChar);

        assertTrue("the override of apply(Document, int) is called", overrideCalled.get());
        assertEquals(expectedText, myFixture.getEditor().getDocument().getText());
    }
}

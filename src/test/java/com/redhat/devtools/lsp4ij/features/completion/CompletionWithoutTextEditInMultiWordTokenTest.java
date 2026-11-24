/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

/**
 * A completion item without a text edit completes the word at the caret, also when the PSI element at the caret is
 * one token that spans several words, as the value of a property here, and as a whole comment that a custom PSI keeps
 * as one token, after which IntelliJ's prefix was {@code disable-next-line : u}, which matched no item (#447).
 */
public class CompletionWithoutTextEditInMultiWordTokenTest extends LSPCompletionFixtureTestCase {

    // The diagnostic codes of the EmmyLua language server, without a text edit
    private static final String DIAGNOSTIC_CODES = """
            [
              {
                "label": "unused",
                "kind": 20,
                "detail": "diagnostic code"
              },
              {
                "label": "undefined-global",
                "kind": 20,
                "detail": "diagnostic code"
              },
              {
                "label": "need-import",
                "kind": 20,
                "detail": "diagnostic code"
              }
            ]""";

    public CompletionWithoutTextEditInMultiWordTokenTest() {
        super("test.properties");
    }

    public void testTheItemsAreMatchedAgainstTheWordAtTheCaret() {
        assertCompletion("test.properties", "key=disable-next-line : u<caret>", DIAGNOSTIC_CODES,
                "unused", "undefined-global");
        assertFalse(getLookupStrings().toString(), getLookupStrings().contains("need-import"));

        assertApplyCompletionItem(getLookupStrings().indexOf("unused"), "key=disable-next-line : unused<caret>");
    }

    public void testAfterASpaceAnItemIsInsertedAtTheCaret() {
        assertCompletion("test.properties", "key=disable-next-line : <caret>", DIAGNOSTIC_CODES,
                "unused", "undefined-global", "need-import");

        assertApplyCompletionItem(getLookupStrings().indexOf("need-import"), "key=disable-next-line : need-import<caret>");
    }

    private @NotNull List<String> getLookupStrings() {
        return Stream.of(myFixture.getLookupElements())
                .map(LookupElement::getLookupString)
                .toList();
    }
}

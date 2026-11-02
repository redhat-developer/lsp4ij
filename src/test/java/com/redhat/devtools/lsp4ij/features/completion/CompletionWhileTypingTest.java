/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;
import com.intellij.util.ThrowableRunnable;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Typing while the completion auto popup is open, or right after it found nothing, filters the items the language
 * server gave, unless they can't be filtered: an incomplete list, or no items for the trigger character which opened
 * the popup. Then the language server is asked again.
 *
 * @see <a href="https://github.com/redhat-developer/lsp4ij/issues/1020">#1020</a>
 */
public class CompletionWhileTypingTest extends LSPCodeInsightFixtureTestCase {

    private CompletionAutoPopupTester tester;

    public CompletionWhileTypingTest() {
        super("*.txt");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tester = new CompletionAutoPopupTester(myFixture);
    }

    // The auto popup runs as it does in the IDE only when the test types from outside the EDT
    @Override
    protected boolean runInDispatchThread() {
        return false;
    }

    @Override
    protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
        tester.runWithAutoPopupEnabled(testRunnable);
    }

    public void testTypingAsksAgainForAnIncompleteList() throws Exception {
        open("");
        answer(true, "alpha", "almond", "kiwi");
        tester.typeWithPauses("a");
        assertEquals(List.of("almond", "alpha"), lookupStrings());

        answer(true, "alpine", "alps");
        tester.typeWithPauses("l");

        assertEquals(2, requests().size());
        assertEquals(List.of("alpine", "alps"), lookupStrings());
    }

    public void testTypingFiltersACompleteList() throws Exception {
        open("");
        answer(false, "alpha", "almond", "kiwi");
        tester.typeWithPauses("a");

        answer(false, "alpine", "alps");
        tester.typeWithPauses("l");

        assertEquals(1, requests().size());
        assertEquals(List.of("almond", "alpha"), lookupStrings());
    }

    public void testTypingAsksAgainWhenATriggerCharacterGetsNoItems() throws Exception {
        MockLanguageServer.INSTANCE.setCompletionTriggerChars(Set.of("("));
        open("print");
        answer(false);
        tester.typeWithPauses("(");
        assertEmpty(lookupStrings());

        answer(false, "player", "print");
        tester.typeWithPauses("p");

        assertTriggerKinds(CompletionTriggerKind.TriggerCharacter, CompletionTriggerKind.Invoked);
        assertEquals(List.of("player", "print"), lookupStrings());
    }

    public void testTypingAsksAgainWhenATriggerCharacterGetsNull() throws Exception {
        MockLanguageServer.INSTANCE.setCompletionTriggerChars(Set.of("("));
        open("print");
        MockLanguageServer.INSTANCE.setCompletionList(null);
        tester.typeWithPauses("(");
        assertEmpty(lookupStrings());

        answer(false, "player", "print");
        tester.typeWithPauses("p");

        assertTriggerKinds(CompletionTriggerKind.TriggerCharacter, CompletionTriggerKind.Invoked);
        assertEquals(List.of("player", "print"), lookupStrings());
    }

    public void testTypingFiltersTheItemsOfATriggerCharacter() throws Exception {
        MockLanguageServer.INSTANCE.setCompletionTriggerChars(Set.of("("));
        open("print");
        answer(false, "player", "point", "zeta");
        tester.typeWithPauses("(");

        answer(false, "pear");
        tester.typeWithPauses("p");

        assertTriggerKinds(CompletionTriggerKind.TriggerCharacter);
        assertEquals(List.of("player", "point"), lookupStrings());
    }

    private void open(@NotNull String text) throws Exception {
        var file = myFixture.configureByText("main.txt", text + "<caret>");
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS);
    }

    private static void answer(boolean incomplete, String... labels) {
        var items = Stream.of(labels).map(CompletionItem::new).toList();
        MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(incomplete, items));
    }

    private static List<CompletionParams> requests() {
        return MockLanguageServer.INSTANCE.getTextDocumentService().getCompletionRequests();
    }

    private static void assertTriggerKinds(CompletionTriggerKind... expected) {
        assertEquals(List.of(expected), requests().stream()
                .map(request -> request.getContext().getTriggerKind())
                .toList());
    }

    private List<String> lookupStrings() {
        return EdtTestUtil.runInEdtAndGet(() -> {
            Lookup lookup = myFixture.getLookup();
            return lookup == null ? List.of() : lookup.getItems().stream()
                    .map(LookupElement::getLookupString)
                    .sorted()
                    .toList();
        });
    }
}

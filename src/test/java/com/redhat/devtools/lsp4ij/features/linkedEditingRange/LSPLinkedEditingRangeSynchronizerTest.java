/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.linkedEditingRange;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.LinkedEditingRangeParams;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * A language server which supports 'textDocument/linkedEditingRange' links ranges, such as the declaration and the uses
 * of a local variable: typing in one of them edits the others too.
 */
public class LSPLinkedEditingRangeSynchronizerTest extends LSPCodeInsightFixtureTestCase {

    private static final String TEXT = "var name = 1; print(name); name++;";
    // the declaration and the two uses of "name" in TEXT
    private static final List<Range> NAME_RANGES = List.of(range(4, 8), range(20, 24), range(27, 31));
    private static final List<TextRange> NAME_TEXT_RANGES = List.of(
            new TextRange(4, 8), new TextRange(20, 24), new TextRange(27, 31));
    // at the end of the declaration of "name"
    private static final int AFTER_DECLARATION = 8;

    public LSPLinkedEditingRangeSynchronizerTest() {
        super("*.foo");
    }

    public void testTypingInALinkedRangeEditsTheOthers() throws Exception {
        open(new LinkedEditingRanges(NAME_RANGES));
        assertEquals(NAME_TEXT_RANGES, linkedAt(AFTER_DECLARATION));

        myFixture.type("s");

        assertEquals("var names = 1; print(names); names++;", text());
        List<LinkedEditingRangeParams> requests = requests();
        assertEquals(1, requests.size());
        assertEquals(new Position(0, AFTER_DECLARATION), requests.get(0).getPosition());
    }

    public void testTheRangeWithTheCaretLeads() throws Exception {
        open(new LinkedEditingRanges(NAME_RANGES));
        // in the middle of "name" in print(name)
        assertEquals(List.of(new TextRange(20, 24), new TextRange(4, 8), new TextRange(27, 31)), linkedAt(22));

        myFixture.type("X");

        assertEquals("var naXme = 1; print(naXme); naXme++;", text());
        assertEquals("the caret stays after the typed text", 24, myFixture.getEditor().getCaretModel().getOffset());
    }

    public void testUndoRevertsTheLinkedEditsTogether() throws Exception {
        open(new LinkedEditingRanges(NAME_RANGES));
        linkedAt(AFTER_DECLARATION);
        myFixture.type("s");

        var undoManager = UndoManager.getInstance(myFixture.getProject());
        undoManager.undo(TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor()));

        assertEquals(TEXT, text());
    }

    public void testRetypingASelectedRangeEditsTheOthers() throws Exception {
        open(new LinkedEditingRanges(NAME_RANGES));
        linkedAt(AFTER_DECLARATION);
        myFixture.getEditor().getSelectionModel().setSelection(4, AFTER_DECLARATION);

        myFixture.type("id");

        assertEquals("var id = 1; print(id); id++;", text());
    }

    public void testDeletingARangeAndTypingANewNameEditsTheOthers() throws Exception {
        open(new LinkedEditingRanges(NAME_RANGES));
        linkedAt(AFTER_DECLARATION);

        myFixture.type("\b\b\b\b");
        assertEquals("the other ranges are emptied too", "var  = 1; print(); ++;", text());
        myFixture.type("id");

        assertEquals("var id = 1; print(id); id++;", text());
    }

    public void testTheRangesWhereTheEditorOpensAreAskedFor() throws Exception {
        // the caret stays at offset 0, where the editor opens
        open("name = 1; print(name);", new LinkedEditingRanges(List.of(range(0, 4), range(16, 20))));
        var synchronizer = synchronizer();

        PlatformTestUtil.waitWithEventsDispatching("linked editing ranges where the editor opens",
                () -> !synchronizer.getRanges().isEmpty(), 30);
        myFixture.type("x");

        assertEquals("xname = 1; print(xname);", text());
    }

    public void testATextWhichIsNotAWordIsNotCopied() throws Exception {
        open(new LinkedEditingRanges(NAME_RANGES));
        linkedAt(AFTER_DECLARATION);

        myFixture.type(" ");

        assertEquals("var name  = 1; print(name); name++;", text());
        assertEmpty(synchronizer().getRanges());
    }

    public void testTheWordPatternOfTheServerIsUsed() throws Exception {
        open(new LinkedEditingRanges(NAME_RANGES, "[a-z]+"));
        linkedAt(AFTER_DECLARATION);

        myFixture.type("1");

        assertEquals("var name1 = 1; print(name); name++;", text());
        assertEmpty(synchronizer().getRanges());
    }

    public void testAChangeOutsideTheLinkedRangesDropsThem() throws Exception {
        open(new LinkedEditingRanges(NAME_RANGES));
        linkedAt(AFTER_DECLARATION);

        WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                () -> myFixture.getEditor().getDocument().insertString(TEXT.length(), " end"));
        assertEmpty(synchronizer().getRanges());
        myFixture.type("s");

        assertEquals("var names = 1; print(name); name++; end", text());
    }

    public void testRangesWithoutTheCaretAreIgnored() throws Exception {
        open(new LinkedEditingRanges(NAME_RANGES));

        assertEmpty(linkedAt(0));
    }

    public void testRangesWithDifferentTextsAreIgnored() throws Exception {
        // "name" and "prin"
        open(new LinkedEditingRanges(List.of(range(4, 8), range(14, 18))));

        assertEmpty(linkedAt(AFTER_DECLARATION));
    }

    public void testTouchingRangesAreIgnored() throws Exception {
        // a text typed between them would grow both
        open("var abab = 1;", new LinkedEditingRanges(List.of(range(4, 6), range(6, 8))));

        assertEmpty(linkedAt(5));
    }

    public void testAWordPatternWhichBacktracksWithoutEndDoesNotMatch() {
        var backtracking = Pattern.compile("(a+)+$");

        assertFalse(LSPLinkedEditingRangeSynchronizer.isWord("a".repeat(40) + "!", backtracking));
        assertTrue(LSPLinkedEditingRangeSynchronizer.isWord("a".repeat(40), backtracking));
        assertTrue("an empty range can be typed again", LSPLinkedEditingRangeSynchronizer.isWord("", backtracking));
    }

    public void testATextLongerThanTheReadLimitIsNotMatched() {
        var text = new LSPLinkedEditingRangeSynchronizer.ReadLimitedText("a".repeat(11), 10);

        assertFalse(LSPLinkedEditingRangeSynchronizer.isWord(text, Pattern.compile("a+")));
        assertEquals("the word pattern doesn't run on it", 0, text.reads());
    }

    public void testASingleRangeIsIgnored() throws Exception {
        open(new LinkedEditingRanges(List.of(range(4, 8))));

        assertEmpty(linkedAt(AFTER_DECLARATION));
    }

    public void testAServerWithoutLinkedEditingIsNotAsked() throws Exception {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setLinkedEditingRangeProvider((Boolean) null);
            return capabilities;
        });
        open(new LinkedEditingRanges(NAME_RANGES));
        myFixture.getEditor().getCaretModel().moveToOffset(AFTER_DECLARATION);

        assertNull(synchronizer().requestNow());
        assertEmpty(requests());
    }

    private void open(@Nullable LinkedEditingRanges linkedEditingRanges) throws Exception {
        open(TEXT, linkedEditingRanges);
    }

    private void open(String text, @Nullable LinkedEditingRanges linkedEditingRanges) throws Exception {
        MockLanguageServer.INSTANCE.setLinkedEditingRanges(linkedEditingRanges);
        var file = myFixture.configureByText("main.foo", text);
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS);
    }

    /**
     * Moves the caret to the given offset and returns the ranges which are then edited together.
     */
    private List<TextRange> linkedAt(int offset) {
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        var synchronizer = synchronizer();
        var request = synchronizer.requestNow();
        assertNotNull("the linked editing ranges are asked for", request);
        PlatformTestUtil.waitWithEventsDispatching("linked editing ranges", request::isDone, 30);
        return synchronizer.getRanges();
    }

    private LSPLinkedEditingRangeSynchronizer synchronizer() {
        var synchronizer = LSPLinkedEditingRangeSynchronizer.getSynchronizer(myFixture.getEditor());
        assertNotNull("the editor has a linked editing synchronizer", synchronizer);
        return synchronizer;
    }

    private String text() {
        return myFixture.getEditor().getDocument().getText();
    }

    private static List<LinkedEditingRangeParams> requests() {
        return MockLanguageServer.INSTANCE.getTextDocumentService().getLinkedEditingRangeRequests();
    }

    private static Range range(int start, int end) {
        return new Range(new Position(0, start), new Position(0, end));
    }
}

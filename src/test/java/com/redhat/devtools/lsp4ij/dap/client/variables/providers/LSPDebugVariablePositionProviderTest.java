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
package com.redhat.devtools.lsp4ij.dap.client.variables.providers;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterVariableSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.InlineValue;
import org.eclipse.lsp4j.InlineValueEvaluatableExpression;
import org.eclipse.lsp4j.InlineValueText;
import org.eclipse.lsp4j.InlineValueVariableLookup;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The variables that a language server names with 'textDocument/inlineValue' are shown where it names them last,
 * before or on the stopped line; a language server which doesn't answer in time, or fails, names none, so that the
 * highlighter gives the positions.
 */
public class LSPDebugVariablePositionProviderTest extends BasePlatformTestCase {

    private static final String TEXT = """
            var count = 0
            for (item : items) {
              count = count + item
            }
            print(count)
            """;

    public void testTheLanguageServerPositionsWinOverTheHighlighter() {
        var providers = new DebugAdapterVariableSupport().getDebugVariablePositionProvider();

        assertEquals(List.of(LSPDebugVariablePositionProvider.class, HighlighterDebugVariablePositionProvider.class),
                providers.stream().map(Object::getClass).toList());
    }

    public void testTheStoppedLocationIsTheFramesLocationFromZero() {
        assertEquals(range(2, 4, 2, 4), LSPDebugVariablePositionProvider.getStoppedLocation(frame(3, 5, null, null)));
        assertEquals(range(2, 4, 3, 9), LSPDebugVariablePositionProvider.getStoppedLocation(frame(3, 5, 4, 10)));
        // a DAP server without columns
        assertEquals(range(2, 0, 2, 0), LSPDebugVariablePositionProvider.getStoppedLocation(frame(3, 0, null, null)));
    }

    public void testTheInlineValuesAreAskedForUpToTheEndOfTheStoppedLine() {
        var document = EditorFactory.getInstance().createDocument(TEXT);

        assertEquals(range(0, 0, 2, 22), LSPDebugVariablePositionProvider.getRange(document, 2));
        assertEquals(range(0, 0, 0, 13), LSPDebugVariablePositionProvider.getRange(document, 0));
        // a stopped line past the end of the document
        assertEquals(range(0, 0, 5, 0), LSPDebugVariablePositionProvider.getRange(document, 42));
    }

    public void testAVariableIsShownWhereTheLanguageServerNamesItLastBeforeTheStop() {
        var lookups = named(List.of(
                lookup("count", 0, 4, 0, 9),
                lookup("count", 2, 2, 2, 7),
                lookup("count", 2, 10, 2, 15),
                lookup("item", 1, 5, 1, 9),
                lookup("item", 2, 18, 2, 22),
                // after the stopped line
                lookup("count", 4, 6, 4, 11)), 2);

        assertEquals(range(2, 10, 2, 15), LSPDebugVariablePositionProvider.findVariableRange(lookups, "count"));
        assertEquals(range(2, 18, 2, 22), LSPDebugVariablePositionProvider.findVariableRange(lookups, "item"));
    }

    public void testAnOccurrenceAfterTheStoppedLineIsIgnored() {
        var lookups = named(List.of(
                lookup("count", 0, 4, 0, 9),
                lookup("count", 4, 6, 4, 11)), 1);

        assertEquals(range(0, 4, 0, 9), LSPDebugVariablePositionProvider.findVariableRange(lookups, "count"));
    }

    public void testAVariableThatTheLanguageServerDoesNotNameHasNoPosition() {
        var lookups = named(List.of(
                lookup("count", 0, 4, 0, 9)), 2);

        // the highlighter gives its position
        assertNull(LSPDebugVariablePositionProvider.findVariableRange(lookups, "items"));
        assertNull(LSPDebugVariablePositionProvider.findVariableRange(List.of(), "count"));
    }

    public void testTheCaseOfTheNameMattersOnlyForACaseSensitiveLookup() {
        var lookups = named(List.of(
                new InlineValue(new InlineValueVariableLookup(range(0, 4, 0, 9), true, "count")),
                new InlineValue(new InlineValueVariableLookup(range(1, 5, 1, 9), false, "item"))), 2);

        assertNull(LSPDebugVariablePositionProvider.findVariableRange(lookups, "COUNT"));
        assertEquals(range(1, 5, 1, 9), LSPDebugVariablePositionProvider.findVariableRange(lookups, "ITEM"));
    }

    public void testALookupWithoutAVariableNameLooksUpTheNameAtItsRange() {
        var lookups = named(List.of(
                new InlineValue(new InlineValueVariableLookup(range(1, 5, 1, 9), true)),
                // no name at the range
                new InlineValue(new InlineValueVariableLookup(range(1, 3, 1, 4), true))), 2);

        assertEquals(1, lookups.size());
        assertEquals("item", lookups.get(0).getVariableName());
        assertEquals(range(1, 5, 1, 9), LSPDebugVariablePositionProvider.findVariableRange(lookups, "item"));
    }

    public void testTextsAndExpressionsAreIgnored() {
        var lookups = named(List.of(
                new InlineValue(new InlineValueText(range(0, 4, 0, 9), "count = 0")),
                new InlineValue(new InlineValueEvaluatableExpression(range(2, 10, 2, 22), "count + item"))), 2);

        assertEmpty(lookups);
    }

    public void testTheInlineValuesThatLsp4jReadsAsEither3AreUnderstood() {
        // lsp4j reads the inline values of a response as Either3, which are not InlineValue
        var count = new InlineValueVariableLookup(range(0, 4, 0, 9), true, "count");
        List<Either3<InlineValueText, InlineValueVariableLookup, InlineValueEvaluatableExpression>> answer = List.of(
                Either3.forFirst(new InlineValueText(range(0, 0, 0, 3), "var")),
                Either3.forSecond(count),
                Either3.forThird(new InlineValueEvaluatableExpression(range(2, 10, 2, 22))));

        assertEquals(List.of(count), LSPDebugVariablePositionProvider.toVariableLookups(answer));
        assertEmpty(LSPDebugVariablePositionProvider.toVariableLookups(null));
    }

    public void testVariableLookupsThatComeInTimeAreUsed() throws Exception {
        var answer = List.of(variableLookup("count", 0, 4, 0, 9));

        var lookups = LSPDebugVariablePositionProvider.waitForVariableLookups(CompletableFuture.completedFuture(answer),
                Duration.ofSeconds(5), new CancellationSupport());

        assertEquals(answer, lookups.get(5, TimeUnit.SECONDS));
    }

    public void testNoVariableLookupsAreEmpty() throws Exception {
        var lookups = LSPDebugVariablePositionProvider.waitForVariableLookups(CompletableFuture.completedFuture(null),
                Duration.ofSeconds(5), new CancellationSupport());

        assertEmpty(lookups.get(5, TimeUnit.SECONDS));
    }

    public void testVariableLookupsThatDoNotComeInTimeAreEmptyAndTheRequestIsCancelled() throws Exception {
        var cancellationSupport = new CancellationSupport();
        var request = cancellationSupport.execute(new CompletableFuture<List<InlineValueVariableLookup>>());
        var applied = new AtomicInteger();

        var lookups = LSPDebugVariablePositionProvider.waitForVariableLookups(request, Duration.ofMillis(50), cancellationSupport);
        lookups.thenAccept(unused -> applied.incrementAndGet());

        // the wait never fails, and ends without the language server
        assertEmpty(lookups.get(5, TimeUnit.SECONDS));
        assertTrue("the language server must stop computing the inline values", request.isCancelled());
        // an answer which comes too late changes nothing
        request.complete(List.of(variableLookup("count", 0, 4, 0, 9)));
        assertEmpty(lookups.join());
        assertEquals(1, applied.get());
    }

    public void testVariableLookupsThatFailAreEmpty() throws Exception {
        var lookups = LSPDebugVariablePositionProvider.waitForVariableLookups(
                CompletableFuture.failedFuture(new IllegalStateException("the language server failed")),
                Duration.ofSeconds(5), new CancellationSupport());

        assertEmpty(lookups.get(5, TimeUnit.SECONDS));
    }

    private static List<InlineValueVariableLookup> named(List<InlineValue> inlineValues, int stoppedLine) {
        return LSPDebugVariablePositionProvider.getNamedVariableLookups(
                LSPDebugVariablePositionProvider.toVariableLookups(inlineValues), TEXT, stoppedLine);
    }

    private static InlineValue lookup(String name, int startLine, int startCharacter, int endLine, int endCharacter) {
        return new InlineValue(variableLookup(name, startLine, startCharacter, endLine, endCharacter));
    }

    private static InlineValueVariableLookup variableLookup(String name, int startLine, int startCharacter, int endLine, int endCharacter) {
        return new InlineValueVariableLookup(range(startLine, startCharacter, endLine, endCharacter), true, name);
    }

    private static Range range(int startLine, int startCharacter, int endLine, int endCharacter) {
        return new Range(new Position(startLine, startCharacter), new Position(endLine, endCharacter));
    }

    private static StackFrame frame(int line, int column, Integer endLine, Integer endColumn) {
        var frame = new StackFrame();
        frame.setId(7);
        frame.setName("main");
        frame.setLine(line);
        frame.setColumn(column);
        frame.setEndLine(endLine);
        frame.setEndColumn(endColumn);
        return frame;
    }
}

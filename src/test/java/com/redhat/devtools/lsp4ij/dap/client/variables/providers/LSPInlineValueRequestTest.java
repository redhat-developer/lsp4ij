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

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.InlineValue;
import org.eclipse.lsp4j.InlineValueContext;
import org.eclipse.lsp4j.InlineValueParams;
import org.eclipse.lsp4j.InlineValueText;
import org.eclipse.lsp4j.InlineValueVariableLookup;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The language server of a file is asked for the inline values with 'textDocument/inlineValue', with the DAP frame and
 * the stopped location as context, only when it supports them (inlineValueProvider).
 */
public class LSPInlineValueRequestTest extends LSPCodeInsightFixtureTestCase {

    private static final Range RANGE = new Range(new Position(0, 0), new Position(1, 8));
    private static final Range STOPPED_LOCATION = new Range(new Position(1, 0), new Position(1, 0));

    public LSPInlineValueRequestTest() {
        super("*.foo");
    }

    public void testALanguageServerWhichSupportsInlineValuesIsAskedForThem() throws Exception {
        serverSupportsInlineValues(true);
        var count = new InlineValueVariableLookup(new Range(new Position(1, 6), new Position(1, 11)), true, "count");
        var text = new InlineValueText(new Range(new Position(0, 0), new Position(0, 3)), "var");
        MockLanguageServer.INSTANCE.getTextDocumentService().setMockInlineValues(List.of(new InlineValue(count), new InlineValue(text)));
        var file = open();

        var lookups = LSPDebugVariablePositionProvider.getVariableLookups(file, RANGE, new InlineValueContext(7, STOPPED_LOCATION),
                new CancellationSupport()).get(10, TimeUnit.SECONDS);

        // the variable lookups, as the language server sent them
        assertEquals(List.of(count), lookups);
        List<InlineValueParams> requests = MockLanguageServer.INSTANCE.getTextDocumentService().getInlineValueRequests();
        assertEquals(1, requests.size());
        var request = requests.get(0);
        assertEquals(LSPIJUtils.toUriAsString(file), request.getTextDocument().getUri());
        assertEquals(RANGE, request.getRange());
        assertEquals(7, request.getContext().getFrameId());
        assertEquals(STOPPED_LOCATION, request.getContext().getStoppedLocation());
    }

    public void testALanguageServerWhichDoesNotSupportInlineValuesIsNotAsked() throws Exception {
        serverSupportsInlineValues(false);
        var file = open();

        var lookups = LSPDebugVariablePositionProvider.getVariableLookups(file, RANGE, new InlineValueContext(7, STOPPED_LOCATION),
                new CancellationSupport()).get(10, TimeUnit.SECONDS);

        // the highlighter gives the positions
        assertEmpty(lookups);
        assertEmpty(MockLanguageServer.INSTANCE.getTextDocumentService().getInlineValueRequests());
    }

    public void testALanguageServerWhichAnswersNullNamesNoVariable() throws Exception {
        serverSupportsInlineValues(true);
        var file = open();

        var lookups = LSPDebugVariablePositionProvider.getVariableLookups(file, RANGE, new InlineValueContext(7, STOPPED_LOCATION),
                new CancellationSupport()).get(10, TimeUnit.SECONDS);

        assertEmpty(lookups);
        assertEquals(1, MockLanguageServer.INSTANCE.getTextDocumentService().getInlineValueRequests().size());
    }

    private static void serverSupportsInlineValues(boolean inlineValueProvider) {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            if (inlineValueProvider) {
                capabilities.setInlineValueProvider(true);
            }
            return capabilities;
        });
    }

    private PsiFile open() throws Exception {
        var file = myFixture.configureByText("main.foo", "var count = 0\nprint(count)\n");
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
        return file;
    }
}

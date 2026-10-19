/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.inlineCompletion;

import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import org.eclipse.lsp4j.InlineCompletionContext;
import org.eclipse.lsp4j.InlineCompletionItem;
import org.eclipse.lsp4j.InlineCompletionList;
import org.eclipse.lsp4j.InlineCompletionParams;
import org.eclipse.lsp4j.InlineCompletionTriggerKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectedCompletionInfo;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/** The transmitted inline request must retain invocation and popup context at the same caret position. */
public class LSPInlineCompletionContextTest extends LSPCodeInsightFixtureTestCase {

    public LSPInlineCompletionContextTest() {
        super("*.inline-test");
    }

    public void testInvocationAndSelectedItemInvalidateSameOffsetCache() throws Exception {
        var service = new RecordingService();
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setInlineCompletionProvider(true);
            return capabilities;
        });
        MockLanguageServer.INSTANCE.setTextDocumentService(service);
        var file = myFixture.configureByText("test.inline-test", "con<caret>");
        var support = new LSPInlineCompletionSupport(file);
        var identifier = new TextDocumentIdentifier(LSPIJUtils.toUri(file).toString());
        var automatic = params(identifier, InlineCompletionTriggerKind.Automatic, null);
        support.getInlineCompletions(automatic).get(5, TimeUnit.SECONDS);
        var explicit = params(identifier, InlineCompletionTriggerKind.Invoked, null);
        support.getInlineCompletions(explicit).get(5, TimeUnit.SECONDS);
        var selected = new SelectedCompletionInfo(new Range(new Position(0, 0), new Position(0, 3)), "console");
        var popup = params(identifier, InlineCompletionTriggerKind.Automatic, selected);
        var result = support.getInlineCompletions(popup).get(5, TimeUnit.SECONDS);
        assertEquals(3, service.requests.size());
        assertEquals(InlineCompletionTriggerKind.Automatic, service.requests.get(0).getContext().getTriggerKind());
        assertEquals(InlineCompletionTriggerKind.Invoked, service.requests.get(1).getContext().getTriggerKind());
        assertEquals(selected, service.requests.get(2).getContext().getSelectedCompletionInfo());
        assertEquals(popup.getContext(), service.requests.get(2).getContext());
        assertEquals(identifier.getUri(), popup.getTextDocument().getUri());
        assertSame(result, support.getInlineCompletions(
                params(identifier, InlineCompletionTriggerKind.Automatic, selected)).get(5, TimeUnit.SECONDS));
        assertEquals("Equivalent requests may reuse the response", 3, service.requests.size());

        var changed = new SelectedCompletionInfo(selected.getRange(), "constant");
        support.getInlineCompletions(params(identifier, InlineCompletionTriggerKind.Automatic, changed))
                .get(5, TimeUnit.SECONDS);
        assertEquals(4, service.requests.size());
        assertEquals("constant", service.requests.get(3).getContext().getSelectedCompletionInfo().getText());
    }

    private static LSPInlineCompletionParams params(TextDocumentIdentifier identifier,
                                                    InlineCompletionTriggerKind kind,
                                                    SelectedCompletionInfo selected) {
        var params = new LSPInlineCompletionParams(identifier, new Position(0, 3), 3);
        params.setContext(new InlineCompletionContext(kind, selected));
        return params;
    }

    private static final class RecordingService extends MockTextDocumentService {
        private final List<InlineCompletionParams> requests = new CopyOnWriteArrayList<>();

        private RecordingService() {
            super(CompletableFuture::completedFuture);
        }

        @Override
        public CompletableFuture<Either<List<InlineCompletionItem>, InlineCompletionList>> inlineCompletion(
                InlineCompletionParams params) {
            requests.add(params);
            return CompletableFuture.completedFuture(Either.forLeft(List.of()));
        }
    }
}

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.inlayhint;

import com.google.gson.JsonPrimitive;
import com.intellij.codeInsight.hints.declarative.CollapseState;
import com.intellij.codeInsight.hints.declarative.CollapsiblePresentationTreeBuilder;
import com.intellij.codeInsight.hints.declarative.HintFormat;
import com.intellij.codeInsight.hints.declarative.InlayActionData;
import com.intellij.codeInsight.hints.declarative.InlayPayload;
import com.intellij.codeInsight.hints.declarative.InlayPosition;
import com.intellij.codeInsight.hints.declarative.InlayTreeSink;
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition;
import com.intellij.codeInsight.hints.declarative.OwnBypassCollector;
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.InlayHintRegistrationOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A language server which supports 'inlayHint/resolve' can leave the label of an inlay hint to it: an inlay hint
 * without a label is then resolved before it's shown, and dropped when its resolve fails or gives no label.
 */
public class InlayHintResolveTest extends LSPCodeInsightFixtureTestCase {

    private static final String TEXT = "let x = 1";
    // after "x" in TEXT
    private static final Position UNLABELED_HINT_POSITION = new Position(0, 5);
    private static final int UNLABELED_HINT_OFFSET = 5;
    // after "1" in TEXT
    private static final Position LABELED_HINT_POSITION = new Position(0, 9);
    private static final int LABELED_HINT_OFFSET = 9;

    public InlayHintResolveTest() {
        super("*.foo");
    }

    public void testAHintWithoutALabelIsResolved() throws Exception {
        serverResolvesInlayHints();
        MockLanguageServer.INSTANCE.getTextDocumentService().setMockInlayHints(List.of(hintWithoutLabel()));
        MockLanguageServer.INSTANCE.getTextDocumentService().setInlayHintResolveProcessor(hint -> {
            var resolved = new InlayHint(hint.getPosition(), Either.forLeft(": int"));
            resolved.setData(hint.getData());
            return CompletableFuture.completedFuture(resolved);
        });
        open();

        assertEquals(List.of(new Inlay(UNLABELED_HINT_OFFSET, ": int")), collect());
        List<InlayHint> requests = MockLanguageServer.INSTANCE.getTextDocumentService().getInlayHintResolveRequests();
        assertEquals(1, requests.size());
        assertEquals(UNLABELED_HINT_POSITION, requests.get(0).getPosition());
        assertEquals("the hint is resolved with the data the server gave it", new JsonPrimitive("hint-1"), requests.get(0).getData());
    }

    public void testAHintWhoseResolveFailsIsDropped() throws Exception {
        serverResolvesInlayHints();
        MockLanguageServer.INSTANCE.getTextDocumentService().setMockInlayHints(List.of(hintWithoutLabel(), hintWithLabel()));
        MockLanguageServer.INSTANCE.getTextDocumentService().setInlayHintResolveProcessor(hint ->
                CompletableFuture.failedFuture(new ResponseErrorException(
                        new ResponseError(ResponseErrorCode.InvalidParams, "The hint is stale", null))));
        open();

        assertEquals(List.of(new Inlay(LABELED_HINT_OFFSET, ": String")), collect());
        assertEquals(1, MockLanguageServer.INSTANCE.getTextDocumentService().getInlayHintResolveRequests().size());
    }

    public void testAHintWhoseResolveGivesNoLabelIsDropped() throws Exception {
        serverResolvesInlayHints();
        // the mock language server resolves a hint to itself
        MockLanguageServer.INSTANCE.getTextDocumentService().setMockInlayHints(List.of(hintWithoutLabel(), hintWithLabel()));
        open();

        assertEquals(List.of(new Inlay(LABELED_HINT_OFFSET, ": String")), collect());
        assertEquals(1, MockLanguageServer.INSTANCE.getTextDocumentService().getInlayHintResolveRequests().size());
    }

    public void testAHintWhoseResolvedLabelHasANullPartIsDropped() throws Exception {
        serverResolvesInlayHints();
        MockLanguageServer.INSTANCE.getTextDocumentService().setMockInlayHints(List.of(hintWithoutLabel(), hintWithLabel()));
        MockLanguageServer.INSTANCE.getTextDocumentService().setInlayHintResolveProcessor(hint -> {
            var resolved = new InlayHint(hint.getPosition(), Either.forRight(Arrays.asList(null, new InlayHintLabelPart(": int"))));
            resolved.setData(hint.getData());
            return CompletableFuture.completedFuture(resolved);
        });
        open();

        assertEquals("the malformed hint is dropped, the other one shows",
                List.of(new Inlay(LABELED_HINT_OFFSET, ": String")),
                collect());
    }

    public void testAHintWhoseResolvedLabelHasAPartWithoutAValueIsDropped() throws Exception {
        serverResolvesInlayHints();
        MockLanguageServer.INSTANCE.getTextDocumentService().setMockInlayHints(List.of(hintWithoutLabel(), hintWithLabel()));
        MockLanguageServer.INSTANCE.getTextDocumentService().setInlayHintResolveProcessor(hint -> {
            var resolved = new InlayHint(hint.getPosition(),
                    Either.forRight(List.of(new InlayHintLabelPart(), new InlayHintLabelPart(": int"))));
            resolved.setData(hint.getData());
            return CompletableFuture.completedFuture(resolved);
        });
        open();

        assertEquals("the malformed hint is dropped, the other one shows",
                List.of(new Inlay(LABELED_HINT_OFFSET, ": String")),
                collect());
    }

    public void testAHintWithALabelIsNotResolved() throws Exception {
        serverResolvesInlayHints();
        MockLanguageServer.INSTANCE.getTextDocumentService().setMockInlayHints(List.of(hintWithLabel()));
        open();

        assertEquals(List.of(new Inlay(LABELED_HINT_OFFSET, ": String")), collect());
        assertEmpty(MockLanguageServer.INSTANCE.getTextDocumentService().getInlayHintResolveRequests());
    }

    private static InlayHint hintWithoutLabel() {
        var hint = new InlayHint();
        hint.setPosition(UNLABELED_HINT_POSITION);
        hint.setData("hint-1");
        return hint;
    }

    private static InlayHint hintWithLabel() {
        return new InlayHint(LABELED_HINT_POSITION, Either.forLeft(": String"));
    }

    private static void serverResolvesInlayHints() {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            var inlayHintProvider = new InlayHintRegistrationOptions();
            inlayHintProvider.setResolveProvider(true);
            capabilities.setInlayHintProvider(inlayHintProvider);
            return capabilities;
        });
    }

    private void open() throws Exception {
        var file = myFixture.configureByText("main.foo", TEXT);
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS);
    }

    private List<Inlay> collect() throws Exception {
        var file = myFixture.getFile();
        var collector = (OwnBypassCollector) new LSPInlayHintsProvider().createCollector(file, myFixture.getEditor());
        assertNotNull(collector);
        var sink = new InlaySink();
        // IntelliJ collects the inlay hints off the EDT, in a read action
        ApplicationManager.getApplication()
                .executeOnPooledThread(() -> ReadAction.run(() -> collector.collectHintsForFile(file, sink)))
                .get(30, TimeUnit.SECONDS);
        return sink.inlays;
    }

    private record Inlay(int offset, @NotNull String text) {
    }

    /**
     * Keeps the offset and the text of the inlays which are added.
     */
    private static final class InlaySink implements InlayTreeSink {

        private final List<Inlay> inlays = new ArrayList<>();

        @Override
        public void addPresentation(@NotNull InlayPosition position,
                                    @Nullable List<InlayPayload> payloads,
                                    @Nullable String tooltip,
                                    @NotNull HintFormat hintFormat,
                                    @NotNull Function1<? super PresentationTreeBuilder, Unit> builder) {
            var text = new TextBuilder();
            builder.invoke(text);
            inlays.add(new Inlay(((InlineInlayPosition) position).getOffset(), text.getText()));
        }

        @Override
        public void whenOptionEnabled(@NotNull String optionId, @NotNull Function0<Unit> block) {
            block.invoke();
        }
    }

    /**
     * Keeps the text of an inlay.
     */
    private static final class TextBuilder implements PresentationTreeBuilder {

        private final StringBuilder text = new StringBuilder();

        @NotNull String getText() {
            return text.toString();
        }

        @Override
        public void list(@NotNull Function1<? super PresentationTreeBuilder, Unit> builder) {
            builder.invoke(this);
        }

        @Override
        public void collapsibleList(@NotNull CollapseState state,
                                    @NotNull Function1<? super CollapsiblePresentationTreeBuilder, Unit> expandedState,
                                    @NotNull Function1<? super CollapsiblePresentationTreeBuilder, Unit> collapsedState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void text(@NotNull String text, @Nullable InlayActionData actionData) {
            this.text.append(text);
        }

        @Override
        public void clickHandlerScope(@NotNull InlayActionData actionData,
                                      @NotNull Function1<? super PresentationTreeBuilder, Unit> builder) {
            builder.invoke(this);
        }
    }
}

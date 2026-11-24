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
package com.redhat.devtools.lsp4ij.features.inlayhint;

import com.intellij.codeInsight.hints.declarative.CollapseState;
import com.intellij.codeInsight.hints.declarative.CollapsiblePresentationTreeBuilder;
import com.intellij.codeInsight.hints.declarative.HintColorKind;
import com.intellij.codeInsight.hints.declarative.HintFontSize;
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
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An inlay hint has the padding its language server asks for with {@code paddingLeft} and {@code paddingRight}: a space
 * before or after its label, which is not part of a clickable label part, while its format stays the one of a hint
 * without padding. A hint without padding shows only its label.
 */
public class InlayHintPaddingTest extends LSPCodeInsightFixtureTestCase {

    private static final String TEXT = "{#user name=value}";
    // before "value" in TEXT
    private static final Position HINT_POSITION = new Position(0, 12);
    private static final int HINT_OFFSET = 12;

    private static final HintFormat TYPE_HINT_FORMAT = HintFormat.Companion.getDefault()
            .withFontSize(HintFontSize.ABitSmallerThanInEditor);
    private static final HintFormat PARAMETER_HINT_FORMAT = TYPE_HINT_FORMAT.withColorKind(HintColorKind.Parameter);

    public InlayHintPaddingTest() {
        super("*.foo");
    }

    public void testAHintWithoutPaddingShowsOnlyItsLabel() throws Exception {
        assertEquals(List.of(new Inlay(HINT_OFFSET, List.of(text(": String")), TYPE_HINT_FORMAT)),
                collect(hint(null, null)));
    }

    public void testAHintWhosePaddingIsFalseShowsOnlyItsLabel() throws Exception {
        assertEquals(List.of(new Inlay(HINT_OFFSET, List.of(text(": String")), TYPE_HINT_FORMAT)),
                collect(hint(false, false)));
    }

    public void testAHintWithPaddingLeftHasASpaceBeforeItsLabel() throws Exception {
        assertEquals(List.of(new Inlay(HINT_OFFSET, List.of(text(" "), text(": String")), TYPE_HINT_FORMAT)),
                collect(hint(true, null)));
    }

    public void testAHintWithPaddingRightHasASpaceAfterItsLabel() throws Exception {
        assertEquals(List.of(new Inlay(HINT_OFFSET, List.of(text(": String"), text(" ")), TYPE_HINT_FORMAT)),
                collect(hint(false, true)));
    }

    public void testAHintWithPaddingOnBothSidesHasASpaceOnEachSide() throws Exception {
        assertEquals(List.of(new Inlay(HINT_OFFSET, List.of(text(" "), text(": String"), text(" ")), TYPE_HINT_FORMAT)),
                collect(hint(true, true)));
    }

    public void testAParameterHintWithPaddingKeepsTheParameterFormat() throws Exception {
        var hint = new InlayHint(HINT_POSITION, Either.forLeft("name:"));
        hint.setKind(InlayHintKind.Parameter);
        hint.setPaddingRight(true);
        assertEquals(List.of(new Inlay(HINT_OFFSET, List.of(text("name:"), text(" ")), PARAMETER_HINT_FORMAT)),
                collect(hint));
    }

    public void testTheSpacesOfAHintWithPartsAreAroundAllItsPartsAndNotClickable() throws Exception {
        var clickablePart = new InlayHintLabelPart("String");
        clickablePart.setCommand(new Command("Show String", "show.type"));
        var hint = new InlayHint(HINT_POSITION, Either.forRight(List.of(new InlayHintLabelPart(": "), clickablePart)));
        hint.setPaddingLeft(true);
        hint.setPaddingRight(true);
        assertEquals(List.of(new Inlay(HINT_OFFSET,
                        List.of(text(" "), text(": "), clickableText("String"), text(" ")),
                        TYPE_HINT_FORMAT)),
                collect(hint));
    }

    private static InlayHint hint(@Nullable Boolean paddingLeft, @Nullable Boolean paddingRight) {
        var hint = new InlayHint(HINT_POSITION, Either.forLeft(": String"));
        hint.setPaddingLeft(paddingLeft);
        hint.setPaddingRight(paddingRight);
        return hint;
    }

    private static Segment text(@NotNull String text) {
        return new Segment(text, false);
    }

    private static Segment clickableText(@NotNull String text) {
        return new Segment(text, true);
    }

    /**
     * Opens a file with a language server which gives the given inlay hint, and collects the inlays of the file.
     *
     * @param hint the inlay hint the language server gives.
     * @return the inlays LSP4IJ adds for it.
     */
    private List<Inlay> collect(@NotNull InlayHint hint) throws Exception {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setInlayHintProvider(true);
            return capabilities;
        });
        MockLanguageServer.INSTANCE.getTextDocumentService().setMockInlayHints(List.of(hint));
        var file = myFixture.configureByText("main.foo", TEXT);
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS);

        var collector = (OwnBypassCollector) new LSPInlayHintsProvider().createCollector(file, myFixture.getEditor());
        assertNotNull(collector);
        var sink = new InlaySink();
        // IntelliJ collects the inlay hints off the EDT, in a read action
        ApplicationManager.getApplication()
                .executeOnPooledThread(() -> ReadAction.run(() -> collector.collectHintsForFile(file, sink)))
                .get(30, TimeUnit.SECONDS);
        return sink.inlays;
    }

    private record Segment(@NotNull String text, boolean clickable) {
    }

    private record Inlay(int offset, @NotNull List<Segment> segments, @NotNull HintFormat format) {
    }

    /**
     * Keeps the offset, the text segments and the format of the inlays which are added.
     */
    private static final class InlaySink implements InlayTreeSink {

        private final List<Inlay> inlays = new ArrayList<>();

        @Override
        public void addPresentation(@NotNull InlayPosition position,
                                    @Nullable List<InlayPayload> payloads,
                                    @Nullable String tooltip,
                                    @NotNull HintFormat hintFormat,
                                    @NotNull Function1<? super PresentationTreeBuilder, Unit> builder) {
            var segments = new SegmentBuilder();
            builder.invoke(segments);
            inlays.add(new Inlay(((InlineInlayPosition) position).getOffset(), segments.segments, hintFormat));
        }

        @Override
        public void whenOptionEnabled(@NotNull String optionId, @NotNull Function0<Unit> block) {
            block.invoke();
        }
    }

    /**
     * Keeps the text segments of an inlay, and whether each is clickable.
     */
    private static final class SegmentBuilder implements PresentationTreeBuilder {

        private final List<Segment> segments = new ArrayList<>();

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
            segments.add(new Segment(text, actionData != null));
        }

        @Override
        public void clickHandlerScope(@NotNull InlayActionData actionData,
                                      @NotNull Function1<? super PresentationTreeBuilder, Unit> builder) {
            builder.invoke(this);
        }
    }
}

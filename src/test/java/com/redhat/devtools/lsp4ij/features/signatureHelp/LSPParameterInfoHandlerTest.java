/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.signatureHelp;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.internal.capabilities.ClientCapabilitiesFactory;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple;

import java.awt.Color;
import java.util.List;

/** Exercises the parameter popup renderer with optional server metadata. */
public class LSPParameterInfoHandlerTest extends BasePlatformTestCase {

    public void testMissingParametersShowTheSignatureLabel() {
        var signature = new SignatureInformation("read(value: Int): Int");
        signature.setParameters(null);
        var ui = render(signature, 0, false);
        assertEquals(signature.getLabel(), ui.text);
        assertEquals(-1, ui.start);
        assertEquals(-1, ui.end);
    }

    public void testMissingParametersShowTheEscapedLabelAsASingleParameter() {
        var signature = new SignatureInformation("first(items: List<T>): T");
        signature.setParameters(null);
        assertEquals("first(items: List&lt;T&gt;): T", render(signature, 0, true).text);
    }

    public void testEmptyParametersStillSayThereAreNone() {
        var signature = new SignatureInformation("read(): Int");
        signature.setParameters(List.of());
        assertEquals(CodeInsightBundle.message("parameter.info.no.parameters"), render(signature, 0, false).text);
    }

    public void testCurrentParameterIsHighlighted() {
        assertEquals(" second", render(signature(), 1, false).highlight());
    }

    public void testParameterLabelOffsetsUseSignatureText() {
        var signature = signature();
        var first = new ParameterInformation();
        first.setLabel(Either.forRight(Tuple.two(5, 10)));
        var second = new ParameterInformation();
        second.setLabel(Either.forRight(Tuple.two(12, 18)));
        signature.setParameters(List.of(first, second));
        assertEquals(" second", render(signature, 1, false).highlight());
    }

    public void testSingleParameterPresentationIncludesTheParameter() {
        assertEquals("<b>second</b>", render(signature(), 1, true).text);
    }

    public void testSingleParameterPresentationEscapesTheParameter() {
        var signature = new SignatureInformation("first(items: List<T>): T");
        signature.setParameters(List.of(new ParameterInformation("items: List<T>")));
        assertEquals("<b>items: List&lt;T&gt;</b>", render(signature, 0, true).text);
    }

    public void testSingleParameterPresentationShowsOnlyTheCurrentParameter() {
        assertEquals("<b>first</b>", render(signature(), 0, true).text);
    }

    public void testSingleParameterPresentationUsesLabelOffsets() {
        var signature = new SignatureInformation("first(items: List<T>, count: Int): T");
        var items = new ParameterInformation();
        items.setLabel(Either.forRight(Tuple.two(6, 20)));
        var count = new ParameterInformation();
        count.setLabel(Either.forRight(Tuple.two(22, 32)));
        signature.setParameters(List.of(items, count));
        assertEquals("<b>items: List&lt;T&gt;</b>", render(signature, 0, true).text);
    }

    public void testCurrentParameterOutsideTheSignatureDefaultsToTheFirst() {
        assertEquals(" first", render(signature(), 5, false).highlight());
        assertEquals("<b>first</b>", render(signature(), 5, true).text);
    }

    public void testTheClientAdvertisesASignaturesOwnActiveParameter() {
        assertEquals(Boolean.TRUE, ClientCapabilitiesFactory.create(null).getTextDocument()
                .getSignatureHelp().getSignatureInformation().getActiveParameterSupport());
    }

    public void testASignaturesOwnActiveParameterIsHighlighted() {
        var signature = signature();
        signature.setActiveParameter(1);
        // LSP: if provided, it is used in place of the signature help's active parameter
        assertEquals(" second", render(signature, 0, false).highlight());
    }

    public void testTheLatestSignatureHelpDescribesTheShownOverload() {
        // The popup keeps the signatures of the reply that opened it; a retrigger's reply describes them now
        var retriggered = new SignatureInformation("read(first, second)");
        retriggered.setParameters(List.of(new ParameterInformation("first: Int"), new ParameterInformation("second: String")));
        retriggered.setActiveParameter(1);
        var ui = render(signature(), 0, false, owner(new SignatureHelp(List.of(retriggered), 0, 0)));
        assertEquals(" first: Int,  second: String", ui.text);
        assertEquals(" second: String", ui.highlight());
    }

    public void testWithoutAnOverloadOfTheSameLabelTheShownSignatureIsRendered() {
        var other = new SignatureInformation("write(value)");
        other.setParameters(List.of(new ParameterInformation("value")));
        var ui = render(signature(), 1, false, owner(new SignatureHelp(List.of(other), 0, 0)));
        assertEquals(" first,  second", ui.text);
        assertEquals(" second", ui.highlight());
    }

    private LSPSignatureHelperPsiElement owner(SignatureHelp signatureHelp) {
        var owner = new LSPSignatureHelperPsiElement(myFixture.configureByText("call.txt", ""), new TextRange(0, 0));
        owner.setActiveSignatureHelp(signatureHelp);
        return owner;
    }

    private static SignatureInformation signature() {
        var signature = new SignatureInformation("read(first, second)");
        signature.setParameters(List.of(new ParameterInformation("first"), new ParameterInformation("second")));
        return signature;
    }

    private static Presentation render(SignatureInformation signature, int index, boolean single) {
        return render(signature, index, single, null);
    }

    private static Presentation render(SignatureInformation signature, int index, boolean single, PsiElement owner) {
        var ui = new Presentation(index, single, owner);
        new LSPParameterInfoHandler().updateUI(signature, ui);
        return ui;
    }

    private static final class Presentation implements ParameterInfoUIContext {
        private final int index;
        private final boolean single;
        private final PsiElement owner;
        private String text;
        private int start = -1;
        private int end = -1;

        private Presentation(int index, boolean single, PsiElement owner) {
            this.index = index;
            this.single = single;
            this.owner = owner;
        }

        private String highlight() {
            return text.substring(start, end);
        }

        @Override
        public String setupUIComponentPresentation(String text, int start, int end, boolean disabled,
                                                    boolean strikeout, boolean disabledBeforeHighlight, Color color) {
            this.text = text;
            this.start = start;
            this.end = end;
            return text;
        }

        @Override public void setupRawUIComponentPresentation(String text) { this.text = text; }
        @Override public boolean isUIComponentEnabled() { return true; }
        @Override public void setUIComponentEnabled(boolean enabled) { }
        @Override public int getCurrentParameterIndex() { return index; }
        @Override public PsiElement getParameterOwner() { return owner; }
        @Override public boolean isSingleOverload() { return true; }
        @Override public boolean isSingleParameterInfo() { return single; }
        @Override public Color getDefaultParameterColor() { return Color.BLACK; }
    }
}

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.signatureHelp;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
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
import java.lang.reflect.Proxy;
import java.util.List;

/** Exercises parameter popup rendering and overload refreshes. */
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

    public void testGrowingOverloadsReplaceTheControllerWithoutWritingPastItsRows() {
        checkChangedOverloads(List.of(signature()), List.of(signature(), otherSignature()));
    }

    public void testShrinkingOverloadsRemoveOldRows() {
        checkChangedOverloads(List.of(signature(), otherSignature()), List.of(otherSignature()));
    }

    public void testReorderingOverloadsReplacesTheirRows() {
        checkChangedOverloads(List.of(signature(), otherSignature()), List.of(otherSignature(), signature()));
    }

    public void testDifferentOverloadWithTheSameRowCountReplacesTheRow() {
        checkChangedOverloads(List.of(signature()), List.of(otherSignature()));
    }

    public void testUnchangedOverloadsUpdateTheActiveRowWithoutReplacingTheController() {
        var popup = new Popup(List.of(signature(), otherSignature()));
        var reply = new SignatureHelp(List.of(signature(), otherSignature()), 1, 1);
        popup.update(reply);
        assertFalse(popup.enabled[0]);
        assertTrue(popup.enabled[1]);
        assertEquals(1, popup.parameter);
        UIUtil.dispatchAllInvocationEvents();
        assertSame(popup.controller, popup.current());
        assertSame(reply, popup.owner.getActiveSignatureHelp());
    }

    public void testInvalidActiveSignatureDoesNotDisableEveryRow() {
        var popup = new Popup(List.of(signature()));
        popup.update(new SignatureHelp(List.of(signature()), 4, 0));
        assertTrue(popup.enabled[0]);
    }

    public void testDuplicateLabelsWithNewParametersReplaceTheRows() {
        var withoutParameters = new SignatureInformation(signature().getLabel());
        checkChangedOverloads(List.of(withoutParameters, signature()), List.of(signature(), signature()));
    }

    public void testDuplicateLabelsWithChangedActiveParametersReplaceTheRows() {
        var before = signature();
        before.setActiveParameter(0);
        var after = signature();
        after.setActiveParameter(1);
        checkChangedOverloads(List.of(before, signature()), List.of(after, signature()));
    }

    public void testUnchangedDuplicateLabelsKeepTheController() {
        var popup = new Popup(List.of(signature(), signature()));
        popup.update(new SignatureHelp(List.of(signature(), signature()), 1, 1));
        UIUtil.dispatchAllInvocationEvents();
        assertSame(popup.controller, popup.current());
        assertFalse(popup.enabled[0]);
        assertTrue(popup.enabled[1]);
    }

    public void testUniqueLabelUsesNewParametersWithoutReplacingTheController() {
        var withoutParameters = new SignatureInformation(signature().getLabel());
        var popup = new Popup(List.of(withoutParameters));
        popup.update(new SignatureHelp(List.of(signature()), 0, 1));
        UIUtil.dispatchAllInvocationEvents();
        assertSame(popup.controller, popup.current());
        var ui = render(withoutParameters, -1, false, popup.owner);
        assertEquals(" first,  second", ui.text);
        assertEquals(" second", ui.highlight());
    }

    public void testRecreatedPopupUsesTheLatestGlobalActiveParameter() {
        var shown = signature();
        var ui = render(shown, -1, false, owner(new SignatureHelp(List.of(signature()), 0, 1)));
        assertEquals(" second", ui.highlight());
    }

    public void testQueuedRefreshDoesNotReopenADismissedPopup() {
        var popup = new Popup(List.of(signature()));
        popup.update(new SignatureHelp(List.of(signature(), otherSignature()), 1, 0));
        Disposer.dispose(popup.controller);
        UIUtil.dispatchAllInvocationEvents();
        assertNull(popup.current());
    }

    public void testQueuedRefreshDoesNotReplaceANewerPopup() {
        var popup = new Popup(List.of(signature()));
        popup.update(new SignatureHelp(List.of(signature(), otherSignature()), 1, 0));
        Disposer.dispose(popup.controller);
        var newer = popup.create(List.of(otherSignature()));
        UIUtil.dispatchAllInvocationEvents();
        assertSame(newer, popup.current());
    }

    public void testQueuedRefreshIsDiscardedAfterAnEdit() {
        var popup = new Popup(List.of(signature()));
        popup.update(new SignatureHelp(List.of(signature(), otherSignature()), 1, 0));
        WriteCommandAction.runWriteCommandAction(getProject(), () -> myFixture.getEditor().getDocument().insertString(0, " "));
        UIUtil.dispatchAllInvocationEvents();
        assertFalse(Disposer.isDisposed(popup.controller));
    }

    public void testQueuedRefreshIsDiscardedAfterTheCaretMoves() {
        var popup = new Popup(List.of(signature()));
        popup.update(new SignatureHelp(List.of(signature(), otherSignature()), 1, 0));
        myFixture.getEditor().getCaretModel().moveToOffset(1);
        UIUtil.dispatchAllInvocationEvents();
        assertSame(popup.controller, popup.current());
    }

    public void testOnlyTheLatestQueuedReplyReplacesThePopup() {
        var popup = new Popup(List.of(signature()));
        popup.update(new SignatureHelp(List.of(signature(), otherSignature()), 0, 0));
        var latest = new SignatureHelp(List.of(otherSignature()), 0, 0);
        popup.update(latest);
        UIUtil.dispatchAllInvocationEvents();
        assertEquals(List.of(otherSignature()), List.of(popup.current().getObjects()));
        assertSame(latest, popup.owner.getActiveSignatureHelp());
    }

    public void testEmptyReplyCancelsAPendingOverloadRefresh() {
        var popup = new Popup(List.of(signature()));
        popup.update(new SignatureHelp(List.of(signature(), otherSignature()), 0, 0));
        popup.update(new SignatureHelp(List.of(), null, null));
        assertTrue(popup.removed);
        UIUtil.dispatchAllInvocationEvents();
        assertFalse(Disposer.isDisposed(popup.controller));
    }

    private void checkChangedOverloads(List<SignatureInformation> before, List<SignatureInformation> after) {
        var popup = new Popup(before);
        var reply = new SignatureHelp(after, after.size() - 1, 1);
        popup.update(reply);
        assertEquals("No writes to the obsolete update context", 0, popup.enableWrites);
        UIUtil.dispatchAllInvocationEvents();
        assertTrue(Disposer.isDisposed(popup.controller));
        assertNotNull(popup.current());
        assertEquals(after, List.of(popup.current().getObjects()));
        assertSame(reply, popup.owner.getActiveSignatureHelp());
    }

    private static SignatureInformation otherSignature() {
        var signature = new SignatureInformation("read(value)");
        signature.setParameters(List.of(new ParameterInformation("value")));
        return signature;
    }

    /**
     * Models a real popup with the update context's original, fixed row count.
     * The proxy deliberately keeps its enabled-state array at that size so
     * stale row writes fail exactly as they do in IntelliJ.
     */
    private final class Popup {
        private final LSPSignatureHelperPsiElement owner;
        private final LSPParameterInfoHandler handler = new LSPParameterInfoHandler() {
            // The response under test is supplied explicitly; automatic controller
            // refreshes must not start an unrelated language server in this fixture.
            @Override public void updateParameterInfo(LSPSignatureHelperPsiElement element, UpdateParameterInfoContext context) { }
        };
        private final ParameterInfoControllerBase controller;
        private final UpdateParameterInfoContext context;
        private final boolean[] enabled;
        private int enableWrites;
        private int parameter = -1;
        private boolean removed;

        private Popup(List<SignatureInformation> signatures) {
            var file = myFixture.configureByText("call.txt", "read(<caret>)");
            owner = new LSPSignatureHelperPsiElement(file, new TextRange(5, 5));
            owner.setActiveSignatureHelp(new SignatureHelp(signatures, 0, 0));
            controller = create(signatures);
            Object[] shown = controller.getObjects();
            enabled = new boolean[shown.length];
            context = (UpdateParameterInfoContext) Proxy.newProxyInstance(UpdateParameterInfoContext.class.getClassLoader(),
                    new Class<?>[]{UpdateParameterInfoContext.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getProject" -> getProject();
                        case "getEditor" -> myFixture.getEditor();
                        case "getFile" -> file;
                        case "getOffset" -> 5;
                        case "getParameterListStart" -> 5;
                        case "getParameterOwner" -> owner;
                        case "getObjectsToView" -> shown;
                        case "setUIComponentEnabled" -> {
                            enabled[(int) args[0]] = (boolean) args[1];
                            enableWrites++;
                            yield null;
                        }
                        case "setCurrentParameter" -> { parameter = (int) args[0]; yield null; }
                        case "removeHint" -> { removed = true; yield null; }
                        default -> throw new AssertionError("Unexpected context operation: " + method.getName());
                    });
        }

        private ParameterInfoControllerBase create(List<SignatureInformation> signatures) {
            return ParameterInfoControllerBase.createParameterInfoController(getProject(), myFixture.getEditor(), 5,
                    signatures.toArray(), null, owner, handler, true, false);
        }

        private void update(SignatureHelp reply) { handler.updateParameterInfo(owner, context, reply); }
        private ParameterInfoControllerBase current() {
            return ParameterInfoControllerBase.findControllerAtOffset(myFixture.getEditor(), 5);
        }
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

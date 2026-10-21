/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.signatureHelp;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.awaitWithCheckCanceled;

/**
 * LSP implementation of {@link ParameterInfoHandler} to support
 * <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_signatureHelp>LSP 'textDocument/signatureHelp'</a>.
 * <p>
 * Some copy/paste from <a href="https://github.com/JetBrains/intellij-community/blob/4fe13f3bee1568a78facb3e4a0d851fa77adeb1a/java/java-impl/src/com/intellij/codeInsight/hint/api/impls/MethodParameterInfoHandler.java#L694-L785">MethodParameterInfoHandler.java</a> has been done.
 */
public class LSPParameterInfoHandler implements ParameterInfoHandler<LSPSignatureHelperPsiElement, SignatureInformation> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPParameterInfoHandler.class);

    // Methods called when the parameter hint popup appears.

    @Override
    public @Nullable LSPSignatureHelperPsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        return toLSPSignatureHelperPsiElement(context);
    }

    @Override
    public void showParameterInfo(@NotNull LSPSignatureHelperPsiElement psiElement, @NotNull CreateParameterInfoContext context) {
        // 1. Collect signature help asynchronously
        // Here the popup hint is not shown, the signature help is invoked
        SignatureHelpParams params = toSignatureHelpParams(context, SignatureHelpTriggerKind.Invoked);
        LSPSignatureHelpSupport signatureHelpSupport = LSPFileSupport.getSupport(psiElement.getContainingFile()).getSignatureHelpSupport();
        signatureHelpSupport.cancel();
        CompletableFuture<SignatureHelp> future = signatureHelpSupport.getSignatureHelp(params);
        // 2. Show the popup hint when signature help is ready.
        future.thenAccept(signatureHelp -> {
            if (signatureHelp != null) {
                // Store the current signature help in the Psi element
                psiElement.setActiveSignatureHelp(signatureHelp);
                // There is a signature help, display it
                List<SignatureInformation> signatures = signatureHelp.getSignatures();
                if (signatures != null && !signatures.isEmpty()) {
                    context.setItemsToShow(signatures.toArray(new SignatureInformation[0]));
                    context.showHint(psiElement, context.getOffset(), this);
                }
            }
        });
    }

    // Methods called when the parameter hint popup is already displayed.

    @Override
    public @Nullable LSPSignatureHelperPsiElement findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        PsiElement psiElement = context.getParameterOwner();
        if (psiElement == null || psiElement.getContainingFile() != context.getFile() || !(psiElement instanceof LSPSignatureHelperPsiElement psiSignatureHelperElement)) {
            // Should never occur...
            return toLSPSignatureHelperPsiElement(context);
        }
        // Update offset
        psiSignatureHelperElement.setTextRange(getTextRange(context));
        return psiSignatureHelperElement;
    }

    @Override
    public void updateParameterInfo(@NotNull LSPSignatureHelperPsiElement psiElement, @NotNull UpdateParameterInfoContext context) {
        // Here the popup hint not displayed, the signature help
        // is triggered by the cursor moving or by the document content changing.
        SignatureHelpParams params = toSignatureHelpParams(context, SignatureHelpTriggerKind.ContentChange);
        params.getContext().setIsRetrigger(true);
        params.getContext().setActiveSignatureHelp(psiElement.getActiveSignatureHelp());

        LSPSignatureHelpSupport signatureHelpSupport = LSPFileSupport.getSupport(psiElement.getContainingFile()).getSignatureHelpSupport();
        signatureHelpSupport.cancel();
        CompletableFuture<SignatureHelp> future = signatureHelpSupport.getSignatureHelp(params);

        try {
            awaitWithCheckCanceled(future);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (CancellationException e) {
            // ignore
        }

        if (isDoneNormally(future)) {
            updateParameterInfo(psiElement, context, future.getNow(null));
        }
    }

    void updateParameterInfo(@NotNull LSPSignatureHelperPsiElement psiElement,
                             @NotNull UpdateParameterInfoContext context,
                             @Nullable SignatureHelp signatureHelp) {
        // Also invalidate any queued refresh when the latest reply has no signatures.
        psiElement.setActiveSignatureHelp(signatureHelp);
        List<SignatureInformation> signatures = signatureHelp != null ? signatureHelp.getSignatures() : null;
        if (signatures == null || signatures.isEmpty()) {
            context.removeHint();
            return;
        }

        Object[] shown = context.getObjectsToView();
        if (!hasSameOverloads(shown, signatures)) {
            refreshOverloads(psiElement, context, signatureHelp, shown);
            return;
        }

        int activeSignature = signatureHelp.getActiveSignature() != null ? signatureHelp.getActiveSignature() : -1;
        for (int i = 0; i < shown.length; i++) {
            context.setUIComponentEnabled(i, activeSignature < 0 || activeSignature >= shown.length || activeSignature == i);
        }
        Integer activeParameter = signatureHelp.getActiveParameter();
        context.setCurrentParameter(activeParameter != null ? activeParameter : 0);
    }

    private static boolean hasSameOverloads(Object[] shown, List<SignatureInformation> signatures) {
        if (shown.length != signatures.size()) {
            return false;
        }
        for (int i = 0; i < shown.length; i++) {
            if (!(shown[i] instanceof SignatureInformation signature)
                    || !Objects.equals(signature.getLabel(), signatures.get(i).getLabel())) {
                return false;
            }
        }
        return true;
    }

    private void refreshOverloads(LSPSignatureHelperPsiElement owner, UpdateParameterInfoContext context,
                                  SignatureHelp signatureHelp, Object[] shown) {
        var project = context.getProject();
        var editor = context.getEditor();
        int startOffset = context.getParameterListStart();
        int caretOffset = editor.getCaretModel().getOffset();
        long documentStamp = editor.getDocument().getModificationStamp();
        // An update context has a fixed-size enabled array. Replacing descriptors on its
        // controller would still break applyUIChanges(), even if this handler avoided it.
        // Dispose that controller on the EDT and create one with the new row count.
        ApplicationManager.getApplication().invokeLater(() -> ReadAction.run(() -> {
            if (editor.isDisposed() || !owner.isValid()
                    || editor.getCaretModel().getOffset() != caretOffset
                    || editor.getDocument().getModificationStamp() != documentStamp
                    || owner.getActiveSignatureHelp() != signatureHelp) {
                return;
            }
            var controller = ParameterInfoControllerBase.findControllerAtOffset(editor, startOffset);
            // Do not resurrect a dismissed popup or replace a newer popup at this offset.
            if (controller == null || controller.getObjects() != shown || context.getParameterOwner() != owner
                    || controller.isPreservedOnHintHidden() && !controller.isHintShown(true)) {
                return;
            }
            boolean preserved = controller.isPreservedOnHintHidden();
            Disposer.dispose(controller);
            var replacement = ParameterInfoControllerBase.createParameterInfoController(project, editor, startOffset,
                    signatureHelp.getSignatures().toArray(new SignatureInformation[0]), null, owner, this, true, false);
            replacement.setPreservedOnHintHidden(preserved);
        }), ModalityState.stateForComponent(editor.getComponent()), project.getDisposed());
    }

    @Override
    public void updateUI(SignatureInformation shownSignature, @NotNull ParameterInfoUIContext context) {
        SignatureInformation signatureInformation = getCurrentSignature(shownSignature, context);
        // LSP: a signature's own active parameter, if provided, is used in place of the signature help's
        Integer activeParameter = signatureInformation.getActiveParameter();
        if (activeParameter == null && context.getParameterOwner() instanceof LSPSignatureHelperPsiElement owner) {
            SignatureHelp signatureHelp = owner.getActiveSignatureHelp();
            if (signatureHelp != null && signatureHelp.getSignatures() != null
                    && signatureHelp.getSignatures().contains(signatureInformation)) {
                activeParameter = signatureHelp.getActiveParameter();
            }
        }
        int currentParameter = activeParameter != null ? activeParameter : context.getCurrentParameterIndex();
        List<ParameterInformation> parameters = signatureInformation.getParameters();
        int numParams = parameters != null ? parameters.size() : 0;
        if (currentParameter < 0 || currentParameter >= numParams) {
            // LSP: an active parameter outside the signature's parameters defaults to the first one.
            currentParameter = 0;
        }

        StringBuilder html = new StringBuilder(numParams * 8);
        int highlightStartOffset = -1;
        int highlightEndOffset = -1;
        if (numParams > 0) {

            for (int j = 0; j < numParams; j++) {
                if (context.isSingleParameterInfo() && j != currentParameter) {
                    continue;
                }

                int startOffset = html.length();
                String name = getParameterName(signatureInformation, j);
                if (context.isSingleParameterInfo()) {
                    html.append("<b>");
                    html.append(StringUtil.escapeXmlEntities(String.valueOf(name)));
                    html.append("</b>");
                } else {
                    html.append(" ");
                    html.append(name);
                }

                int endOffset = html.length();

                // A single parameter is shown alone, without the separator that follows it in the list.
                if (!context.isSingleParameterInfo() && j < numParams - 1) {
                    html.append(", ");
                }

                if (context.isUIComponentEnabled() &&
                        (j == currentParameter || j == numParams - 1 && false /*param.isVarArgs() */ && currentParameter >= numParams)) {
                    highlightStartOffset = startOffset;
                    highlightEndOffset = endOffset;
                }
            }
        } else if (parameters == null) {
            // Parameters are optional in LSP. Without them, the signature label is all there is to
            // show: the signature may still take arguments, and there is no range to highlight.
            String label = signatureInformation.getLabel();
            html.append(context.isSingleParameterInfo() ? StringUtil.escapeXmlEntities(label) : label);
        } else {
            html.append(CodeInsightBundle.message("parameter.info.no.parameters"));
        }

        String text = html.toString();
        if (context.isSingleParameterInfo()) {
            context.setupRawUIComponentPresentation(text);
        } else {
            context.setupUIComponentPresentation(
                    text,
                    highlightStartOffset,
                    highlightEndOffset,
                    !context.isUIComponentEnabled(),
                    false,
                    false,
                    context.getDefaultParameterColor()
            );
        }
    }

    /**
     * Returns the overload as the latest signature help describes it. The popup keeps the signatures of
     * the reply that opened it, while each later reply is stored in the parameter owner; the overload is
     * the one there with the same label. Without exactly one, the shown signature is returned.
     */
    private static @NotNull SignatureInformation getCurrentSignature(@NotNull SignatureInformation shown,
                                                                     @NotNull ParameterInfoUIContext context) {
        if (!(context.getParameterOwner() instanceof LSPSignatureHelperPsiElement owner)) {
            return shown;
        }
        SignatureHelp signatureHelp = owner.getActiveSignatureHelp();
        List<SignatureInformation> signatures = signatureHelp != null ? signatureHelp.getSignatures() : null;
        if (signatures == null) {
            return shown;
        }
        SignatureInformation current = null;
        for (SignatureInformation signature : signatures) {
            if (Objects.equals(signature.getLabel(), shown.getLabel())) {
                if (current != null) {
                    return shown;
                }
                current = signature;
            }
        }
        return current != null ? current : shown;
    }

    private static @Nullable String getParameterName(SignatureInformation signatureInformation, int index) {
        ParameterInformation parameterInformation = signatureInformation.getParameters().get(index);
        var label = parameterInformation.getLabel();
        if (label.isLeft()) {
            // simple label
            return label.getLeft();
        }
        if (!label.isRight()) {
            return null;
        }
        // processing label offsets
        var indexes = label.getRight();
        int start = indexes.getFirst();
        int end = indexes.getSecond();
        return signatureInformation.getLabel().substring(start, end);
    }

    @NotNull
    private static LSPSignatureHelperPsiElement toLSPSignatureHelperPsiElement(ParameterInfoContext context) {
        TextRange textRange = getTextRange(context);
        return new LSPSignatureHelperPsiElement(context.getFile(), textRange);
    }

    @NotNull
    private static TextRange getTextRange(ParameterInfoContext context) {
        int offset = context.getOffset();
        return new TextRange(offset, offset);
    }

    /**
     * Create signature help parameters from the given IntelliJ parameter info context.
     *
     * @param context the IntelliJ parameter info context.
     * @return signature help parameters from the given IntelliJ parameter info context.
     */
    @NotNull
    private static SignatureHelpParams toSignatureHelpParams(@NotNull ParameterInfoContext context, @NotNull SignatureHelpTriggerKind kind) {
        TextDocumentIdentifier identifier = new TextDocumentIdentifier();
        Position position = LSPIJUtils.toPosition(context.getOffset(), context.getEditor().getDocument());
        SignatureHelpContext signatureHelpContext = new SignatureHelpContext();
        signatureHelpContext.setTriggerKind(kind);
        return new SignatureHelpParams(identifier, position, signatureHelpContext);
    }
}

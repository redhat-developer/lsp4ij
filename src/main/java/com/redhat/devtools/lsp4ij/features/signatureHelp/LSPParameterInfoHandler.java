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
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

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
        boolean removeHint = false;

        // Here the popup hint not displayed, the signature help
        // is triggered by the cursor moving or by the document content changing.
        SignatureHelpParams params = toSignatureHelpParams(context, SignatureHelpTriggerKind.ContentChange);
        params.getContext().setIsRetrigger(true);
        params.getContext().setActiveSignatureHelp(psiElement.getActiveSignatureHelp());

        LSPSignatureHelpSupport signatureHelpSupport = LSPFileSupport.getSupport(psiElement.getContainingFile()).getSignatureHelpSupport();
        signatureHelpSupport.cancel();
        CompletableFuture<SignatureHelp> future = signatureHelpSupport.getSignatureHelp(params);

        try {
            // Wait upon the future is finished and stop the wait if there are some ProcessCanceledException.
            waitUntilDone(future, psiElement.getContainingFile());
        } catch (CancellationException | ProcessCanceledException e) {
            // Do nothing
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/signatureHelp' request", e);
        }

        if (isDoneNormally(future)) {
            SignatureHelp activeSignatureHelp = future.getNow(null);
            List<SignatureInformation> signatures = activeSignatureHelp != null ? activeSignatureHelp.getSignatures() : null;
            if (signatures == null || signatures.isEmpty()) {
                // No signature helper found, close the popup hint
                removeHint = true;
            } else {
                // A signature help is found

                // Update enable/disable of signature
                int activateSignature = activeSignatureHelp.getActiveSignature() != null ? activeSignatureHelp.getActiveSignature() : -1;
                for (int i = 0; i < signatures.size(); i++) {
                    context.setUIComponentEnabled(i, activateSignature == -1 || activateSignature == i);
                }
                // Update the IntelliJ parameter info context to highlight the proper parameter
                Integer activeParameter = activeSignatureHelp.getActiveParameter();
                context.setCurrentParameter(activeParameter != null ? activeParameter : 0);
                // Update the Psi element with the active signature help
                psiElement.setActiveSignatureHelp(activeSignatureHelp);
            }
        }
        if (removeHint) {
            // Close the popup hint
            context.removeHint();
        }
    }

    @Override
    public void updateUI(SignatureInformation signatureInformation, @NotNull ParameterInfoUIContext context) {
        int currentParameter = context.getCurrentParameterIndex();
        List<ParameterInformation> parameters = signatureInformation.getParameters();
        int numParams = parameters.size();

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
                }

                if (!context.isSingleParameterInfo()) {
                    html.append(" ");
                    html.append(name);
                }
                if (context.isSingleParameterInfo()) {
                    html.append("</b>");
                }

                int endOffset = html.length();

                if (j < numParams - 1) {
                    html.append(", ");
                }

                if (context.isUIComponentEnabled() &&
                        (j == currentParameter || j == numParams - 1 && false /*param.isVarArgs() */ && currentParameter >= numParams)) {
                    highlightStartOffset = startOffset;
                    highlightEndOffset = endOffset;
                }
            }
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
        TextDocumentIdentifier identifier = LSPIJUtils.toTextDocumentIdentifier(context.getFile().getVirtualFile());
        Position position = LSPIJUtils.toPosition(context.getOffset(), context.getEditor().getDocument());
        SignatureHelpContext signatureHelpContext = new SignatureHelpContext();
        signatureHelpContext.setTriggerKind(kind);
        return new SignatureHelpParams(identifier, position, signatureHelpContext);
    }
}

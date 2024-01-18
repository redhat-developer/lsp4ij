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
package com.redhat.devtools.lsp4ij.operations.signatureHelp;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.*;

/**
 * LSP implementation of {@link ParameterInfoHandler} to support
 * <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_signatureHelp>LSP 'textDocument/signatureHelp'</a>.
 */
public class LSPParameterInfoHandler implements ParameterInfoHandler<LSPSignatureHelperPsiElement, SignatureInformation> {

    // Methods called when the parameter hint popup appears.

    @Override
    public @Nullable LSPSignatureHelperPsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        return toLSPSignatureHelperPsiElement(context);
    }

    @Override
    public void showParameterInfo(@NotNull LSPSignatureHelperPsiElement psiElement, @NotNull CreateParameterInfoContext context) {
        CancellationSupport cancellationSupport = new CancellationSupport();
        try {
            ProgressManager.checkCanceled();

            // 1. Collect signature help asynchronously
            // Here the popup hint is not shown, the signature help is invoked
            SignatureHelpParams params = toSignatureHelpParams(context, SignatureHelpTriggerKind.Invoked);
            BlockingDeque<Pair<SignatureHelp, LanguageServer>> signatureHelps = new LinkedBlockingDeque<>();
            CompletableFuture<Void> future = collectSignatureHelp(context, params, signatureHelps, cancellationSupport);
            // 2. Show the popup hint when signature help is ready.
            future.thenAccept(a -> {
                if (!signatureHelps.isEmpty()) {
                    // Get the first signature help
                    SignatureHelp signatureHelp = signatureHelps.getFirst().getFirst();
                    if (signatureHelp != null) {
                        // Store the current signature help in the Psi element
                        psiElement.setActiveSignatureHelp(signatureHelp);
                        // There is a signature help, display it
                        List<SignatureInformation> signatures = signatureHelp.getSignatures();
                        context.setItemsToShow(signatures.toArray(new SignatureInformation[0]));
                        context.showHint(psiElement, context.getOffset(), this);
                    }
                }
            });
        } catch (CancellationException e) {
            // Do nothing
        } catch (ProcessCanceledException e) {
            // Cancel all LSP requests
            cancellationSupport.cancel();
        }
    }

    // Methods called when the parameter hint popup is already displayed.

    @Override
    public @Nullable LSPSignatureHelperPsiElement findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        PsiElement psiElement = (LSPSignatureHelperPsiElement) context.getParameterOwner();
        if (psiElement == null || psiElement.getContainingFile() != context.getFile() || !(psiElement instanceof LSPSignatureHelperPsiElement)) {
            // Should never occur...
            return toLSPSignatureHelperPsiElement(context);
        }
        // Update offset
        LSPSignatureHelperPsiElement psiSignatureHelperElement = (LSPSignatureHelperPsiElement) psiElement;
        psiSignatureHelperElement.setTextRange(getTextRange(context));
        return psiSignatureHelperElement;
    }

    @Override
    public void updateParameterInfo(@NotNull LSPSignatureHelperPsiElement psiElement, @NotNull UpdateParameterInfoContext context) {
        boolean removeHint = false;
        CancellationSupport cancellationSupport = new CancellationSupport();
        try {
            ProgressManager.checkCanceled();

            // Here the popup hint not displayed, the signature help
            // is triggered by the cursor moving or by the document content changing.
            SignatureHelpParams params = toSignatureHelpParams(context, SignatureHelpTriggerKind.ContentChange);
            params.getContext().setIsRetrigger(true);
            params.getContext().setActiveSignatureHelp(psiElement.getActiveSignatureHelp());

            BlockingDeque<Pair<SignatureHelp, LanguageServer>> signatureHelps = new LinkedBlockingDeque<>();
            CompletableFuture<Void> future = collectSignatureHelp(context, params, signatureHelps, cancellationSupport);

            ProgressManager.checkCanceled();
            while (!future.isDone() || !signatureHelps.isEmpty()) {
                ProgressManager.checkCanceled();
                Pair<SignatureHelp, LanguageServer> pair = signatureHelps.poll(25, TimeUnit.MILLISECONDS);
                if (pair != null) {
                    SignatureHelp activeSignatureHelp = pair.getFirst();
                    List<SignatureInformation> signatures = activeSignatureHelp != null ? activeSignatureHelp.getSignatures() : null;
                    if (signatures == null || signatures.isEmpty()) {
                        // No signature helper found, close the popup hint
                        removeHint = true;
                    } else {
                        // A signature help is found

                        // Update enable/disable of signature
                        int activateSignature = activeSignatureHelp.getActiveSignature() != null ? activeSignatureHelp.getActiveSignature() : -1;
                        for (int i = 0; i < signatures.size(); i++) {
                            context
                                    .setUIComponentEnabled(i, activateSignature == -1 || activateSignature == i);
                        }
                        // Update the IntelliJ parameter info context to highlight the proper parameter
                        Integer activeParameter = activeSignatureHelp.getActiveParameter();
                        context.setCurrentParameter(activeParameter != null ? activeParameter : 0);
                        // Update the Psi element with the active signature help
                        psiElement.setActiveSignatureHelp(activeSignatureHelp);
                    }
                }
            }
        } catch (ProcessCanceledException e) {
            // Cancel all LSP requests and close the popup hint
            cancellationSupport.cancel();
            removeHint = true;
        } catch (Exception e) {
            // Close the popup hint
            removeHint = true;
        }
        if (removeHint) {
            // Close the popup hint
            context.removeHint();
        }
    }

    @Override
    public void updateUI(SignatureInformation signatureInformation, @NotNull ParameterInfoUIContext context) {
        CodeInsightSettings settings = CodeInsightSettings.getInstance();

        int currentParameter = context.getCurrentParameterIndex();

        List<ParameterInformation> parameters = signatureInformation.getParameters();
        int numParams = parameters.size();

        StringBuilder html = new StringBuilder(numParams * 8);
        int highlightStartOffset = -1;
        int highlightEndOffset = -1;
        if (numParams > 0) {

            for (int j = 0; j < numParams; j++) {
                if (context.isSingleParameterInfo() && j != currentParameter) continue;

                int startOffset = html.length();
                String name = getParameterName(signatureInformation, j);
                if (context.isSingleParameterInfo()) html.append("<b>");

                if (!context.isSingleParameterInfo()) {
                    html.append(" ");
                    html.append(name);
                }
                if (context.isSingleParameterInfo()) html.append("</b>");

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
                    false, //method.isDeprecated() && !context.isSingleParameterInfo() && !context.isSingleOverload(),
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

    private CompletableFuture<Void> collectSignatureHelp(@NotNull ParameterInfoContext context,
                                                         @NotNull SignatureHelpParams params,
                                                         @NotNull BlockingDeque<Pair<SignatureHelp, LanguageServer>> pairs,
                                                         @NotNull CancellationSupport cancellationSupport) {
        return LanguageServiceAccessor.getInstance(context.getProject())
                .getLanguageServers(context.getFile().getVirtualFile(), LSPParameterInfoHandler::isSignatureHelpProvider)
                .thenComposeAsync(languageServers -> cancellationSupport.execute(
                        CompletableFuture.allOf(languageServers.stream()
                                .map(languageServer ->
                                        cancellationSupport.execute(languageServer.getServer().getTextDocumentService().signatureHelp(params))
                                                .thenAcceptAsync(signatureHelp -> {
                                                    // textDocument/signatureHelp may return null
                                                        pairs.add(new Pair<>(signatureHelp, languageServer.getServer()));
                                                }))
                                .toArray(CompletableFuture[]::new))));
    }

    /**
     * Returns true if the language server can support signature help and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support signature help and false otherwise.
     */
    public static boolean isSignatureHelpProvider(ServerCapabilities serverCapabilities) {
        return serverCapabilities != null && serverCapabilities.getSignatureHelpProvider() != null;
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

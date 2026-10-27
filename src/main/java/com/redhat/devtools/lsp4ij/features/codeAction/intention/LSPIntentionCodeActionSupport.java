/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction.intention;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.features.codeAction.CodeActionData;
import com.redhat.devtools.lsp4ij.features.codeAction.LSPLazyCodeActionProvider;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.awaitWithCheckCanceled;

/**
 * LSP code action support which loads and caches code actions by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/codeAction' requests</li>
 * </ul>
 */
public class LSPIntentionCodeActionSupport extends AbstractLSPDocumentFeatureSupport<CodeActionParams, List<CodeActionData>> implements LSPLazyCodeActionProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPIntentionCodeActionSupport.class);

    private CodeActionParams previousParams;

    public LSPIntentionCodeActionSupport(@NotNull PsiFile file) {
        super(file);
    }

    /**
     * Get or load code actions for the given parameters.
     * If the parameters have changed from the previous call (e.g., caret moved),
     * cancel the in-flight request and start a new one.
     *
     * @param params the code action parameters (range, context, etc.)
     * @return a future that will complete with the list of code actions
     */
    public CompletableFuture<List<CodeActionData>> getCodeActions(@NotNull CodeActionParams params) {
        if (previousParams != null && hasChanged(params)) {
            // The caret or selection has changed, cancel the previous loading of textDocument/codeAction
            // to avoid wasting resources on obsolete results
            super.cancel();
        }
        previousParams = params;
        return super.getFeatureData(params);
    }

    /**
     * Check if the code action parameters have changed since the last request.
     * Used to determine if we should cancel the previous request and start a new one.
     *
     * @param params the new parameters to check
     * @return true if parameters changed (or this is the first request), false otherwise
     */
    public boolean hasChanged(CodeActionParams params) {
        return previousParams == null || !previousParams.equals(params);
    }

    @Override
    protected CompletableFuture<List<CodeActionData>> doLoad(@NotNull CodeActionParams params,
                                                             @NotNull CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getCodeActions(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<CodeActionData>> getCodeActions(@NotNull PsiFile file,
                                                                                   @NotNull CodeActionParams params,
                                                                                   @NotNull CancellationSupport cancellationSupport) {

        return getLanguageServers(file,
                f -> f.getCodeActionFeature().isIntentionActionsEnabled(file),
                f -> f.getCodeActionFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have code action capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Collect list of textDocument/codeAction future for each language servers
                    List<CompletableFuture<List<CodeActionData>>> codeActionPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getCodeActionsFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/codeAction future in one future which return the list of code actions
                    return CompletableFutures.mergeInOneFuture(codeActionPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<CodeActionData>> getCodeActionsFor(@NotNull CodeActionParams params,
                                                                             @NotNull PsiFile file,
                                                                             @NotNull LanguageServerItem languageServer,
                                                                             @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .codeAction(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_CODE_ACTION)
                .thenApplyAsync(codeActions -> {
                    if (codeActions == null) {
                        // textDocument/codeAction may return null
                        return Collections.emptyList();
                    }
                    return codeActions.stream()
                            .filter(LSPIntentionCodeActionSupport::isValidCodeAction)
                            .map(codeAction -> new CodeActionData(codeAction, languageServer))
                            .toList();
                });
    }

    /**
     * Returns the LSP CodeAction for the given index and null otherwise.
     *
     * @param index the code action index.
     * @return the LSP CodeAction for the given index and null otherwise.
     */
    @Override
    public @Nullable Either<CodeActionData, Boolean> getCodeActionAt(int index) {
        var future = super.getValidLSPFuture();
        // This method is called from ShowIntentionsPass inside a ReadAction.
        // Use awaitWithCheckCanceled to cooperate with IntelliJ's threading model:
        // when a WriteAction is requested, the progress indicator is cancelled
        // → ProcessCanceledException → ReadAction released → WriteAction proceeds.
        // IntelliJ reschedules the pass automatically.
        // See https://github.com/redhat-developer/lsp4ij/issues/1648
        if (future != null) {
            try {
                awaitWithCheckCanceled(future);
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (CancellationException e) {
                return null;
            }
        }

        if (isDoneNormally(future)) {
            List<CodeActionData> codeActions = future.getNow(null);
            if (codeActions != null) {
                if (codeActions.size() > index) {
                    return Either.forLeft(codeActions.get(index));
                }
                return Either.forRight(Boolean.FALSE);
            }
        }
        return null;
    }

    /**
     * Returns true if the given code action is valid and false otherwise.
     *
     * @param codeAction the code action.
     * @return true if the given code action is valid and false otherwise.
     */
    private static boolean isValidCodeAction(@Nullable Either<org.eclipse.lsp4j.Command, org.eclipse.lsp4j.CodeAction> codeAction) {
        if (codeAction == null) {
            return false;
        }
        if (codeAction.isRight()) {
            // For IJ intention, 'quickfix' code action must be filtered.
            return !CodeActionKind.QuickFix.equals(codeAction.getRight().getKind());
        }
        return true;
    }
}
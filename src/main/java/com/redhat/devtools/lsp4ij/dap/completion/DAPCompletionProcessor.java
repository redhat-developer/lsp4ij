/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.evaluation.DAPExpressionCodeFragment;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.debug.CompletionItem;
import org.eclipse.lsp4j.debug.CompletionsResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.features.completion.LSPCompletionContributor.getCurrentWord;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * Debug Adapter Protocol (DAP) completion processor
 * used in the "Evaluate expression" editor.
 */
public class DAPCompletionProcessor extends CompletionContributor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DAPCompletionProcessor.class);

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        PsiFile psiFile = parameters.getOriginalFile();
        if (psiFile instanceof DAPExpressionCodeFragment dapFile) {
            DAPStackFrame stackFrame = dapFile.getCurrentDapStackFrame();
            if (stackFrame == null) {
                return;
            }
            DAPClient client = stackFrame.getClient();
            if (!client.isSupportsCompletionsRequest()) {
                // DAP server doesn't support "completion" request.
                return;
            }
            Editor editor = parameters.getEditor();
            Document document = editor.getDocument();
            int offset = parameters.getOffset();

            Position position = LSPIJUtils.toPosition(offset, document);
            String text = document.getText();

            int frameId = stackFrame.getFrameId();

            CompletableFuture<CompletionsResponse> future =
                    client.completion(text, position.getLine() + 1, position.getCharacter() + 1, frameId);

            try {
                // Wait until the future is finished and stop the wait if there are some ProcessCanceledException.
                waitUntilDone(future, psiFile);
            } catch (
                    ProcessCanceledException ignore) {//Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
                //TODO delete block when minimum required version is 2024.2
                return;
            } catch (CancellationException ignore) {
                return;
            } catch (ExecutionException e) {
                LOGGER.error("Error while consuming DAP 'completion' request", e);
                return;
            }

            ProgressManager.checkCanceled();

            if (CompletableFutures.isDoneNormally(future)) {
                CompletionsResponse completionsResponse = future.getNow(null);
                if (completionsResponse != null) {
                    CompletionItem[] items = completionsResponse.getTargets();
                    if (items != null) {

                        // TODO customize that
                        boolean useContextAwareSorting = false;

                        PrefixMatcher prefixMatcher = useContextAwareSorting ? result.getPrefixMatcher() : null;
                        String currentWord = useContextAwareSorting ? getCurrentWord(parameters) : null;
                        // TODO customize that
                        boolean caseSensitive = false; //clientFeatures.isCaseSensitive(originalFile);
                        Arrays.sort(items, new DAPCompletionItemComparator(prefixMatcher, currentWord, caseSensitive));
                        int size = items.length;

                        // Items now sorted by priority, low index == high priority
                        for (int i = 0; i < size; i++) {
                            var item = items[i];
                            ProgressManager.checkCanceled();

                            LookupElement lookupElement = createLookupElement(parameters, item);
                            if (lookupElement != null) {
                                int priority = size - i;
                                var prioritizedLookupItem = PrioritizedLookupElement.withPriority(new DAPLookupElementDecorator(lookupElement, item), priority);
                                result.addElement(prioritizedLookupItem);
                            }
                        }
                    }
                }
            }
        }
    }

    @Nullable
    private LookupElement createLookupElement(@NotNull CompletionParameters parameters,
                                              @NotNull CompletionItem item) {
        String label = item.getLabel();
        String insertText = label;
        if (StringUtils.isNotBlank(item.getText())) {
            insertText = item.getText();
        }
        insertText = StringUtilRt.convertLineSeparators(insertText);
        if (StringUtils.isBlank(insertText)) {
            return null;
        }

        LookupElementBuilder builder = LookupElementBuilder.create(item, insertText);
        if (!Objects.equals(label, insertText)) {
            builder = builder.withLookupString(label);
        }
        return builder;
    }

}

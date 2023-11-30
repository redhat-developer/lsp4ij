/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerItem;
import com.redhat.devtools.intellij.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.intellij.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.intellij.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * LSP completion contributor.
 */
public class LSPCompletionContributor extends CompletionContributor {
    private static final Logger LOGGER = LoggerFactory.getLogger(LSPCompletionContributor.class);

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiFile psiFile = parameters.getOriginalFile();
        VirtualFile file = psiFile.getVirtualFile();
        if (file == null) {
            return;
        }

        Editor editor = parameters.getEditor();
        Document document = editor.getDocument();
        Project project = psiFile.getProject();
        int offset = parameters.getOffset();
        URI uri = LSPIJUtils.toUri(file);

        ProgressManager.checkCanceled();

        final CancellationSupport cancellationSupport = new CancellationSupport();
        try {
            CompletableFuture<List<LanguageServerItem>> completionLanguageServersFuture = initiateLanguageServers(file, project);
            cancellationSupport.execute(completionLanguageServersFuture);
            ProgressManager.checkCanceled();

            /*
             process the responses out of the completable loop as it may cause deadlock if user is typing
             more characters as toProposals will require as read lock that this thread already have and
             async processing is occuring on a separate thread.
             */
            CompletionParams params = LSPIJUtils.toCompletionParams(uri, offset, document);
            BlockingDeque<Pair<Either<List<CompletionItem>, CompletionList>, LanguageServerItem>> proposals = new LinkedBlockingDeque<>();

            CompletableFuture<Void> future = completionLanguageServersFuture
                    .thenComposeAsync(languageServers -> cancellationSupport.execute(
                            CompletableFuture.allOf(languageServers.stream()
                                    .map(languageServer ->
                                            cancellationSupport.execute(languageServer.getServer().getTextDocumentService().completion(params))
                                                    .thenAcceptAsync(completion -> proposals.add(new Pair<>(completion, languageServer))))
                                    .toArray(CompletableFuture[]::new))));

            ProgressManager.checkCanceled();
            while (!future.isDone() || !proposals.isEmpty()) {
                ProgressManager.checkCanceled();
                Pair<Either<List<CompletionItem>, CompletionList>, LanguageServerItem> pair = proposals.poll(25, TimeUnit.MILLISECONDS);
                if (pair != null) {
                    Either<List<CompletionItem>, CompletionList> completion = pair.getFirst();
                    if (completion != null) {
                        CompletionPrefix completionPrefix = new CompletionPrefix(offset, document);
                        addCompletionItems(psiFile, editor, completionPrefix, pair.getFirst(), pair.getSecond(), result, cancellationSupport);
                    }
                }
            }
        } catch (ProcessCanceledException cancellation) {
            cancellationSupport.cancel();
            throw cancellation;
        } catch (RuntimeException | InterruptedException e) {
            LOGGER.warn(e.getLocalizedMessage(), e);
            result.addElement(createErrorProposal(offset, e));
        }
    }

    private void addCompletionItems(PsiFile file, Editor editor, CompletionPrefix completionPrefix, Either<List<CompletionItem>,
            CompletionList> completion, LanguageServerItem languageServer, @NotNull CompletionResultSet result, CancellationSupport cancellationSupport) {
        CompletionItemDefaults itemDefaults = null;
        List<CompletionItem> items = null;
        if (completion.isLeft()) {
            items = completion.getLeft();
        } else {
            CompletionList completionList = completion.getRight();
            itemDefaults = completionList.getItemDefaults();
            items = completionList.getItems();
        }
        for (var item : items) {
            if (StringUtils.isBlank(item.getLabel())) {
                // Invalid completion Item, ignore it
                continue;
            }
            cancellationSupport.checkCanceled();
            // Create lookup item
            var lookupItem = createLookupItem(file, editor, completionPrefix.getCompletionOffset(), item, itemDefaults, languageServer);
            // Group it by using completion item kind
            var groupedLookupItem = PrioritizedLookupElement.withGrouping(lookupItem, item.getKind().getValue());
            // Compute the prefix
            String prefix = completionPrefix.getPrefixFor(lookupItem.getTextEditRange(), item);
            if (prefix != null) {
                // Add the IJ completion item (lookup item) by using the computed prefix
                result.withPrefixMatcher(prefix)
                        .caseInsensitive() // set case-insensitive to search Java class which starts with upper case
                        .addElement(groupedLookupItem);
            } else {
                // Should happen rarely, only when text edit is for multi-lines or if completion is triggered outside the text edit range.
                // Add the IJ completion item (lookup item) which will use the IJ prefix
                result.addElement(groupedLookupItem);
            }
        }
    }

    private static LSPCompletionProposal createLookupItem(PsiFile file, Editor editor, int offset,
                                                          CompletionItem item,
                                                          CompletionItemDefaults itemDefaults, LanguageServerItem languageServer) {
        // Update text edit range with item defaults if needed
        updateWithItemDefaults(item, itemDefaults);
        return new LSPCompletionProposal(file, editor, offset, item, languageServer);
    }

    private static void updateWithItemDefaults(CompletionItem item, CompletionItemDefaults itemDefaults) {
        if (itemDefaults == null) {
            return;
        }
        String itemText = item.getTextEditText();
        if (itemDefaults.getEditRange() != null && itemText != null) {
            if (itemDefaults.getEditRange().isLeft()) {
                Range defaultRange = itemDefaults.getEditRange().getLeft();
                if (defaultRange != null) {
                    item.setTextEdit(Either.forLeft(new TextEdit(defaultRange, itemText)));
                }
            } else {
                InsertReplaceRange defaultInsertReplaceRange = itemDefaults.getEditRange().getRight();
                if (defaultInsertReplaceRange != null) {
                    item.setTextEdit(Either.forRight(new InsertReplaceEdit(itemText, defaultInsertReplaceRange.getInsert(), defaultInsertReplaceRange.getReplace())));
                }
            }
        }
    }


    private static LookupElement createErrorProposal(int offset, Exception ex) {
        return LookupElementBuilder.create("Error while computing completion", "");
    }

    private static CompletableFuture<List<LanguageServerItem>> initiateLanguageServers(@NotNull VirtualFile file, @NotNull Project project) {
        return LanguageServiceAccessor.getInstance(project).getLanguageServers(file,
                capabilities -> {
                    CompletionOptions provider = capabilities.getCompletionProvider();
                    if (provider != null) {
                        return true;
                    }
                    return false;
                });
    }
}
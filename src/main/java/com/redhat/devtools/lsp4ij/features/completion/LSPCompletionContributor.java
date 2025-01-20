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
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionFeature;
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionProposal;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

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

        if (ProjectIndexingManager.canExecuteLSPFeature(psiFile) != ExecuteLSPFeatureStatus.NOW) {
            return;
        }

        Editor editor = parameters.getEditor();
        Document document = editor.getDocument();
        int offset = parameters.getOffset();

        // Get LSP completion items from cache or create them
        boolean autoPopup = parameters.isAutoPopup();
        LSPCompletionParams params = new LSPCompletionParams(LSPIJUtils.toTextDocumentIdentifier(file),
                LSPIJUtils.toPosition(offset, document),
                offset,
                autoPopup ? getCompletionChar(offset, document) : null,
                autoPopup);
        CompletableFuture<List<CompletionData>> future = LSPFileSupport.getSupport(psiFile)
                .getCompletionSupport()
                .getCompletions(params);
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
            LOGGER.error("Error while consuming LSP 'textDocument/completion' request", e);
            return;
        }

        ProgressManager.checkCanceled();
        if (isDoneNormally(future)) {
            List<CompletionData> data = future.getNow(Collections.emptyList());
            if (!data.isEmpty()) {
                CompletionPrefix completionPrefix = new CompletionPrefix(offset, document);
                for (var item : data) {
                    ProgressManager.checkCanceled();
                    addCompletionItems(parameters, completionPrefix, item.completion(), item.languageServer(), result);
                }
            }
        }
    }

    private void addCompletionItems(@NotNull CompletionParameters parameters,
                                    @NotNull CompletionPrefix completionPrefix,
                                    @NotNull Either<List<CompletionItem>, CompletionList> completion,
                                    @NotNull LanguageServerItem languageServer,
                                    @NotNull CompletionResultSet result) {
        CompletionItemDefaults itemDefaults = null;
        List<CompletionItem> items = new ArrayList<>();
        if (completion.isLeft()) {
            items.addAll(completion.getLeft());
        } else {
            CompletionList completionList = completion.getRight();
            itemDefaults = completionList.getItemDefaults();
            items.addAll(completionList.getItems());
        }

        PsiFile originalFile = parameters.getOriginalFile();
        LSPClientFeatures clientFeatures = languageServer.getClientFeatures();

        // Sort the completions as appropriate based on client configuration
        boolean useContextAwareSorting = clientFeatures.getCompletionFeature().useContextAwareSorting(originalFile);
        if (useContextAwareSorting) {
            // Cache-buster for prefix changes since that can affect ordering
            result.restartCompletionOnPrefixChange(StandardPatterns.string().longerThan(0));
        }
        PrefixMatcher prefixMatcher = useContextAwareSorting ? result.getPrefixMatcher() : null;
        String currentWord = useContextAwareSorting ? getCurrentWord(parameters) : null;
        boolean caseSensitive = clientFeatures.isCaseSensitive(originalFile);
        items.sort(new LSPCompletionItemComparator(prefixMatcher, currentWord, caseSensitive));
        int size = items.size();

        Set<String> addedLookupStrings = new HashSet<>();
        var completionFeature = clientFeatures.getCompletionFeature();
        LSPCompletionFeature.LSPCompletionContext context = new LSPCompletionFeature.LSPCompletionContext(parameters, languageServer);
        // Items now sorted by priority, low index == high priority
        for (int i = 0; i < size; i++) {
            var item = items.get(i);
            ProgressManager.checkCanceled();
            // Update text edit range, commitCharacters, ... with item defaults if needed
            updateWithItemDefaults(item, itemDefaults);
            // Create lookup item
            LookupElement lookupItem = completionFeature.createLookupElement(item, context);
            if (lookupItem != null) {
                completionFeature.addLookupItem(context, completionPrefix, result, lookupItem, size - i, item);
                ContainerUtil.addIfNotNull(addedLookupStrings, lookupItem.getLookupString());
            }
        }

        // If completions were added from LSP and this is auto-completion or the explicit completion was triggered an
        // odd number of times (acts as a toggle), add completions from other all other contributors except for the
        // word completion contributor.
        // Note that the word completion contributor only really kicks in when LSP completions are added in the context
        // of a TextMate file (see TextMateCompletionContributor), but that's going to be very common for LSP usage
        // since it's often adding (pseudo-)language support when not already present in the host JetBrains IDE.
        if ((size > 0) && ((parameters.getInvocationCount() == 0) || ((parameters.getInvocationCount() % 2) > 0))) {
            // Don't allow automatic execution of the remaining completion contributors; we'll execute them ourselves below
            result.stopHere();

            // Try to disable word completion by temporarily setting FORBID_WORD_COMPLETION=true on the completion process.
            // Note that this will not work in some older IDE versions where WordCompletionContributor.addWordCompletionVariants()
            // did not yet check the value of FORBID_WORD_COMPLETION
            CompletionProcess completionProcess = parameters.getProcess();
            UserDataHolder userDataHolder = completionProcess instanceof UserDataHolder ? (UserDataHolder) completionProcess : null;
            boolean originalForbidWordCompletion = false;
            if (userDataHolder != null) {
                originalForbidWordCompletion = userDataHolder.getUserData(BaseCompletionService.FORBID_WORD_COMPLETION) == Boolean.TRUE;
                userDataHolder.putUserData(BaseCompletionService.FORBID_WORD_COMPLETION, true);
            }
            try {
                if (userDataHolder != null) {
                    // If we disabled word completion, just run all remaining contributors
                    result.runRemainingContributors(parameters, true);
                } else {
                    // If not, run all remaining contributors and only add results with distinct lookup strings
                    result.runRemainingContributors(parameters, completionResult -> {
                        LookupElement lookupElement = completionResult.getLookupElement();
                        if (lookupElement != null) {
                            String lookupString = lookupElement.getLookupString();
                            if (!addedLookupStrings.contains(lookupString)) {
                                result.consume(lookupElement);
                                addedLookupStrings.add(lookupString);
                            }
                        }
                    });
                }
            } finally {
                // If appropriate, re-enable word completion
                if (userDataHolder != null) {
                    userDataHolder.putUserData(BaseCompletionService.FORBID_WORD_COMPLETION, originalForbidWordCompletion);
                }
            }
        }
    }

    @Nullable
    public static String getCurrentWord(@NotNull CompletionParameters parameters) {
        PsiFile originalFile = parameters.getOriginalFile();
        VirtualFile virtualFile = originalFile.getVirtualFile();
        Document document = virtualFile != null ? LSPIJUtils.getDocument(virtualFile) : null;
        if (document != null) {
            int offset = parameters.getOffset();
            TextRange wordTextRange = LSPIJUtils.getWordRangeAt(document, originalFile, offset);
            if (wordTextRange != null) {
                CharSequence documentChars = document.getCharsSequence();
                CharSequence wordChars = documentChars.subSequence(wordTextRange.getStartOffset(), wordTextRange.getEndOffset());
                return wordChars.toString();
            }
        }
        return null;
    }

    protected void updateWithItemDefaults(@NotNull CompletionItem item,
                                          @Nullable CompletionItemDefaults itemDefaults) {
        if (itemDefaults == null) {
            return;
        }
        // Update TextEdit with CompletionItemDefaults if needed
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
        // Update data with CompletionItemDefaults if needed
        if (item.getData() == null) {
            item.setData(itemDefaults.getData());
        }
        // Update InsertTextFormat with CompletionItemDefaults if needed
        if (item.getInsertTextFormat() == null) {
            item.setInsertTextFormat(itemDefaults.getInsertTextFormat());
        }
        // Update InsertTextMode with CompletionItemDefaults if needed
        if (item.getInsertTextMode() == null) {
            item.setInsertTextMode(itemDefaults.getInsertTextMode());
        }
        // Update CommitCharacters with CompletionItemDefaults if needed
        if (item.getCommitCharacters() == null) {
            item.setCommitCharacters(itemDefaults.getCommitCharacters());
        }
    }

    /**
     * LSP lookup listener to track the selected completion item
     * and resolve if needed the LSP completionItem to get the detail
     * only for the selected completion item.
     */
    public static class LSPLookupManagerListener implements LookupManagerListener {

        @Override
        public void activeLookupChanged(@Nullable Lookup oldLookup, @Nullable Lookup newLookup) {
            if (newLookup == null) {
                return;
            }
            newLookup.addLookupListener(new LookupListener() {
                @Override
                public void currentItemChanged(@NotNull LookupEvent event) {
                    var item = event.getItem();
                    if (item == null) {
                        return;
                    }
                    if (item.getObject() instanceof LSPCompletionProposal lspCompletionProposal) {
                        // It is an LSP completion proposal
                        if (newLookup instanceof LookupImpl lookupImpl && lspCompletionProposal.needToResolveCompletionDetail()) {
                            // The LSP completion item requires to resolve completionItem to get the detail
                            // Refresh the lookup item
                            lookupImpl.scheduleItemUpdate(item);
                        }
                    }
                }
            });
        }
    }

    private static final String getCompletionChar(int offset, Document document) {
        if (offset > 0 && offset <= document.getTextLength()) {
            return String.valueOf(document.getCharsSequence().charAt(offset - 1));
        }
        return null;
    }
}
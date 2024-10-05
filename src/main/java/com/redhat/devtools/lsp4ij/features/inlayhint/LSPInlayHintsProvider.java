/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.inlayhint;

import com.intellij.codeInsight.hints.InlayHintsSink;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.SequencePresentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPInlayHintsProvider;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP textDocument/inlayHint support.
 */
public class LSPInlayHintsProvider extends AbstractLSPInlayHintsProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPInlayHintsProvider.class);

    @Override
    protected void doCollect(@NotNull PsiFile psiFile,
                             @NotNull Editor editor,
                             @NotNull PresentationFactory factory,
                             @NotNull InlayHintsSink inlayHintsSink,
                             @NotNull List<CompletableFuture> pendingFutures) {
        // Get LSP inlay hints from cache or create them
        LSPInlayHintsSupport inlayHintSupport = LSPFileSupport.getSupport(psiFile).getInlayHintsSupport();
        Range viewPortRange = getViewPortRange(editor);
        InlayHintParams params = new InlayHintParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()), viewPortRange);
        CompletableFuture<List<InlayHintData>> future = inlayHintSupport.getInlayHints(params);

        try {
            // Wait until the future is finished and stop the wait if there are some ProcessCanceledException.
            waitUntilDone(future, psiFile);
            if (isDoneNormally(future)) {

                // Collect inlay hints
                List<Pair<Integer, InlayHintData>> inlayHints = new ArrayList<>();
                List<InlayHintData> data = future.getNow(Collections.emptyList());
                fillInlayHints(editor.getDocument(), data, inlayHints);

                // Render inlay hints and collect all unfinished inlayHint/resolve futures
                inlayHints.stream()
                        .collect(Collectors.groupingBy(p -> p.first))
                        .forEach((offset, list) ->
                                inlayHintsSink.addInlineElement(
                                        offset,
                                        false,
                                        toPresentation(psiFile, editor, list, factory),
                                        false));
            }
            return;
        } catch (ProcessCanceledException ignore) {//Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
            //TODO delete block when minimum required version is 2024.2
        } catch (CancellationException ignore) {
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/inlayHint' request", e);
        } finally {
            if (!future.isDone()) {
                // the future which collects all textDocument/inlayHint for all servers is not finished
                // add it to the pending futures to refresh again the UI when this future will be finished.
                pendingFutures.add(future);
            }
        }
    }

    private static void fillInlayHints(Document document, List<InlayHintData> data, List<Pair<Integer, InlayHintData>> inlayHints) {
        for (var inlayHintData : data) {
            int offset = LSPIJUtils.toOffset(inlayHintData.inlayHint().getPosition(), document);
            inlayHints.add(Pair.create(offset, inlayHintData));
        }
    }

    @NotNull
    private static Range getViewPortRange(Editor editor) {
        // LSP textDocument/inlayHint request parameter expects to fill the visible view port range.
        // As Intellij inlay hint provider is refreshed just only when editor is opened or editor content changed
        // and not when editor is scrolling, the view port range must be created with full text document offsets.
        Position start = new Position(0, 0);
        Document document = editor.getDocument();
        Position end = LSPIJUtils.toPosition(document.getTextLength(), document);
        return new Range(start, end);
    }

    private InlayPresentation toPresentation(@NotNull PsiFile psiFile,
                                             @NotNull Editor editor,
                                             @NotNull List<Pair<Integer, InlayHintData>> elements,
                                             @NotNull PresentationFactory factory) {
        List<InlayPresentation> presentations = new ArrayList<>();
        elements.forEach(p -> {
            Either<String, List<InlayHintLabelPart>> label = p.second.inlayHint().getLabel();
            if (label.isLeft()) {
                presentations.add(factory.smallText(label.getLeft()));
            } else {
                int index = 0;
                for (InlayHintLabelPart part : label.getRight()) {
                    InlayPresentation text = createInlayPresentation(psiFile, editor, factory, p, index, part);
                    if (part.getTooltip() != null && part.getTooltip().isLeft()) {
                        text = factory.withTooltip(part.getTooltip().getLeft(), text);
                    }
                    presentations.add(text);
                    index++;
                }
            }
        });
        return factory.roundWithBackground(new SequencePresentation(presentations));
    }

    @NotNull
    private InlayPresentation createInlayPresentation(
            PsiFile psiFile,
            Editor editor,
            PresentationFactory factory,
            Pair<Integer, InlayHintData> p,
            int index,
            InlayHintLabelPart part) {
        InlayPresentation text = factory.smallText(part.getValue());
        if (hasCommand(part)) {
            // InlayHintLabelPart defines a Command, create a clickable inlay hint
            int finalIndex = index;
            text = factory.referenceOnHover(text, (event, translated) ->
                    executeCommand(psiFile, editor, p.second.languageServer(), p.second.inlayHint(), finalIndex, event)
            );
        }
        return text;
    }

    private static boolean hasCommand(InlayHintLabelPart part) {
        Command command = part.getCommand();
        return (command != null && command.getCommand() != null && !command.getCommand().isEmpty());
    }

    /**
     * Execute InlayHint command.
     *
     * @param psiFile        the Psi file.
     * @param editor         the editor.
     * @param languageServer the language server.
     * @param inlayHint      the inlay hint.
     * @param index          the inlay part index where the command should be defined.
     * @param event          the Mouse event.
     */
    private void executeCommand(@NotNull PsiFile psiFile,
                                @NotNull Editor editor,
                                @NotNull LanguageServerItem languageServer,
                                @NotNull InlayHint inlayHint,
                                int index,
                                @Nullable MouseEvent event) {
        if (languageServer.getClientFeatures().getInlayHintFeature().isResolveInlayHintSupported(psiFile)) {
            languageServer.getTextDocumentService()
                    .resolveInlayHint(inlayHint)
                    .thenAcceptAsync(resolvedInlayHint -> {
                                if (resolvedInlayHint != null) {
                                    executeCommand(getCommand(resolvedInlayHint, index), psiFile, editor, event, languageServer);
                                }
                            }
                    );
        } else {
            executeCommand(getCommand(inlayHint, index), psiFile, editor, event, languageServer);
        }
    }

    private static @Nullable Command getCommand(@Nullable InlayHint inlayHint, int index) {
        if (inlayHint == null) {
            return null;
        }
        if (inlayHint.getLabel() != null && inlayHint.getLabel().isRight()) {
            List<InlayHintLabelPart> parts = inlayHint.getLabel().getRight();
            if (index < parts.size()) {
                return parts.get(index).getCommand();
            }
        }
        return null;
    }

}

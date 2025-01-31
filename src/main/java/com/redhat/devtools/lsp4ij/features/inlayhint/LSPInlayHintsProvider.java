/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * FalsePattern - port to declarative inlay hint API
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.inlayhint;

import com.intellij.codeInsight.hints.declarative.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP textDocument/inlayHint support.
 */
public class LSPInlayHintsProvider extends AbstractLSPDeclarativeInlayHintsProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPInlayHintsProvider.class);

    public static final String PROVIDER_ID = "LSPInlayHintsProvider";

    @Override
    protected void doCollect(@NotNull PsiFile psiFile,
                             @NotNull Editor editor,
                             @NotNull InlayTreeSink inlayHintsSink,
                             @NotNull List<CompletableFuture<?>> pendingFutures) {
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
                        .forEach((offset, list) -> {
                            var position = new InlineInlayPosition(offset, false, 0);
                            buildInlayHints(psiFile, list, position, inlayHintsSink);
                        });
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

    private void buildInlayHints(@NotNull PsiFile psiFile,
                                 @NotNull List<Pair<Integer, InlayHintData>> elements,
                                 @NotNull InlayPosition position,
                                 @NotNull InlayTreeSink sink) {
        elements.forEach(p -> {
            Either<String, List<InlayHintLabelPart>> label = p.second.inlayHint().getLabel();
            if (label.isLeft()) {
                buildBasicInlayHint(label.getLeft(), position, sink);
            } else {
                buildMultipartInlayHint(psiFile, label.getRight(), p.second, position, sink);
            }
        });
    }

    private void buildBasicInlayHint(@NotNull String label,
                                     @NotNull InlayPosition position,
                                     @NotNull InlayTreeSink sink) {
        sink.addPresentation(position, null, null, true, builder -> {
            builder.text(label, null);
            return null;
        });
    }

    private record InlayHintPartBuildInfo(@Nullable String tooltip,
                                          @NotNull Consumer<PresentationTreeBuilder> build) {}

    private void buildMultipartInlayHint(@NotNull PsiFile psiFile,
                                         @NotNull List<InlayHintLabelPart> parts,
                                         @NotNull InlayHintData hintData,
                                         @NotNull InlayPosition position,
                                         @NotNull InlayTreeSink sink) {
        int index = 0;
        var builds = new ArrayList<Consumer<PresentationTreeBuilder>>();
        var tooltip = new StringBuilder();
        boolean hasTooltip = false;
        for (InlayHintLabelPart part : parts) {
            var info = buildSingleInlayHint(psiFile, hintData, index, part);
            builds.add(info.build);
            if (info.tooltip != null) {
                if (hasTooltip) {
                    tooltip.append('\n');
                }
                tooltip.append(info.tooltip);
                hasTooltip = true;
            }
            index++;
        }
        sink.addPresentation(position, null, tooltip.toString(), true, builder -> {
            for (var build: builds) {
                build.accept(builder);
            }
            return null;
        });
    }

    private InlayHintPartBuildInfo buildSingleInlayHint(
            PsiFile psiFile,
            InlayHintData hintData,
            int index,
            InlayHintLabelPart part) {
        var tooltip = part.getTooltip();
        final String tooltipStr;
        if (tooltip != null && tooltip.isLeft()) {
            tooltipStr = tooltip.getLeft();
        } else {
            tooltipStr = null;
        }
        String text = part.getValue();
        final InlayActionData data;
        if (hasCommand(part)) {
            // InlayHintLabelPart defines a Command, create a clickable inlay hint
            InlayActionPayload payload = LSPDeclarativeInlayActionHandler.createPayload(psiFile.getProject(), editor -> {
                executeCommand(psiFile, editor, hintData.languageServer(), hintData.inlayHint(), index);
            });
            data = new InlayActionData(payload, LSPDeclarativeInlayActionHandler.HANDLER_ID);
        } else {
            data = null;
        }
        return new InlayHintPartBuildInfo(tooltipStr, builder -> {
            builder.text(text, data);
        });
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
     */
    private void executeCommand(@NotNull PsiFile psiFile,
                                @NotNull Editor editor,
                                @NotNull LanguageServerItem languageServer,
                                @NotNull InlayHint inlayHint,
                                int index) {
        if (languageServer.getClientFeatures().getInlayHintFeature().isResolveInlayHintSupported(psiFile)) {
            languageServer.getTextDocumentService()
                    .resolveInlayHint(inlayHint)
                    .thenAcceptAsync(resolvedInlayHint -> {
                                if (resolvedInlayHint != null) {
                                    executeCommand(getCommand(resolvedInlayHint, index), psiFile, editor, null, languageServer);
                                }
                            }
                    );
        } else {
            executeCommand(getCommand(inlayHint, index), psiFile, editor, null, languageServer);
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

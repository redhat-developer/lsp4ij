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
package com.redhat.devtools.lsp4ij.features.color;

import com.intellij.codeInsight.hints.InlayHintsSink;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.SequencePresentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.AbstractLSPInlayHintsProvider;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.DocumentColorParams;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * LSP textDocument/color support.
 */
public class LSPColorProvider extends AbstractLSPInlayHintsProvider {

    @Override
    protected void doCollect(@NotNull PsiFile psiFile,
                             @NotNull Editor editor,
                             @NotNull PresentationFactory factory,
                             @NotNull InlayHintsSink inlayHintsSink,
                             @NotNull List<CompletableFuture> pendingFutures) throws InterruptedException {
        // Get LSP color information from cache or create them
        LSPColorSupport colorSupport = LSPFileSupport.getSupport(psiFile).getColorSupport();
        var params = new DocumentColorParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()));
        CompletableFuture<List<ColorData>> future = colorSupport.getColors(params);

        try {
            List<Pair<Integer, ColorData>> codeLenses = createColorInformation(editor.getDocument(), future);
            // Render color information
            codeLenses.stream()
                    .collect(Collectors.groupingBy(p -> p.first))
                    .forEach((offset, list) ->
                            inlayHintsSink.addInlineElement(
                                    offset,
                                    false,
                                    toPresentation(list, factory))
                    );
        } finally {
            if (!future.isDone()) {
                // the future which collects all textDocument/colorInformation for all servers is not finished
                // add it to the pending futures to refresh again the UI when this future will be finished.
                pendingFutures.add(future);
            }
        }
    }

    @NotNull
    private List<Pair<Integer, ColorData>> createColorInformation(Document document,
                                                                  CompletableFuture<List<ColorData>> future) throws InterruptedException {
        List<Pair<Integer, ColorData>> colorInformation = new ArrayList<>();
        if (future.isDone()) {
            List<ColorData> data = future.getNow(Collections.emptyList());
            fillColor(document, data, colorInformation);
        } else {
            while (!future.isDone()) {
                ProgressManager.checkCanceled();
                try {
                    List<ColorData> data = future.get(25, TimeUnit.MILLISECONDS);
                    fillColor(document, data, colorInformation);
                } catch (TimeoutException | ExecutionException e) {
                    // Do nothing
                }
            }
        }
        return colorInformation;
    }

    private static void fillColor(Document document, List<ColorData> data, List<Pair<Integer, ColorData>> colors) {
        for (var codeLensData : data) {
            int offset = LSPIJUtils.toOffset(codeLensData.color().getRange().getStart(), document);
            colors.add(Pair.create(offset, codeLensData));
        }
    }

    private InlayPresentation toPresentation(@NotNull List<Pair<Integer, ColorData>> elements,
                                             @NotNull PresentationFactory factory) {
        List<InlayPresentation> presentations = new ArrayList<>();
        elements.forEach(p -> {
            Color color = p.second.color().getColor();
            java.awt.Color c = new java.awt.Color((float) color.getRed(), (float) color.getGreen(), (float) color.getBlue(), (float) color.getAlpha());
            presentations.add(new ColorInlayPresentation(c, factory));
        });
        return new SequencePresentation(presentations);
    }

}

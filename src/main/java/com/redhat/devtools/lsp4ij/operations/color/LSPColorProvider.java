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
package com.redhat.devtools.lsp4ij.operations.color;

import com.intellij.codeInsight.hints.InlayHintsSink;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.SequencePresentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.operations.AbstractLSPInlayHintsProvider;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * LSP textDocument/color support.
 */
public class LSPColorProvider extends AbstractLSPInlayHintsProvider {

    private static final Key<CancellationSupport> CANCELLATION_SUPPORT_KEY = new Key<>(LSPColorProvider.class.getName() + "-CancellationSupport");

    public LSPColorProvider() {
        super(CANCELLATION_SUPPORT_KEY);
    }

    @Override
    protected void doCollect(@NotNull VirtualFile file, @NotNull Project project, @NotNull Editor editor, @NotNull PresentationFactory factory, @NotNull InlayHintsSink inlayHintsSink, @NotNull CancellationSupport cancellationSupport) throws InterruptedException {
        Document document = editor.getDocument();
        URI fileUri = LSPIJUtils.toUri(file);
        DocumentColorParams param = new DocumentColorParams(new TextDocumentIdentifier(fileUri.toASCIIString()));
        BlockingDeque<Pair<ColorInformation, LanguageServer>> pairs = new LinkedBlockingDeque<>();

        CompletableFuture<Void> future = collect(project, file, param, pairs, cancellationSupport);
        List<Pair<Integer, Pair<ColorInformation, LanguageServer>>> colorInformations = createColorInformations(document, pairs, future);
        colorInformations.stream()
                .collect(Collectors.groupingBy(p -> p.first))
                .forEach((offset, list) ->
                        inlayHintsSink.addInlineElement(offset, false, toPresentation(editor, list, factory, cancellationSupport), false));
    }

    @NotNull
    private List<Pair<Integer, Pair<ColorInformation, LanguageServer>>> createColorInformations(
            @NotNull Document document,
            BlockingDeque<Pair<ColorInformation, LanguageServer>> pairs,
            CompletableFuture<Void> future)
            throws InterruptedException {
        List<Pair<Integer, Pair<ColorInformation, LanguageServer>>> colorInformations = new ArrayList<>();
        while (!future.isDone() || !pairs.isEmpty()) {
            ProgressManager.checkCanceled();
            Pair<ColorInformation, LanguageServer> pair = pairs.poll(25, TimeUnit.MILLISECONDS);
            if (pair != null) {
                int offset = LSPIJUtils.toOffset(pair.getFirst().getRange().getStart(), document);
                colorInformations.add(Pair.create(offset, pair));
            }
        }
        return colorInformations;
    }

    private CompletableFuture<Void> collect(@NotNull Project project, @NotNull VirtualFile file, DocumentColorParams param, BlockingDeque<Pair<ColorInformation, LanguageServer>> pairs, CancellationSupport cancellationSupport) {
        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, LSPColorProvider::isColorProvider)
                .thenComposeAsync(languageServers -> cancellationSupport.execute(CompletableFuture.allOf(languageServers.stream()
                        .map(languageServer ->
                                cancellationSupport.execute(languageServer.getServer().getTextDocumentService().documentColor(param))
                                        .thenAcceptAsync(colorInformations -> {
                                            // textDocument/color may return null
                                            if (colorInformations != null) {
                                                colorInformations.stream()
                                                        .filter(Objects::nonNull)
                                                        .forEach(colorInformation -> pairs.add(new Pair(colorInformation, languageServer.getServer())));
                                            }
                                        }))
                        .toArray(CompletableFuture[]::new))));
    }

    private InlayPresentation toPresentation(@NotNull Editor editor,
                                             @NotNull List<Pair<Integer, Pair<ColorInformation, LanguageServer>>> elements,
                                             @NotNull PresentationFactory factory,
                                             @NotNull CancellationSupport cancellationSupport) {
        List<InlayPresentation> presentations = new ArrayList<>();
        elements.forEach(p -> {
            cancellationSupport.checkCanceled();
            Color color = p.second.first.getColor();
            java.awt.Color c = new java.awt.Color((float) color.getRed(), (float) color.getGreen(), (float) color.getBlue(), (float)color.getAlpha());
            presentations.add(new ColorInlayPresentation(c, factory));
        });
        return new SequencePresentation(presentations);
    }

    private static boolean isColorProvider(final ServerCapabilities capabilities) {
        return capabilities != null && LSPIJUtils.hasCapability(capabilities.getColorProvider());
    }

}

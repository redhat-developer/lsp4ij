/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.codelens;

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
import com.redhat.devtools.intellij.lsp4ij.AbstractLSPInlayProvider;
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import com.redhat.devtools.intellij.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.intellij.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
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
 * LSP textDocument/codeLens support.
 */
public class LSPCodelensInlayProvider extends AbstractLSPInlayProvider {

    private static final Key<CancellationSupport> CANCELLATION_SUPPORT_KEY = new Key<>(LSPCodelensInlayProvider.class.getName() + "-CancellationSupport");


    public LSPCodelensInlayProvider() {
        super(CANCELLATION_SUPPORT_KEY);
    }

    @Override
    protected void doCollect(@NotNull VirtualFile file, @NotNull Project project, @NotNull Editor editor, @NotNull PresentationFactory factory, @NotNull InlayHintsSink inlayHintsSink, @NotNull CancellationSupport cancellationSupport) throws InterruptedException {
        Document document = editor.getDocument();
        URI fileUri = LSPIJUtils.toUri(file);
        CodeLensParams param = new CodeLensParams(new TextDocumentIdentifier(fileUri.toASCIIString()));
        BlockingDeque<Pair<CodeLens, LanguageServer>> pairs = new LinkedBlockingDeque<>();

        CompletableFuture<Void> future = collect(file, project, param, pairs, cancellationSupport);
        List<Pair<Integer, Pair<CodeLens, LanguageServer>>> codeLenses = createCodeLenses(document, pairs, future, cancellationSupport);
        codeLenses.stream()
                .collect(Collectors.groupingBy(p -> p.first))
                .forEach((offset, list) ->
                        inlayHintsSink.addBlockElement(
                                offset,
                                true,
                                true,
                                0,
                                toPresentation(editor, offset, list, factory, cancellationSupport))
                );
    }

    @NotNull
    private List<Pair<Integer, Pair<CodeLens, LanguageServer>>> createCodeLenses(Document document, BlockingDeque<Pair<CodeLens, LanguageServer>> pairs, CompletableFuture<Void> future, CancellationSupport cancellationSupport) throws InterruptedException {
        List<Pair<Integer, Pair<CodeLens, LanguageServer>>> codelenses = new ArrayList<>();
        while (!future.isDone() || !pairs.isEmpty()) {
            ProgressManager.checkCanceled();
            Pair<CodeLens, LanguageServer> pair = pairs.poll(25, TimeUnit.MILLISECONDS);
            if (pair != null) {
                int offset = LSPIJUtils.toOffset(pair.getFirst().getRange().getStart(), document);
                codelenses.add(Pair.create(offset, pair));
            }
        }
        return codelenses;
    }

    private @NotNull CompletableFuture<Void> collect(@NotNull VirtualFile file, @NotNull Project project, @NotNull CodeLensParams param, @NotNull BlockingDeque<Pair<CodeLens, LanguageServer>> pairs, @NotNull CancellationSupport cancellationSupport) {
        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, capabilities -> capabilities.getCodeLensProvider() != null)
                .thenComposeAsync(languageServers ->
                        cancellationSupport.execute(CompletableFuture.allOf(languageServers.stream()
                                .map(languageServer ->
                                        cancellationSupport.execute(languageServer.getServer().getTextDocumentService().codeLens(param))
                                                .thenAcceptAsync(codeLenses -> {
                                                    // textDocument/codeLens may return null
                                                    if (codeLenses != null) {
                                                        codeLenses.stream()
                                                                .filter(Objects::nonNull)
                                                                .forEach(codeLens -> {
                                                                    if (getCodeLensContent(codeLens) != null) {
                                                                        // The codelens content is filled, display it
                                                                        pairs.add(new Pair(codeLens, languageServer.getServer()));
                                                                    }
                                                                });
                                                    }
                                                }))
                                .toArray(CompletableFuture[]::new))));
    }

    private InlayPresentation toPresentation(
            @NotNull Editor editor,
            int offset,
            @NotNull List<Pair<Integer, Pair<CodeLens, LanguageServer>>> elements,
            @NotNull PresentationFactory factory,
            @NotNull CancellationSupport cancellationSupport) {
        int line = editor.getDocument().getLineNumber(offset);
        int column = offset - editor.getDocument().getLineStartOffset(line);
        List<InlayPresentation> presentations = new ArrayList<>();
        presentations.add(factory.textSpacePlaceholder(column, true));
        elements.forEach(p -> {
            cancellationSupport.checkCanceled();
            CodeLens codeLens = p.second.first;
            LanguageServer languageServer = p.second.second;
            InlayPresentation text = factory.smallText(getCodeLensContent(codeLens));
            if (!hasCommand(codeLens)) {
                // No command, create a simple text inlay hint
                presentations.add(text);
            } else {
                // Codelens defines a Command, create a clickable inlay hint
                InlayPresentation clickableText = factory.referenceOnHover(text, (event, translated) ->
                        executeClientCommand(p.second.second, p.second.first, (Component) event.getSource(), editor.getProject())
                );
                presentations.add(clickableText);
            }
            presentations.add(factory.textSpacePlaceholder(1, true));
        });
        return new SequencePresentation(presentations);
    }

    private void executeClientCommand(LanguageServer languageServer, CodeLens codeLens, Component source, Project project) {
        if (LanguageServiceAccessor.getInstance(project).checkCapability(languageServer, capabilities ->
                Boolean.TRUE.equals(capabilities.getCodeLensProvider().getResolveProvider()))
        ) {
            languageServer.getTextDocumentService().resolveCodeLens(codeLens).thenAcceptAsync(resolvedCodeLens ->
                    executeClientCommand(source, resolvedCodeLens.getCommand())
            );
        } else {
            executeClientCommand(source, codeLens.getCommand());
        }
    }

    private static boolean hasCommand(CodeLens codeLens) {
        Command command = codeLens.getCommand();
        return (command != null && command.getCommand() != null && !command.getCommand().isEmpty());
    }

    private static String getCodeLensContent(CodeLens codeLens) {
        Command command = codeLens.getCommand();
        if (command == null || command.getTitle().isEmpty()) {
            return null;
        }
        return command.getTitle();
    }
}

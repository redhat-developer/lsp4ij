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
package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.features.AbstractLSPFeatureSupport;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * LSP hover support which loads and caches hover by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/hover' requests</li>
 * </ul>
 */
public class LSPHoverSupport extends AbstractLSPFeatureSupport<HoverParams, List<MarkupContent>> {

    private Integer previousOffset;

    public LSPHoverSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<MarkupContent>> getHover(int offset, Document document) {
        if (previousOffset != null && previousOffset != offset) {
            // Cancel previous hover (without setting previousOffset to null)
            cancel();
        }
        previousOffset = offset;
        HoverParams params = LSPIJUtils.toHoverParams(offset, document);
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<MarkupContent>> doLoad(HoverParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getHover(file.getVirtualFile(), file.getProject(), params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<MarkupContent>> getHover(@NotNull VirtualFile file,
                                                                            @NotNull Project project,
                                                                            @NotNull HoverParams params,
                                                                            @NotNull CancellationSupport cancellationSupport) {

        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, LanguageServerItem::isHoverSupported)
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have hover capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Collect list of textDocument/hover future for each language servers
                    List<CompletableFuture<MarkupContent>> hoverPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getHoverFor(params, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/hover future in one future which return the list of highlights
                    return mergeInOneFuture(hoverPerServerFutures, cancellationSupport);
                });
    }

    public static @NotNull CompletableFuture<List<MarkupContent>> mergeInOneFuture(@NotNull List<CompletableFuture<MarkupContent>> futures,
                                                                                   @NotNull CancellationSupport cancellationSupport) {
        CompletableFuture<Void> allFutures = cancellationSupport
                .execute(CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])));
        return allFutures.thenApply(Void -> {
            List<MarkupContent> mergedDataList = new ArrayList<>(futures.size());
            for (CompletableFuture<MarkupContent> dataListFuture : futures) {
                var data = dataListFuture.join();
                if (data != null) {
                    mergedDataList.add(data);
                }
            }
            return mergedDataList;
        });
    }

    private static CompletableFuture<MarkupContent> getHoverFor(@NotNull HoverParams params,
                                                                @NotNull LanguageServerItem languageServer,
                                                                @NotNull CancellationSupport cancellationSupport) {
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .hover(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_HOVER)
                .thenApplyAsync(LSPHoverSupport::getHoverString);
    }

    private static @Nullable MarkupContent getHoverString(Hover hover) {
        if (hover == null) {
            // textDocument/hover may return null
            return null;
        }
        Either<List<Either<String, MarkedString>>, MarkupContent> hoverContent = hover.getContents();
        if (hoverContent == null) {
            return null;
        }
        if (hoverContent.isLeft()) {
            List<Either<String, MarkedString>> contents = hoverContent.getLeft();
            if (contents == null || contents.isEmpty()) {
                return null;
            }
            String s = contents.stream()
                    .map(content -> {
                        if (content.isLeft()) {
                            return content.getLeft();
                        } else if (content.isRight()) {
                            MarkedString markedString = content.getRight();
                            // TODO this won't work fully until markup parser will support syntax
                            // highlighting but will help display
                            // strings with language tags, e.g. without it things after <?php tag aren't
                            // displayed
                            if (markedString.getLanguage() != null && !markedString.getLanguage().isEmpty()) {
                                return String.format("```%s%n%s%n```", markedString.getLanguage(), markedString.getValue()); //$NON-NLS-1$
                            } else {
                                return markedString.getValue();
                            }
                        } else {
                            return ""; //$NON-NLS-1$
                        }
                    })
                    .filter(((Predicate<String>) String::isEmpty).negate())
                    .collect(Collectors.joining("\n\n"));
            return new MarkupContent(MarkupKind.PLAINTEXT, s);
        } else {
            return hoverContent.getRight();
        }
    }

}

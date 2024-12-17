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
package com.redhat.devtools.lsp4ij.features.workspaceSymbol;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.features.AbstractLSPWorkspaceFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP workspace symbol support which loads and caches symbol response by consuming:
 *
 * <ul>
 *     <li>LSP 'workspace/symbol' requests</li>
 * </ul>
 */
public class LSPWorkspaceSymbolSupport extends AbstractLSPWorkspaceFeatureSupport<LSPWorkspaceSymbolParams, List<WorkspaceSymbolData>> {

    public LSPWorkspaceSymbolSupport(@NotNull Project project) {
        super(project);
    }

    public CompletableFuture<List<WorkspaceSymbolData>> getWorkspaceSymbol(LSPWorkspaceSymbolParams params) {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<WorkspaceSymbolData>> doLoad(LSPWorkspaceSymbolParams params, CancellationSupport cancellationSupport) {
        Project project = super.getProject();
        return getWorkspaceSymbol(project, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<WorkspaceSymbolData>> getWorkspaceSymbol(@NotNull Project project,
                                                                                            @NotNull LSPWorkspaceSymbolParams params,
                                                                                            @NotNull CancellationSupport cancellationSupport) {
        return getLanguageServers(project,
                f -> f.getWorkspaceSymbolFeature().isEnabled() && params.canSupport(f.getWorkspaceSymbolFeature()),
                f -> f.getWorkspaceSymbolFeature().isSupported())
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which have workspaceSymbol capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // Collect list of workspace/symbol future for each language servers
                    List<CompletableFuture<List<WorkspaceSymbolData>>> workspaceSymbolPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getWorkspaceSymbolFor(params, languageServer, cancellationSupport, project))
                            .filter(Objects::nonNull)
                            .toList();

                    // Merge list of workspace/symbol future in one future which return the list of workspace symbol data
                    return CompletableFutures.mergeInOneFuture(workspaceSymbolPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<WorkspaceSymbolData>> getWorkspaceSymbolFor(@NotNull LSPWorkspaceSymbolParams params,
                                                                                      @NotNull LanguageServerItem languageServer,
                                                                                      @NotNull CancellationSupport cancellationSupport,
                                                                                      @NotNull Project project) {
        return cancellationSupport.execute(languageServer
                        .getWorkspaceService()
                        .symbol(params), languageServer, LSPRequestConstants.WORKSPACE_SYMBOL)
                .thenApplyAsync(symbols -> {
                    if (symbols == null) {
                        // workspace/symbol may return null
                        return null;
                    }
                    List<WorkspaceSymbolData> items = new ArrayList<>();
                    if (symbols.isLeft()) {
                        List<? extends SymbolInformation> s = symbols.getLeft();
                        for (var si : s) {
                            if (params.accept(si)) {
                                items.add(new WorkspaceSymbolData(
                                        si.getName(), si.getKind(), si.getLocation(), languageServer.getClientFeatures(), project));
                            }
                        }
                    } else if (symbols.isRight()) {
                        List<? extends WorkspaceSymbol> ws = symbols.getRight();
                        for (var si : ws) {
                            if (params.accept(si)) {
                                WorkspaceSymbolData item = createItem(si, languageServer.getClientFeatures(),project);
                                items.add(item);
                            }
                        }
                    }
                    return items;
                });
    }

    private static WorkspaceSymbolData createItem(WorkspaceSymbol si,
                                                  FileUriSupport fileUriSupport,
                                                  Project project) {
        String name = si.getName();
        SymbolKind symbolKind = si.getKind();
        if (si.getLocation().isLeft()) {
            return new WorkspaceSymbolData(
                    name, symbolKind, si.getLocation().getLeft(), fileUriSupport, project);
        }
        return new WorkspaceSymbolData(
                name, symbolKind, si.getLocation().getRight().getUri(), null, fileUriSupport, project);
    }
}
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
import com.redhat.devtools.lsp4ij.features.AbstractLSPWorkspaceFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LSP workspace symbol support which loads and caches symbol response by consuming:
 *
 * <ul>
 *     <li>LSP 'workspace/symbol' requests</li>
 * </ul>
 */
public class LSPWorkspaceSymbolSupport extends AbstractLSPWorkspaceFeatureSupport<LSPWorkspaceSymbolParams, List<WorkspaceSymbolData>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPWorkspaceSymbolSupport.class);

    /**
     * How long a search waits for the 'workspace/symbol' answer of a language server. A language server which runs but
     * never answers would otherwise keep the search waiting, without the symbols of the other language servers, until
     * the user types again or closes the popup (#722). It is as long as a 'workspace/textDocumentContent' request is
     * given, so as not to drop the answer of a language server which searches a large workspace.
     */
    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(10);

    private volatile @NotNull Duration answerTimeout = ANSWER_TIMEOUT;

    public LSPWorkspaceSymbolSupport(@NotNull Project project) {
        super(project);
    }

    public CompletableFuture<List<WorkspaceSymbolData>> getWorkspaceSymbol(LSPWorkspaceSymbolParams params) {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<WorkspaceSymbolData>> doLoad(LSPWorkspaceSymbolParams params, CancellationSupport cancellationSupport) {
        Project project = super.getProject();
        return getWorkspaceSymbol(project, params, answerTimeout, cancellationSupport);
    }

    /**
     * Sets how long a search waits for the 'workspace/symbol' answer of a language server, {@link #ANSWER_TIMEOUT} by
     * default.
     *
     * @param answerTimeout how long a search waits for the answer of a language server.
     */
    @TestOnly
    void setAnswerTimeout(@NotNull Duration answerTimeout) {
        this.answerTimeout = answerTimeout;
    }

    private static @NotNull CompletableFuture<List<WorkspaceSymbolData>> getWorkspaceSymbol(@NotNull Project project,
                                                                                            @NotNull LSPWorkspaceSymbolParams params,
                                                                                            @NotNull Duration answerTimeout,
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
                            .map(languageServer -> getWorkspaceSymbolFor(params, languageServer, answerTimeout, cancellationSupport, project))
                            .filter(Objects::nonNull)
                            .toList();

                    // Merge list of workspace/symbol future in one future which return the list of workspace symbol data
                    return CompletableFutures.mergeInOneFuture(workspaceSymbolPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<WorkspaceSymbolData>> getWorkspaceSymbolFor(@NotNull LSPWorkspaceSymbolParams params,
                                                                                      @NotNull LanguageServerItem languageServer,
                                                                                      @NotNull Duration answerTimeout,
                                                                                      @NotNull CancellationSupport cancellationSupport,
                                                                                      @NotNull Project project) {
        var request = languageServer.getWorkspaceService().symbol(params);
        // The project holds the request, not the support of a file, which the stop of the language server disposes: so
        // the stop cancels it, and the merge with the other language servers ends (#722)
        languageServer.getServerWrapper().cancelOnStop(languageServer.getServer(), request);
        cancelUnlessAnsweredWithin(answerTimeout, request, languageServer);
        return cancellationSupport.execute(request, languageServer, LSPRequestConstants.WORKSPACE_SYMBOL)
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
                                WorkspaceSymbolData item = createItem(si, languageServer, project);
                                items.add(item);
                            }
                        }
                    }
                    return items;
                });
    }

    /**
     * Cancels the given 'workspace/symbol' request, which sends '$/cancelRequest' to the language server, if the language
     * server doesn't answer it within the given time: a cancelled request gives no result, so the merge with the other
     * language servers ends with their answers (#722).
     *
     * @param answerTimeout  how long the language server may take to answer.
     * @param request        the 'workspace/symbol' request.
     * @param languageServer the language server which the request was sent to.
     */
    private static void cancelUnlessAnsweredWithin(@NotNull Duration answerTimeout,
                                                   @NotNull CompletableFuture<?> request,
                                                   @NotNull LanguageServerItem languageServer) {
        // A copy times out, and the request is cancelled: a request failed with the timeout would send no
        // '$/cancelRequest', and would fail the whole search
        request.copy()
                .orTimeout(answerTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(error -> {
                    if (error instanceof TimeoutException) {
                        LOGGER.warn("The language server '{}' didn't answer 'workspace/symbol' within {} ms: the request is cancelled",
                                languageServer.getServerDefinition().getDisplayName(), answerTimeout.toMillis());
                        CancellationSupport.cancel(request);
                    }
                    return null;
                });
    }

    private static WorkspaceSymbolData createItem(WorkspaceSymbol si,
                                                  LanguageServerItem languageServer,
                                                  Project project) {
        String name = si.getName();
        SymbolKind symbolKind = si.getKind();
        if (si.getLocation().isLeft()) {
            return new WorkspaceSymbolData(
                    name, symbolKind, si.getLocation().getLeft(), languageServer.getClientFeatures(), project);
        }
        // The location has no range: the symbol is resolved when it is opened
        return new WorkspaceSymbolData(si, languageServer, project);
    }
}

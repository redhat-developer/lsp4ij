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
package com.redhat.devtools.lsp4ij.features;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Base class to consume LSP requests (ex : workspace/symbol) from all language servers applying to a given project.
 *
 * @param <Params> the LSP requests parameters (ex : WorkspaceSymbolParams).
 * @param <Result> the LSP response results (ex : List<WorkspaceSymbolData>).
 */
public abstract class AbstractLSPWorkspaceFeatureSupport<Params, Result> extends AbstractLSPFeatureSupport<Params, Result> {

    // The project
    private final @NotNull Project project;

    public AbstractLSPWorkspaceFeatureSupport(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Returns the project.
     *
     * @return the project.
     */
    public @NotNull Project getProject() {
        return project;
    }

    @Override
    protected boolean checkValid() {
        return !project.isDisposed();
    }

    @Override
    public CompletableFuture<Result> getFeatureData(Params params) {
        if (!isValidLSPFuture()) {
            // - the LSP requests have never been executed
            // - or the LSP requests has failed
            // --> consume LSP requests for all language servers applying to a given project.
            load(getLanguageServers(), params);
        }
        return getFuture();
    }

    /**
     * Returns the language servers that support this feature for the current project.
     * This method should return a future with the filtered language servers.
     *
     * @return the language servers future.
     */
    protected abstract CompletableFuture<List<LanguageServerItem>> getLanguageServers();

    /**
     * Load the LSP data for the given language servers.
     *
     * @param languageServers     the language servers.
     * @param params              the LSP parameters expected to execute LSP requests.
     * @param cancellationSupport the cancellation support.
     * @return the LSP response results.
     */
    protected abstract CompletableFuture<Result> doLoadData(@NotNull List<LanguageServerItem> languageServers,
                                                            @NotNull Params params,
                                                            @NotNull CancellationSupport cancellationSupport);

    protected static CompletableFuture<List<LanguageServerItem>> getLanguageServers(@NotNull Project project,
                                                                                    @Nullable Predicate<LSPClientFeatures> beforeStartingServerFilter,
                                                                                    @Nullable Predicate<LSPClientFeatures> afterStartingServerFilter) {
        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(beforeStartingServerFilter, afterStartingServerFilter);
    }
}

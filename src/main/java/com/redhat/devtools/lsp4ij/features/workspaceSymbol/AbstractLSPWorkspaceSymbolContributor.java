/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.workspaceSymbol;

import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import com.redhat.devtools.lsp4ij.LSPWorkspaceSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Abstract base class for LSP goto symbol contributors
 */
abstract class AbstractLSPWorkspaceSymbolContributor implements ChooseByNameContributorEx {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLSPWorkspaceSymbolContributor.class);

    @Override
    public void processNames(@NotNull Processor<? super String> processor,
                             @NotNull GlobalSearchScope scope,
                             @Nullable IdFilter filter) {
        Project project = scope.getProject();
        if (project == null) {
            return;
        }
        String queryString = project.getUserData(ChooseByNamePopup.CURRENT_SEARCH_PATTERN);
        if (queryString == null) {
            queryString = "";
        }

        var workspaceSymbolsFuture = getWorkspaceSymbols(queryString, true, project);
        ProgressIndicatorUtils.awaitWithCheckCanceled(workspaceSymbolsFuture);
        if (CompletableFutures.isDoneNormally(workspaceSymbolsFuture)) {
            var items = workspaceSymbolsFuture.getNow(null);
            if (items != null) {
                items.stream()
                        .filter(data -> data.getFile() != null && ReadAction.compute(() -> scope.accept(data.getFile())))
                        .map(NavigationItem::getName)
                        .forEach(processor::process);
            }
        }
    }

    @Override
    public void processElementsWithName(@NotNull String name,
                                        @NotNull Processor<? super NavigationItem> processor,
                                        @NotNull FindSymbolParameters parameters) {
        var workspaceSymbolsFuture = getWorkspaceSymbols(name, false, parameters.getProject());
        ProgressIndicatorUtils.awaitWithCheckCanceled(workspaceSymbolsFuture);

        if (CompletableFutures.isDoneNormally(workspaceSymbolsFuture)) {
            var items = workspaceSymbolsFuture.getNow(null);
            if (items != null) {
                items.stream()
                        .filter(data -> data.getFile() != null && ReadAction.compute(() -> parameters.getSearchScope().accept(data.getFile())))
                        .forEach(processor::process);
            }
        }
    }

    private CompletableFuture<List<WorkspaceSymbolData>> getWorkspaceSymbols(@NotNull String name, boolean cancel, Project project) {
        LSPWorkspaceSymbolSupport workspaceSymbolSupport = LSPWorkspaceSupport.getSupport(project).getWorkspaceSymbolSupport();
        if (cancel) {
            workspaceSymbolSupport.cancel();
        }
        LSPWorkspaceSymbolParams params = createWorkspaceSymbolParams(name);

        CancellationSupport cancellationSupport = new CancellationSupport();
        return cancellationSupport.execute(
                workspaceSymbolSupport.getWorkspaceSymbol(params),
                null,
                "workspace/symbol"
        ).exceptionally(throwable -> {
            Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;

            if (cause instanceof CancellationException) {
                throw (CancellationException) cause;
            }

            // Log other types of exceptions
            LOGGER.debug("Workspace symbol request failed for query: {}", name, throwable);
            return Collections.emptyList();
        });

    }

    /**
     * Creates the {@link LSPWorkspaceSymbolParams} implementation for this symbol contributor.
     *
     * @param name the (partial) name being requested
     * @return the LSP workspace symbol params
     */
    @NotNull
    protected abstract LSPWorkspaceSymbolParams createWorkspaceSymbolParams(@NotNull String name);
}

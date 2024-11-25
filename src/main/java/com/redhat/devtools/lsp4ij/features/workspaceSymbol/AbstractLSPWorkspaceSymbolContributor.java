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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import com.redhat.devtools.lsp4ij.LSPWorkspaceSupport;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * Abstract base class for LSP goto symbol contributors
 */
abstract class AbstractLSPWorkspaceSymbolContributor implements ChooseByNameContributorEx {

    private final Logger LOGGER;

    protected AbstractLSPWorkspaceSymbolContributor(@NotNull Logger logger) {
        this.LOGGER = logger;
    }

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
        List<WorkspaceSymbolData> items = getWorkspaceSymbols(queryString, true, project);
        if (items != null) {
            items.stream()
                    .filter(data -> scope.accept(data.getFile()))
                    .map(NavigationItem::getName)
                    .forEach(processor::process);
        }
    }

    @Override
    public void processElementsWithName(@NotNull String name,
                                        @NotNull Processor<? super NavigationItem> processor,
                                        @NotNull FindSymbolParameters parameters) {
        List<WorkspaceSymbolData> items = getWorkspaceSymbols(name, false, parameters.getProject());
        if (items != null) {
            items
                    .stream()
                    .filter(ni -> parameters.getSearchScope().accept(ni.getFile()))
                    .forEach(processor::process);
        }
    }

    private List<WorkspaceSymbolData> getWorkspaceSymbols(@NotNull String name, boolean cancel, Project project) {
        // Consume LSP 'workspace/symbol' request
        LSPWorkspaceSymbolSupport workspaceSymbolSupport = LSPWorkspaceSupport.getSupport(project).getWorkspaceSymbolSupport();
        if (cancel) {
            workspaceSymbolSupport.cancel();
        }
        WorkspaceSymbolParams params = new WorkspaceSymbolParams(name);
        CompletableFuture<List<WorkspaceSymbolData>> workspaceSymbolFuture = workspaceSymbolSupport.getWorkspaceSymbol(params);
        try {
            waitUntilDone(workspaceSymbolFuture);
        } catch (
                ProcessCanceledException e) {//Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
            //TODO delete block when minimum required version is 2024.2
            workspaceSymbolSupport.cancel();
            return null;
        } catch (CancellationException e) {
            // cancel the LSP requests workspace/symbol
            workspaceSymbolSupport.cancel();
            return null;
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'workspace/symbol' request", e);
            return null;
        }

        if (isDoneNormally(workspaceSymbolFuture)) {
            // workspace/symbol has been collected correctly
            return filter(workspaceSymbolFuture.getNow(null));
        }
        return null;
    }

    private @Nullable List<WorkspaceSymbolData> filter(@Nullable List<WorkspaceSymbolData> items) {
        if (items != null) {
            List<WorkspaceSymbolData> mutableItems = new ArrayList<>(items);
            mutableItems.removeIf(item -> !accept(item));
            return mutableItems;
        } else {
            return null;
        }
    }

    /**
     * Determines whether or not the provided symbol should be included in the contributor's symbol list.
     *
     * @param item the symbol
     * @return true if the symbol should be include; otherwise false
     */
    protected abstract boolean accept(@NotNull WorkspaceSymbolData item);
}
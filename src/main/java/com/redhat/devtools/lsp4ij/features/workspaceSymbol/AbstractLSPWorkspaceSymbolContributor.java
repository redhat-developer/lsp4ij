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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final static Logger LOGGER = LoggerFactory.getLogger(AbstractLSPWorkspaceSymbolContributor.class);

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
                    .filter(data -> data.getFile() != null && scope.accept(data.getFile()))
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
        LSPWorkspaceSymbolParams params = createWorkspaceSymbolParams(name);
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
            return workspaceSymbolFuture.getNow(null);
        }
        return null;
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
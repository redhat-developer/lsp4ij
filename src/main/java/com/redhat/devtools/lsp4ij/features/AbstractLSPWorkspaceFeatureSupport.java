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

    protected static CompletableFuture<List<LanguageServerItem>> getLanguageServers(@NotNull Project project,
                                                                                    @Nullable Predicate<LSPClientFeatures> beforeStartingServerFilter,
                                                                                    @Nullable Predicate<LSPClientFeatures> afterStartingServerFilter) {
        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(beforeStartingServerFilter, afterStartingServerFilter);
    }
}

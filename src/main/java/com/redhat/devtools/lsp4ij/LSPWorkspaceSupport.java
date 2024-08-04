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
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.redhat.devtools.lsp4ij.features.workspaceSymbol.LSPWorkspaceSymbolSupport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * LSP file support stored in the opened {@link Project} with key "lsp.workspace.support"
 * which manages and caches LSP workspace requests like workspace/symbol futures, etc.
 */
@ApiStatus.Internal

public class LSPWorkspaceSupport extends UserDataHolderBase implements Disposable {

    private static final Key<LSPWorkspaceSupport> LSP_WORKSPACE_SUPPORT_KEY = Key.create("lsp.workspace.support");

    private final Project project;

    private final LSPWorkspaceSymbolSupport workspaceSymbolSupport;

    private LSPWorkspaceSupport(@NotNull Project project) {
        this.project = project;
        this.workspaceSymbolSupport = new LSPWorkspaceSymbolSupport(project);
        project.putUserData(LSP_WORKSPACE_SUPPORT_KEY, this);
    }

    @Override
    public void dispose() {
        // cancel all LSP requests
        project.putUserData(LSP_WORKSPACE_SUPPORT_KEY, null);
        getWorkspaceSymbolSupport().cancel();
    }

    /**
     * Returns the LSP workspace symbol support.
     *
     * @return the LSP workspace symbol support.
     */
    public LSPWorkspaceSymbolSupport getWorkspaceSymbolSupport() {
        return workspaceSymbolSupport;
    }

    /**
     * Return the existing LSP workspace support for the given project, or create a new one if necessary.
     *
     * @param project the project.
     * @return the existing LSP workspace support for the given project, or create a new one if necessary.
     */
    public static @NotNull LSPWorkspaceSupport getSupport(@NotNull Project project) {
        LSPWorkspaceSupport support = project.getUserData(LSP_WORKSPACE_SUPPORT_KEY);
        if (support == null) {
            // create LSP support by taking care of multiple threads which could call it.
            support = createSupport(project);
        }
        return support;
    }

    private synchronized static @NotNull LSPWorkspaceSupport createSupport(@NotNull Project project) {
        LSPWorkspaceSupport support = project.getUserData(LSP_WORKSPACE_SUPPORT_KEY);
        if (support != null) {
            return support;
        }
        return new LSPWorkspaceSupport(project);
    }

    /**
     * Returns true if the given project has LSP support and false otherwise.
     *
     * @param project the project.
     * @return true if the given project has LSP support and false otherwise.
     */
    public static boolean hasSupport(@NotNull Project project) {
        return project.getUserData(LSP_WORKSPACE_SUPPORT_KEY) != null;
    }

    public Project getProject() {
        return project;
    }

}
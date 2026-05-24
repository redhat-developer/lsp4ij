/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.redhat.devtools.lsp4ij.dap.client.files.DAPFileRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Manages the lifecycle of DAP (Debug Adapter Protocol) resources when projects are opened or closed.
 * <ul>
 *   <li>Cleans up DAP file cache when a project is closed to prevent memory leaks.</li>
 * </ul>
 */
public class DAPProjectLifecycleListener implements ProjectManagerListener {

    @Override
    public void projectClosing(@NotNull Project project) {
        // Clear DAP files cache for this project to prevent memory leak
        DAPFileRegistry.getInstance().clearProjectFiles(project);
    }
}

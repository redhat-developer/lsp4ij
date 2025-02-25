/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This file is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Debuggable file API.
 */
public interface DebuggableFile {

    /**
     * Checks if the given file is debuggable within the specified project.
     *
     * <p>A file is considered debuggable if it matches a valid file mapping
     * defined for the server. Mappings can be based on file type, filename pattern, or language.</p>
     *
     * <p>If the file is debuggable, breakpoints can be added or removed.</p>
     *
     * @param file    the virtual file to check (must not be null)
     * @param project the project context (must not be null)
     * @return {@code true} if the file is debuggable and allows breakpoints, {@code false} otherwise
     */
    boolean isDebuggableFile(@NotNull VirtualFile file, @NotNull Project project);
}

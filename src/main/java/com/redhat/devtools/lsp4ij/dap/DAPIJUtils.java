/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.openapi.vfs.VirtualFile;
import org.eclipse.lsp4j.debug.Source;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Debug Adapter Protocol (DAP) utilities.
 */
public class DAPIJUtils {

    private DAPIJUtils() {

    }

    @NotNull
    public static String getFilePath(@NotNull VirtualFile file) {
        return file.toNioPath().toString();
    }

    @NotNull
    public static String getFileName(@NotNull VirtualFile file) {
        return file.getName();
    }

    /**
     * Returns the valid path of the DAP source path and null otherwise.
     *
     * @param source the DAP source.
     * @return the valid path of the DAP source path and null otherwise.
     */
    public static @Nullable Path getValidFilePath(@NotNull Source source) {
        try {
            // ex: source.path = C:\Users\XXXX\IdeaProjects\test-ls\bar.ts
            return Paths.get(source.getPath().trim());
        } catch (Exception e) {
            // Invalid path...
            // ex: <node_internals>/internal/modules/cjs/loader
            return null;
        }
    }
}

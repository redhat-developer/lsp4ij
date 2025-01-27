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
import org.jetbrains.annotations.NotNull;

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

}

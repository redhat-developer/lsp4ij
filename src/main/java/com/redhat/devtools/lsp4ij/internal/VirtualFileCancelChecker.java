/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import com.intellij.openapi.vfs.VirtualFile;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CancellationException;

/**
 * Virtual file cancel checker.
 */
public class VirtualFileCancelChecker implements CancelChecker {

    private final @NotNull VirtualFile file;
    private final long modificationCount;

    public VirtualFileCancelChecker(@NotNull VirtualFile file) {
        this(file, null);
    }

    public VirtualFileCancelChecker(@NotNull VirtualFile file,
                                    @Nullable Long modificationCount) {
        this.file = file;
        this.modificationCount = modificationCount != null ? modificationCount : file.getModificationCount();
    }


    @Override
    public void checkCanceled() {
        if (modificationCount != file.getModificationCount()) {
            throw new CancellationException();
        }
    }
}

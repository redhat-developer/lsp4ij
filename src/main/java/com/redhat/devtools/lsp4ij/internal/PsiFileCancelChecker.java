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

import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CancellationException;

/**
 * Psi file cancel checker.
 */
public class PsiFileCancelChecker implements CancelChecker {

    private final @NotNull PsiFile file;
    private final long modificationStamp;

    public PsiFileCancelChecker(@NotNull PsiFile file) {
        this(file, null);
    }
    
    public PsiFileCancelChecker(@NotNull PsiFile file,
                                @Nullable Long modificationStamp) {
        this.file = file;
        this.modificationStamp = modificationStamp != null ? modificationStamp : file.getModificationStamp();
    }


    @Override
    public void checkCanceled() {
        if (modificationStamp != file.getModificationStamp()) {
            throw new CancellationException();
        }
    }
}

/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * Angelo Zerr - implementation of DAP disassembly support
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.disassembly.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.FileViewProviderFactory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

/**
 * Disassembly file view provider factory.
 */
public class DisassemblyFileViewProviderFactory implements FileViewProviderFactory  {

    @Override
    public @NotNull FileViewProvider createFileViewProvider(@NotNull VirtualFile file,
                                                            Language language,
                                                            @NotNull PsiManager manager,
                                                            boolean eventSystemEnabled) {
        return new DisassemblyFileViewProvider(manager, file);
    }
}
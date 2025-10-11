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

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFileType;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * Disassembly Psi file.
 */
public class PsiDisassemblyFile extends PsiFileBase {

    protected PsiDisassemblyFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, DisassemblyLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return DisassemblyFileType.INSTANCE;
    }
}
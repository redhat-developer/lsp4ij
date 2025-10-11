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
package com.redhat.devtools.lsp4ij.dap.disassembly;

import com.intellij.lang.Language;

/**
 * Disassembly language.
 */
public class DisassemblyLanguage extends Language {
    public static final DisassemblyLanguage INSTANCE = new DisassemblyLanguage();

    private DisassemblyLanguage() {
        super("Disassembly");
    }
}
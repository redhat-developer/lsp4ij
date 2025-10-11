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

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Disassembly Psi parser.
 */
class DisassemblyParser implements PsiParser {
  @NotNull
  @Override
  public ASTNode parse(@NotNull IElementType root, PsiBuilder builder) {
    PsiBuilder.Marker mark = builder.mark();
    while (!builder.eof()) {
      builder.advanceLexer();
    }
    mark.done(root);
    return builder.getTreeBuilt();
  }
}
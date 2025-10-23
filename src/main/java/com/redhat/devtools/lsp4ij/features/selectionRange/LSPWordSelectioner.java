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
package com.redhat.devtools.lsp4ij.features.selectionRange;

import com.intellij.codeInsight.editorActions.wordSelection.WordSelectioner;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * LSP4IJ uses a custom {@link LSPWordSelectioner} to disable the standard {@link WordSelectioner}
 * fromm IntelliJ if language server can support LSP selection ranges.
 * <p>
 * However, if LSP selection ranges returns an empty ranges, the {@link WordSelectioner} must be
 * used otherwise it will select the full line instead of the word.
 * <p>
 * This {@link WordSelectioner} implementation provides the capability to  enable the LSP word selection filter
 * to use it when selection ranges returns an empty ranges.
 */
public class LSPWordSelectioner extends WordSelectioner {

    public boolean canSelect(@NotNull PsiElement e) {
        try {
            LSPWordSelectionFilter.enable(e);
            return super.canSelect(e);
        } finally {
            LSPWordSelectionFilter.disable(e);
        }
    }
}

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

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link FileViewProvider} for LSP-backed files where information about elements can be derived from reported
 * semantic tokens. The implementation can be whatever is appropriate for the file, but this provides a common
 * interface by which the file view provider can be populated with and queried for semantic token information.
 */
public interface LSPSemanticTokensFileViewProvider extends FileViewProvider, LSPSemanticTokensContainer {
    /**
     * Returns the semantic tokens file view provider for the provided element if assignable and enabled.
     *
     * @param element the PSI element
     * @return the semantic tokens file view provider if assignable and enabled; otherwise false
     */
    @Nullable
    @Contract("null -> null")
    static LSPSemanticTokensFileViewProvider getInstance(@Nullable PsiElement element) {
        PsiFile file = element != null ? element.getContainingFile() : null;
        FileViewProvider fileViewProvider = file != null ? file.getViewProvider() : null;
        return ((fileViewProvider instanceof LSPSemanticTokensFileViewProvider semanticTokensFileViewProvider) &&
                semanticTokensFileViewProvider.isEnabled()) ?
                semanticTokensFileViewProvider :
                null;
    }
}

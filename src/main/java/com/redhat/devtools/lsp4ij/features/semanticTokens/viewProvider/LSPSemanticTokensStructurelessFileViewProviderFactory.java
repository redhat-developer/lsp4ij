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

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProviderFactory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link FileViewProviderFactory} for files with no inherent PSI tree where PSI element should correspond exactly to
 * semantic tokens, e.g., plain text and TextMate files.
 */
public final class LSPSemanticTokensStructurelessFileViewProviderFactory extends LSPSemanticTokensFileViewProviderFactory {

    static final LSPSemanticTokensStructurelessFileViewProviderFactory INSTANCE = new LSPSemanticTokensStructurelessFileViewProviderFactory();

    @Override
    @NotNull
    protected LSPSemanticTokensFileViewProvider createFileViewProviderForLanguage(@NotNull PsiManager psiManager,
                                                                                  @NotNull VirtualFile virtualFile,
                                                                                  boolean eventSystemEnabled,
                                                                                  @NotNull Language language) {
        return new LSPSemanticTokensStructurelessFileViewProvider(psiManager, virtualFile, eventSystemEnabled, language);
    }

    @Override
    @NotNull
    protected LSPSemanticTokensFileViewProvider createFileViewProviderForFileType(@NotNull PsiManager psiManager,
                                                                                  @NotNull VirtualFile virtualFile,
                                                                                  boolean eventSystemEnabled) {
        return new LSPSemanticTokensStructurelessFileViewProvider(psiManager, virtualFile, eventSystemEnabled);
    }
}

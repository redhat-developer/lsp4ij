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
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.FileViewProviderFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link FileViewProviderFactory} for LSP-backed files where elements are derived dynamically from semantic tokens.
 */
public class LSPSemanticTokensFileViewProviderFactory implements FileViewProviderFactory {

    @Override
    @NotNull
    public FileViewProvider createFileViewProvider(@NotNull VirtualFile virtualFile,
                                                   Language language,
                                                   @NotNull PsiManager psiManager,
                                                   boolean eventSystemEnabled) {
        // Only create a semantic tokens-based view provider for files supported by a configured language server
        if ((language != null) && LanguageServersRegistry.getInstance().isFileSupported(virtualFile, language)) {
            return new LSPSemanticTokensFileViewProvider(psiManager, virtualFile, eventSystemEnabled, language);
        } else if (LanguageServersRegistry.getInstance().isFileSupported(virtualFile, language)) {
            return new LSPSemanticTokensFileViewProvider(psiManager, virtualFile, eventSystemEnabled);
        }

        // If not supported, use the standard file view provider for simple source files
        return new SingleRootFileViewProvider(psiManager, virtualFile, eventSystemEnabled);
    }
}

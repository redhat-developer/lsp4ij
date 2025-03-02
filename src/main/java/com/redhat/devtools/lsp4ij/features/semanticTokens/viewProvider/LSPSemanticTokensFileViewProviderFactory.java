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
 * A {@link FileViewProviderFactory} for LSP-backed files where information about elements can be derived from reported
 * semantic tokens.
 */
public class LSPSemanticTokensFileViewProviderFactory implements FileViewProviderFactory {

    @Override
    @NotNull
    public final FileViewProvider createFileViewProvider(@NotNull VirtualFile virtualFile,
                                                         Language language,
                                                         @NotNull PsiManager psiManager,
                                                         boolean eventSystemEnabled) {
        // Only create a semantic tokens-based view provider for files supported by a configured language server
        if (LanguageServersRegistry.getInstance().isFileSupported(virtualFile, language)) {
            return language != null ?
                    // If there's a language, create a file view provider for it
                    createFileViewProviderForLanguage(psiManager, virtualFile, eventSystemEnabled, language) :
                    // Otherwise create one for the file type
                    createFileViewProviderForFileType(psiManager, virtualFile, eventSystemEnabled);
        }

        // If not supported, use the standard file view provider for simple source files
        return new SingleRootFileViewProvider(psiManager, virtualFile, eventSystemEnabled);
    }

    /**
     * Creates a semantic tokens-based file view provider for a file with a specific language.
     *
     * @param psiManager         the PSI manager
     * @param virtualFile        the virtual file
     * @param eventSystemEnabled whether or not the event system is enabled
     * @param language           the file's language
     * @return the semantic tokens-based file view provider for the file and language
     */
    @NotNull
    protected LSPSemanticTokensFileViewProvider createFileViewProviderForLanguage(@NotNull PsiManager psiManager,
                                                                                           @NotNull VirtualFile virtualFile,
                                                                                           boolean eventSystemEnabled,
                                                                                  @NotNull Language language) {
        return new LSPSemanticTokensSingleRootFileViewProvider(psiManager, virtualFile, eventSystemEnabled, language);
    }

    /**
     * Creates a semantic tokens-based file view provider for a file without a specific language.
     *
     * @param psiManager         the PSI manager
     * @param virtualFile        the virtual file
     * @param eventSystemEnabled whether or not the event system is enabled
     * @return the semantic tokens-based file view provider for the file
     */
    @NotNull
    protected LSPSemanticTokensFileViewProvider createFileViewProviderForFileType(@NotNull PsiManager psiManager,
                                                                                           @NotNull VirtualFile virtualFile,
                                                                                  boolean eventSystemEnabled) {
        return new LSPSemanticTokensSingleRootFileViewProvider(psiManager, virtualFile, eventSystemEnabled);
    }
}

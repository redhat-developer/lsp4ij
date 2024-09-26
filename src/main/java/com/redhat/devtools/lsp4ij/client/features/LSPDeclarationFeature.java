/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * LSP declaration feature.
 */
@ApiStatus.Experimental
public class LSPDeclarationFeature extends AbstractLSPDocumentFeature {

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isDeclarationSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support codelens and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support codelens and false otherwise.
     */
    public boolean isDeclarationSupported(@NotNull PsiFile file) {
        // TODO implement documentSelector to use language of the given file
        return LanguageServerItem.isDeclarationSupported(getClientFeatures().getServerWrapper().getServerCapabilitiesSync());
    }
}

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

import com.intellij.lang.LanguageFormatting;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * LSP formatting feature.
 */
@ApiStatus.Experimental
public class LSPFormattingFeature extends AbstractLSPDocumentFeature {

    @Override
    public boolean isEnabled(@NotNull PsiFile file) {
        if (!isExistingFormatterOverrideable(file) && LanguageFormatting.INSTANCE.forContext(file) != null) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isDocumentFormattingSupported(file);
    }

    /**
     * Returns true if existing formatter are overrideable and false (default value) otherwise.
     *
     * @return true if existing formatter are overrideable and false (default value) otherwise.
     */
    protected boolean isExistingFormatterOverrideable(@NotNull PsiFile file) {
        return false;
    }

    /**
     * Returns true if the file associated with a language server can support formatting and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support formatting and false otherwise.
     */
    public boolean isDocumentFormattingSupported(@NotNull PsiFile file) {
        // TODO implement documentSelector to use language of the given file
        return LanguageServerItem.isDocumentFormattingSupported(getClientFeatures().getServerWrapper().getServerCapabilitiesSync());
    }

    /**
     * Returns true if the file associated with a language server can support range formatting and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support range formatting and false otherwise.
     */
    public boolean isDocumentRangeFormattingSupported(@NotNull PsiFile file) {
        // TODO implement documentSelector to use language of the given file
        return LanguageServerItem.isDocumentRangeFormattingSupported(getClientFeatures().getServerWrapper().getServerCapabilitiesSync());
    }

}

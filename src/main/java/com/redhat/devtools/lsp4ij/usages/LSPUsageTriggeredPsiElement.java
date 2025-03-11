/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.usages;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.List;

/**
 * Psi element used to execute a "Find Usages".
 * This Psi element contains the offset of the caret editor where the
 * "Find Usages" has been triggered.
 */
public class LSPUsageTriggeredPsiElement extends LSPPsiElement {

    private List<LocationData> references;

    public LSPUsageTriggeredPsiElement(@NotNull PsiFile file, @NotNull TextRange textRange) {
        super(file, textRange);
    }

    @Nullable
    public List<LocationData> getLSPReferences() {
        return references;
    }

    public void setLSPReferences(List<LocationData> references) {
        this.references = references;
    }

    @Nullable
    public PsiElement getRealElement() {
        return getContainingFile().findElementAt(getTextOffset());
    }

    @Override
    @Nullable
    public Icon getIcon(boolean open) {
        return getRealElement() instanceof ItemPresentation itemPresentation ?
                itemPresentation.getIcon(open) :
                super.getIcon(open);
    }

    @Override
    @Nullable
    public String getLocationString() {
        // If this is reference, show the location string of the target
        PsiReference reference = getContainingFile().findReferenceAt(getTextOffset());
        PsiElement target = reference != null ? reference.resolve() : null;
        if (target instanceof LSPPsiElement lspElement) {
            return lspElement.getLocationString();
        }

        return super.getLocationString();
    }
}

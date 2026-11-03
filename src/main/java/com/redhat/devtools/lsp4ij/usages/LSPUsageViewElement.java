/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;

/**
 * The element an {@link LSPUsagePsiElement} is shown with in the usage view.
 * <p>
 * The usage preview highlights a usage whose element is a {@link PsiNamedElement}, like the declaration of a symbol,
 * at the PSI element found at its text offset, its name. An {@link LSPUsagePsiElement} is a {@link PsiNamedElement},
 * as any {@link FakePsiElement} is, and in a file without real PSI, like a plain text or TextMate file, the PSI
 * element at any offset is the whole file: the preview highlighted the whole file and scrolled to its end. This
 * element is not a {@link PsiNamedElement}, so the preview highlights its text range, which is the range of the usage.
 * </p>
 */
final class LSPUsageViewElement extends LightElement {

    private final @NotNull LSPUsagePsiElement usage;

    LSPUsageViewElement(@NotNull LSPUsagePsiElement usage) {
        super(usage.getManager(), usage.getLanguage());
        this.usage = usage;
    }

    /**
     * Returns the usage shown with this element.
     *
     * @return the usage shown with this element.
     */
    @NotNull
    LSPUsagePsiElement getUsage() {
        return usage;
    }

    @Override
    public PsiElement getParent() {
        return usage.getParent();
    }

    @Override
    public PsiFile getContainingFile() {
        return usage.getContainingFile();
    }

    @Override
    public TextRange getTextRange() {
        return usage.getTextRange();
    }

    @Override
    public int getStartOffsetInParent() {
        return usage.getStartOffsetInParent();
    }

    @Override
    public int getTextOffset() {
        return usage.getTextOffset();
    }

    @Override
    public String getText() {
        return usage.getText();
    }

    @Override
    public boolean isValid() {
        return usage.isValid();
    }

    @Override
    public boolean isWritable() {
        return usage.isWritable();
    }

    @Override
    public String toString() {
        return "LSPUsageViewElement:" + usage.getTextRange();
    }
}

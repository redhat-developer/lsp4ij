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

import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Dummy class solely used to decide if the "Find Usages" menu action must be shown
 * (if file is associated to a language server) or hidden otherwise.
 *
 * This class is associated to the LSPFindUsagesProvider singleton when language server mappings are loaded / updated
 * in the {@link LanguageServersRegistry#updateFindUsagesProvider(Set)}}.
 *
 * See <a href="https://github.com/JetBrains/intellij-community/blob/e0de7cbb9e6045d682229032e2cbc47304c5310d/platform/lang-impl/src/com/intellij/find/actions/FindUsagesInFileAction.java#L111">canFindUsages</a>
 */
public class LSPFindUsagesProvider implements FindUsagesProvider {
    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
        return true;
    }

    @Override
    public @Nullable @NonNls String getHelpId(@NotNull PsiElement psiElement) {
        return null;
    }

    @Override
    public @Nls @NotNull String getType(@NotNull PsiElement element) {
        return "LSP TYPE";
    }

    @Override
    public @Nls @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        return "LSP description";
    }

    @Override
    public @Nls @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return "LSP node";
    }
}

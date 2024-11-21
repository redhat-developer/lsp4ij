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
package com.redhat.devtools.lsp4ij.features.typeHierarchy;

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.features.hierarchy.LSPHierarchyProviderBase;
import org.jetbrains.annotations.NotNull;

/**
 * LSP type hierarchy provider.
 */
public class LSPTypeHierarchyProvider extends LSPHierarchyProviderBase {

    @Override
    public @NotNull HierarchyBrowser createHierarchyBrowser(@NotNull PsiElement target) {
        return new LSPTypeHierarchyBrowser(target);
    }

    @Override
    public void browserActivated(@NotNull HierarchyBrowser hierarchyBrowser) {
        ((HierarchyBrowserBaseEx)hierarchyBrowser).changeView(CallHierarchyBrowserBase.getCallerType());
    }
}

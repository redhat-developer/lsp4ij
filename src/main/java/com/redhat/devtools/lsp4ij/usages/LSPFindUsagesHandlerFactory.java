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

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP find usage handler factory.
 */
public class LSPFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
    @Override
    public boolean canFindUsages(@NotNull PsiElement element) {
        // Execute the dummy LSP find usage handler to collect references, implementations
        // with LSPUsageSearcher if file is associated to a language server.
        if (ProjectIndexingManager.canExecuteLSPFeature(element.getContainingFile()) == ExecuteLSPFeatureStatus.NOW) {
            // To avoid overriding existing FindUsagesHandlerFactory for the given PsiElement
            // (ex: JavaFindUsagesHandlerFactory which is also defined as "last".)
            // we check if it exists a FindUsagesHandlerFactory for the element.
            return !canExternalFindUsages(element);
        }
        return false;
    }

    @Override
    public @Nullable FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
        return new LSPFindUsagesHandler(element);
    }

    private boolean canExternalFindUsages(@NotNull PsiElement element) {
        for (FindUsagesHandlerFactory factory : FindUsagesHandlerFactory.EP_NAME.getExtensions(element.getProject())) {
            try {
                if (!factory.equals(this) &&  factory.canFindUsages(element)) {
                    return true;
                }
            }
            catch (IndexNotReadyException | ProcessCanceledException e) {
                throw e;
            }
            catch (Exception e) {

            }
        }
        return false;
    }
}

/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and declaration
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.typeHierarchy;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.features.hierarchy.LSPHierarchyNodeDescriptor;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP type hierarchy tree structure base for typeHierarchy/supertypes.
 */
public class LSPTypeHierarchySupertypesTreeStructure extends LSPTypeHierarchyTreeStructureBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPTypeHierarchySupertypesTreeStructure.class);

    public LSPTypeHierarchySupertypesTreeStructure(@NotNull Project project, @NotNull PsiElement psiElement) {
        super(project, psiElement);
    }

    @Override
    protected void buildChildren(@NotNull HierarchyNodeDescriptor descriptor,
                                 @NotNull PsiFile psiFile,
                                 @Nullable TypeHierarchyItem hierarchyItem,
                                 @NotNull List<LSPHierarchyNodeDescriptor> descriptors) {
        LSPTypeHierarchySupertypesSupport typeHierarchySupertypesSupport = LSPFileSupport.getSupport(psiFile).getTypeHierarchySupertypesSupport();
        typeHierarchySupertypesSupport.cancel();
        var params = new TypeHierarchySupertypesParams(hierarchyItem);
        CompletableFuture<List<TypeHierarchyItemData>> prepareTypeHierarchyFuture = typeHierarchySupertypesSupport.getTypeHierarchySupertypes(params);
        try {
            waitUntilDone(prepareTypeHierarchyFuture, psiFile);
        } catch (ProcessCanceledException ex) {
            // cancel the LSP requests typeHierarchy/supertypes
            typeHierarchySupertypesSupport.cancel();
        } catch (CancellationException ex) {
            // cancel the LSP requests typeHierarchy/supertypes
            typeHierarchySupertypesSupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'typeHierarchy/supertypes' request", e);
        }
        fillChildren(descriptor, prepareTypeHierarchyFuture, descriptors);
    }

}

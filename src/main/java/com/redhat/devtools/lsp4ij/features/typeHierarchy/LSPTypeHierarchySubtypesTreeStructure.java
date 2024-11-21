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
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
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
 * LSP type hierarchy tree structure base for typeHierarchy/subtypes.
 */
public class LSPTypeHierarchySubtypesTreeStructure extends LSPTypeHierarchyTreeStructureBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPTypeHierarchySubtypesTreeStructure.class);

    public LSPTypeHierarchySubtypesTreeStructure(@NotNull Project project, @NotNull PsiElement psiElement) {
        super(project, psiElement);
    }

    @Override
    protected void buildChildren(@NotNull HierarchyNodeDescriptor descriptor,
                                 @NotNull PsiFile psiFile,
                                 @Nullable TypeHierarchyItem hierarchyItem,
                                 @NotNull List<LSPHierarchyNodeDescriptor> descriptors) {
        LSPTypeHierarchySubtypesSupport typeHierarchySubtypesSupport = LSPFileSupport.getSupport(psiFile).getTypeHierarchySubtypesSupport();
        typeHierarchySubtypesSupport.cancel();
        var params = new TypeHierarchySubtypesParams(hierarchyItem);
        CompletableFuture<List<TypeHierarchyItemData>> prepareTypeHierarchyFuture = typeHierarchySubtypesSupport.getTypeHierarchySubtypes(params);
        try {
            waitUntilDone(prepareTypeHierarchyFuture, psiFile);
        } catch (ProcessCanceledException ex) {
            // cancel the LSP requests typeHierarchy/subtypes
            typeHierarchySubtypesSupport.cancel();
        } catch (CancellationException ex) {
            // cancel the LSP requests typeHierarchy/subtypes
            typeHierarchySubtypesSupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'typeHierarchy/subtypes' request", e);
        }
        fillChildren(descriptor, prepareTypeHierarchyFuture, descriptors);
    }

}

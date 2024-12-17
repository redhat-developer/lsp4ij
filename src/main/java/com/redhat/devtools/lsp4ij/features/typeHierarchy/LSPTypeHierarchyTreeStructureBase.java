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

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.hierarchy.LSPHierarchyNodeDescriptor;
import com.redhat.devtools.lsp4ij.features.hierarchy.LSPHierarchyTreeStructureBase;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP type hierarchy tree structure base for typeHierarchy/subtypes / typeHierarchy/supertypes.
 */
public abstract class LSPTypeHierarchyTreeStructureBase extends LSPHierarchyTreeStructureBase<TypeHierarchyItem> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPTypeHierarchyTreeStructureBase.class);

    public LSPTypeHierarchyTreeStructureBase(@NotNull Project project, @NotNull PsiElement psiElement) {
        super(project, psiElement);
    }

    @Override
    protected @NotNull LSPHierarchyNodeDescriptor createHierarchyNodeDescriptor(@NotNull Project project,
                                                                                @Nullable NodeDescriptor parentDescriptor,
                                                                                @NotNull PsiElement element,
                                                                                @Nullable TypeHierarchyItem hierarchyItem) {
        return new LSPTypeHierarchyNodeDescriptor(project, parentDescriptor, element, hierarchyItem);
    }

    @Override
    protected void buildRoot(@NotNull HierarchyNodeDescriptor descriptor,
                             @NotNull PsiFile psiFile,
                             @NotNull Document document,
                             int offset,
                             @NotNull List<LSPHierarchyNodeDescriptor> descriptors) {
        // Consume LSP 'textDocument/prepareTypeHierarchy' request
        LSPPrepareTypeHierarchySupport prepareTypeHierarchySupport = LSPFileSupport.getSupport(psiFile).getPrepareTypeHierarchySupport();
        var params = new LSPTypeHierarchyPrepareParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()), LSPIJUtils.toPosition(offset, document), offset);
        CompletableFuture<List<TypeHierarchyItemData>> prepareTypeHierarchyFuture = prepareTypeHierarchySupport.getPrepareTypeHierarchies(params);
        try {
            waitUntilDone(prepareTypeHierarchyFuture, psiFile);
        } catch (ProcessCanceledException ex) {
            // cancel the LSP requests textDocument/prepareTypeHierarchy
            prepareTypeHierarchySupport.cancel();
        } catch (CancellationException ex) {
            // cancel the LSP requests textDocument/prepareTypeHierarchy
            prepareTypeHierarchySupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/prepareTypeHierarchy' request", e);
        }
        fillChildren(descriptor, prepareTypeHierarchyFuture, descriptors);
    }

    protected void fillChildren(@NotNull HierarchyNodeDescriptor descriptor,
                                @NotNull CompletableFuture<List<TypeHierarchyItemData>> typeHierarchyFuture,
                                @NotNull List<LSPHierarchyNodeDescriptor> descriptors) {
        if (isDoneNormally(typeHierarchyFuture)) {
            List<TypeHierarchyItemData> items = typeHierarchyFuture.getNow(null);
            if (items != null) {
                for (var item : items) {
                    var typeHierarchyItem = item.typeHierarchyItem();
                    PsiElement element = createPsiElement(typeHierarchyItem.getUri(), typeHierarchyItem.getRange(), typeHierarchyItem.getName(), item.languageServer().getClientFeatures());
                    if (element != null) {
                        descriptors.add(createHierarchyNodeDescriptor(myProject, descriptor, element, typeHierarchyItem));
                    }
                }
            }
        }
    }

}

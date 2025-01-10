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
package com.redhat.devtools.lsp4ij.features.callHierarchy;

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
import org.eclipse.lsp4j.CallHierarchyItem;
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
 * LSP call hierarchy tree structure base for callHierarchy/incomingCalls / callHierarchy/outgoingCalls.
 */
public abstract class LSPCallHierarchyTreeStructureBase extends LSPHierarchyTreeStructureBase<CallHierarchyItem> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPCallHierarchyTreeStructureBase.class);

    public LSPCallHierarchyTreeStructureBase(@NotNull Project project, @NotNull PsiElement psiElement) {
        super(project, psiElement);
    }

    @Override
    protected @NotNull LSPHierarchyNodeDescriptor createHierarchyNodeDescriptor(@NotNull Project project,
                                                                                @Nullable NodeDescriptor parentDescriptor,
                                                                                @NotNull PsiElement element,
                                                                                @Nullable CallHierarchyItem hierarchyItem) {
        return new LSPCallHierarchyNodeDescriptor(project, parentDescriptor, element, hierarchyItem);
    }

    @Override
    protected void buildRoot(@NotNull HierarchyNodeDescriptor descriptor,
                             @NotNull PsiFile psiFile,
                             @NotNull Document document,
                             int offset,
                             @NotNull List<LSPHierarchyNodeDescriptor> descriptors) {
        // Consume LSP 'textDocument/prepareCallHierarchy' request
        LSPPrepareCallHierarchySupport prepareCallHierarchySupport = LSPFileSupport.getSupport(psiFile).getPrepareCallHierarchySupport();
        var params = new LSPCallHierarchyPrepareParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()), LSPIJUtils.toPosition(offset, document), offset);
        CompletableFuture<List<CallHierarchyItemData>> prepareCallHierarchyFuture = prepareCallHierarchySupport.getPrepareCallHierarchies(params);
        try {
            waitUntilDone(prepareCallHierarchyFuture, psiFile);
        } catch (ProcessCanceledException ex) {
            // cancel the LSP requests textDocument/prepareCallHierarchy
            prepareCallHierarchySupport.cancel();
        } catch (CancellationException ex) {
            // cancel the LSP requests textDocument/prepareCallHierarchy
            prepareCallHierarchySupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/prepareCallHierarchy' request", e);
        }
        fillChildren(descriptor, prepareCallHierarchyFuture, descriptors);
    }

    protected void fillChildren(@NotNull HierarchyNodeDescriptor descriptor,
                                @Nullable CompletableFuture<List<CallHierarchyItemData>> callHierarchyFuture,
                                @NotNull List<LSPHierarchyNodeDescriptor> descriptors) {
        if (isDoneNormally(callHierarchyFuture)) {
            List<CallHierarchyItemData> items = callHierarchyFuture.getNow(null);
            if (items != null) {
                for (var item : items) {
                    var callHierarchyItem = item.callHierarchyItem();
                    PsiElement element = createPsiElement(callHierarchyItem.getUri(),
                            callHierarchyItem.getRange(),
                            callHierarchyItem.getName(),
                            item.languageServer().getClientFeatures());
                    if (element != null) {
                        descriptors.add(createHierarchyNodeDescriptor(myProject, descriptor, element, callHierarchyItem));
                    }
                }
            }
        }
    }

}

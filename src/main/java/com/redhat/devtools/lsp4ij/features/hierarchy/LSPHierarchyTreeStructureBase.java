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
package com.redhat.devtools.lsp4ij.features.hierarchy;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * LSP hierarchy tree structure base.
 *
 * @param <T> the LSP hierarchy item ({@link org.eclipse.lsp4j.CallHierarchyItem}, {@link org.eclipse.lsp4j.TypeHierarchyItem}.
 */
public abstract class LSPHierarchyTreeStructureBase<T> extends HierarchyTreeStructure {

    public LSPHierarchyTreeStructureBase(@NotNull Project project,
                                         @NotNull PsiElement psiElement) {
        super(project, null);
        super.setBaseElement(createHierarchyNodeDescriptor(project, null, psiElement, null));
    }

    @Override
    protected Object @NotNull [] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
        HierarchyNodeDescriptor nodeDescriptor = getBaseDescriptor();
        if (nodeDescriptor == null) {
            return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
        }
        final List<LSPHierarchyNodeDescriptor> descriptors = new ArrayList<>();
        if (descriptor instanceof LSPHierarchyNodeDescriptor lspDescriptor) {
            final PsiElement element = lspDescriptor.getPsiElement();
            PsiFile psiFile = element.getContainingFile();
            if (lspDescriptor.isBase()) {
                // fill with:
                // - prepareCallHierarchy
                // - prepareTypeHierarchy
                int offset = element.getTextRange().getStartOffset();
                Document document = LSPIJUtils.getDocument(psiFile.getVirtualFile());
                buildRoot(lspDescriptor, psiFile, document, offset, descriptors);
            } else {
                // fill with:
                // - callHierarchy/incomingCalls / callHierarchy/outgoingCalls
                // - typeHierarchy/subtypes / typeHierarchy/supertypes
                buildChildren(lspDescriptor, psiFile, (T) lspDescriptor.getHierarchyItem(), descriptors);
            }
        }
        return ArrayUtil.toObjectArray(descriptors);
    }

    @Nullable
    protected PsiElement createPsiElement(@Nullable String uri,
                                          @Nullable Range range,
                                          @NotNull String name,
                                          @Nullable FileUriSupport fileUriSupport) {
        VirtualFile file = FileUriSupport.findFileByUri(uri, fileUriSupport);
        if (file == null) {
            return null;
        }
        PsiFile psiFile = LSPIJUtils.getPsiFile(file, myProject);
        if (psiFile == null) {
            return null;
        }
        return new LSPHierarchyItemPsiElement(psiFile, range,name);
    }

    @NotNull
    protected abstract LSPHierarchyNodeDescriptor createHierarchyNodeDescriptor(@NotNull Project project,
                                                                                @Nullable NodeDescriptor parentDescriptor,
                                                                                @NotNull PsiElement element,
                                                                                @Nullable T hierarchyItem);

    protected abstract void buildRoot(@NotNull HierarchyNodeDescriptor descriptor,
                                      @NotNull PsiFile psiFile,
                                      @NotNull Document document,
                                      int offset,
                                      @NotNull List<LSPHierarchyNodeDescriptor> descriptors);

    protected abstract void buildChildren(@NotNull HierarchyNodeDescriptor descriptor,
                                          @NotNull PsiFile psiFile,
                                          @Nullable T hierarchyItem,
                                          @NotNull List<LSPHierarchyNodeDescriptor> descriptors);

}

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
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.ui.IconMapper;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * LSP hierarchy node descriptor.
 *
 * @param <T> the LSP hierarchy item ({@link org.eclipse.lsp4j.CallHierarchyItem}, {@link org.eclipse.lsp4j.TypeHierarchyItem}.
 */
public abstract class LSPHierarchyNodeDescriptor<T> extends HierarchyNodeDescriptor implements Navigatable {

    private final @Nullable T hierarchyItem;

    protected LSPHierarchyNodeDescriptor(@NotNull Project project,
                                         @Nullable NodeDescriptor parentDescriptor,
                                         @NotNull PsiElement element,
                                         @Nullable T hierarchyItem) {
        super(project, parentDescriptor, element, hierarchyItem == null);
        this.hierarchyItem = hierarchyItem;
    }

    public @Nullable T getHierarchyItem() {
        return hierarchyItem;
    }

    @Override
    public boolean update() {
        boolean changes = super.update();
        final CompositeAppearance oldText = myHighlightedText;

        myHighlightedText = new CompositeAppearance();

        NavigatablePsiElement element = (NavigatablePsiElement) getPsiElement();
        if (element == null) {
            return invalidElement();
        }

        final ItemPresentation presentation = element.getPresentation();
        if (presentation != null) {
            myHighlightedText.getEnding().addText(presentation.getPresentableText());
            String detail = getDetail(getHierarchyItem());
            if (detail != null && !detail.isBlank()) {
                myHighlightedText.getEnding().addText(" : " + detail, HierarchyNodeDescriptor.getPackageNameAttributes());
            }
        }
        myName = myHighlightedText.getText();

        if (!Comparing.equal(myHighlightedText, oldText)) {
            changes = true;
        }
        return changes;
    }

    @Override
    protected @Nullable Icon getIcon(@NotNull PsiElement element) {
        SymbolKind symbolKind = getSymbolKind(getHierarchyItem());
        if (symbolKind != null) {
            return IconMapper.getIcon(symbolKind);
        }
        return super.getIcon(element);
    }

    @Override
    public boolean canNavigate() {
        var element = getPsiElement();
        return element != null && element.getContainingFile() != null;
    }

    @Override
    public void navigate(boolean requestFocus) {
        PsiElement element = getPsiElement();
        if (element == null) {
            return;
        }
        VirtualFile file = element.getContainingFile().getVirtualFile();
        Range range = null;
        if(element instanceof LSPHierarchyItemPsiElement lspElement) {
           range = lspElement.getRange();
        }
        LSPIJUtils.openInEditor(file, range != null ? range.getStart() : null, requestFocus, getProject());
    }

    public boolean isBase() {
        return myIsBase;
    }

    @Nullable
    protected abstract String getDetail(@Nullable T hierarchyItem);

    @Nullable
    protected abstract SymbolKind getSymbolKind(@Nullable T hierarchyItem);

}

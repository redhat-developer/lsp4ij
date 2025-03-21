/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/

package com.redhat.devtools.lsp4ij.features.documentSymbol;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPDocumentSymbolStructureViewModel.LSPDocumentSymbolViewElement;
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPDocumentSymbolStructureViewModel.LSPFileStructureViewElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * LSP document symbol-based breadcrumbs provider.
 */
public class LSPBreadcrumbsProvider implements BreadcrumbsProvider {

    @Override
    public Language[] getLanguages() {
        // Register for all languages and filter in isSupported()
        List<Language> languages = LanguageUtil.getLanguages(Conditions.alwaysTrue());
        return languages.toArray(Language.EMPTY_ARRAY);
    }

    private boolean isSupported(@NotNull PsiElement element) {
        Project project = element.getProject();
        PsiFile file = element.getContainingFile();
        return LanguageServiceAccessor.getInstance(project).hasAny(
                file,
                // The breadcrumbs feature must be enabled and supported for the file's language server
                ls -> ls.getClientFeatures().getBreadcrumbsFeature().isEnabled(file) &&
                        ls.getClientFeatures().getBreadcrumbsFeature().isSupported(file)
        );
    }

    @Override
    public boolean acceptElement(@NotNull PsiElement element) {
        // NOTE: This is only used to determine which already-vetted elements should be included in the breadcrumb trail
        return element instanceof DocumentSymbolData;
    }

    @Override
    @Nullable
    public PsiElement getParent(@NotNull PsiElement element) {
        if (isSupported(element)) {
            if (element instanceof PsiFile file) {
                return file.getParent();
            } else if (element instanceof DocumentSymbolData documentSymbolData) {
                return documentSymbolData.getParent();
            } else {
                return LSPDocumentSymbolUtils.getDocumentSymbolData(element);
            }
        }

        return null;
    }

    @Override
    @NotNull
    public List<PsiElement> getChildren(@NotNull PsiElement element) {
        if (isSupported(element)) {
            if (element instanceof PsiFile file) {
                LSPDocumentSymbolStructureViewModel structureViewModel = LSPDocumentSymbolUtils.getStructureViewModel(file);
                StructureViewTreeElement root = structureViewModel != null ? structureViewModel.getRoot() : null;
                if (root instanceof LSPFileStructureViewElement fileStructureViewElement) {
                    StructureViewTreeElement[] children = fileStructureViewElement.getChildren();
                    List<PsiElement> childElements = new ArrayList<>(children.length);
                    for (StructureViewTreeElement child : children) {
                        if (child instanceof LSPDocumentSymbolViewElement documentSymbolViewElement) {
                            ContainerUtil.addIfNotNull(childElements, documentSymbolViewElement.getElement());
                        }
                    }
                    return childElements;
                }
            } else {
                DocumentSymbolData documentSymbolData = LSPDocumentSymbolUtils.getDocumentSymbolData(element);
                if (documentSymbolData != null) {
                    return Arrays.asList(documentSymbolData.getChildren());
                }
            }
        }

        return Collections.emptyList();
    }

    @Override
    @NotNull
    public String getElementInfo(@NotNull PsiElement element) {
        if (isSupported(element)) {
            if (element instanceof PsiFile file) {
                return file.getName();
            } else {
                DocumentSymbolData documentSymbolData = LSPDocumentSymbolUtils.getDocumentSymbolData(element);
                if (documentSymbolData != null) {
                    String presentableText = documentSymbolData.getPresentableText();
                    String name = documentSymbolData.getName();
                    return presentableText != null ? presentableText : name != null ? name : "";
                }
            }
        }

        return "";
    }

    @Override
    @Nullable
    public String getElementTooltip(@NotNull PsiElement element) {
        return !(element instanceof PsiFile) && isSupported(element) ? getElementInfo(element) : null;
    }

    @Override
    @Nullable
    public Icon getElementIcon(@NotNull PsiElement element) {
        if (!(element instanceof PsiFile) && isSupported(element)) {
            DocumentSymbolData documentSymbolData = LSPDocumentSymbolUtils.getDocumentSymbolData(element);
            return documentSymbolData != null ? documentSymbolData.getIcon(false) : null;
        }

        return null;
    }
}

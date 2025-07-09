/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.workspaceSymbol;

import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.ui.IconMapper;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Optional;

/**
 * LSP navigation item implementation.
 */
public class WorkspaceSymbolData extends LightElement implements NavigationItem, ItemPresentation {

    private final String name;
    private final SymbolKind symbolKind;
    private final String fileUri;
    private final FileUriSupport fileUriSupport;
    private final Position position;
    private final Project project;

    private @Nullable VirtualFile file;
    private @Nullable PsiFile psiFile;
    private @Nullable String locationString;
    private @Nullable TextRange textRange;


    public WorkspaceSymbolData(String name,
                               SymbolKind symbolKind,
                               Location location,
                               FileUriSupport fileUriSupport,
                               Project project) {
        this(name, symbolKind, location.getUri(), location.getRange().getStart(), fileUriSupport, project);
    }

    public WorkspaceSymbolData(String name,
                               SymbolKind symbolKind,
                               String fileUri,
                               Position position,
                               FileUriSupport fileUriSupport,
                               Project project) {
        super(PsiManager.getInstance(project), Language.ANY);
        this.name = name;
        this.symbolKind = symbolKind;
        this.fileUri = fileUri;
        this.position = position;
        this.fileUriSupport = fileUriSupport;
        this.project = project;
    }

    @Override
    public @Nullable String getName() {
        return name;
    }

    @Override
    public @NotNull Language getLanguage() {
        var psiFile = getPsiFile();
        if (psiFile != null) {
            return psiFile.getLanguage();
        }
        return super.getLanguage();
    }

    @Override
    public @Nullable ItemPresentation getPresentation() {
        return this;
    }

    @Override
    public @NlsSafe @Nullable String getPresentableText() {
        return getName();
    }

    @Override
    public @NlsSafe @Nullable String getLocationString() {
        if (locationString != null) {
            return locationString;
        }
        var file = getFile();
        locationString = file != null ? getLocationString(project, file) : fileUri;
        return locationString;
    }

    @Override
    public void navigate(boolean requestFocus) {
        LSPIJUtils.openInEditor(fileUri, position, requestFocus, false, fileUriSupport, project);
    }

    @Override
    public boolean canNavigate() {
        return true;
    }

    @Override
    public boolean canNavigateToSource() {
        return true;
    }

    @Override
    public @Nullable TextRange getTextRange() {
        if (textRange == null) {
            var file = getFile();
            if (file == null || !file.isValid()) {
                return null;
            }

            var document = LSPIJUtils.getDocument(file);
            if (document == null) {
                return null;
            }

            int offset = LSPIJUtils.toOffset(position, document);
            textRange = new TextRange(offset, offset);
        }
        return textRange;
    }

    @Override
    public String getText() {
        PsiFile file = getContainingFile();
        return file != null ? file.getText() : "";
    }

    @Override
    public @Nullable Icon getIcon(boolean unused) {
        return IconMapper.getIcon(symbolKind);
    }

    public @Nullable SymbolKind getSymbolKind() {
        return symbolKind;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public PsiFile getContainingFile() {
        return getPsiFile();
    }

    @Override
    public PsiManager getManager() {
        return PsiManager.getInstance(project);
    }

    @Override
    public @NotNull PsiElement getNavigationElement() {
        return this;
    }

    public @Nullable VirtualFile getFile() {
        if (file == null) {
            file = FileUriSupport.findFileByUri(fileUri, fileUriSupport);
        }
        return file;
    }

    private @Nullable PsiFile getPsiFile() {
        if (psiFile == null) {
            var file = getFile();
            if (file != null) {
                psiFile = LSPIJUtils.getPsiFile(file, project);
            }
        }
        return psiFile;
    }

    /**
     * This code is a copy/paste from <a href="https://github.com/JetBrains/intellij-community/blob/22243811e3e8342918b5c064cbb94c7886d8e3ed/plugins/htmltools/src/com/intellij/htmltools/html/HtmlGotoSymbolProvider.java#L46">HtmlGotoSymbolProvider.java.</a>
     *
     * @param project the project
     * @param file    the file
     * @return the location string
     */
    private static @Nullable String getLocationString(@NotNull Project project, @NotNull VirtualFile file) {
        return Optional.ofNullable(ProjectUtil.guessProjectDir(project))
                .map(projectDir -> VfsUtilCore.getRelativePath(file, projectDir, java.io.File.separatorChar))
                .map(path -> "(" + path + ")")
                .orElse(null);
    }

}
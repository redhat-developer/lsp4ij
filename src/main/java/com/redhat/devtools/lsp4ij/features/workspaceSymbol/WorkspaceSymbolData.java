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

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.ui.IconMapper;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Optional;

/**
 * LSP navigation item implementation.
 */
public class WorkspaceSymbolData implements NavigationItem {

    private final SymbolKind symbolKind;
    private final String fileUri;
    private final Position position;
    private final Project project;
    private final VirtualFile file;
    private final LSPItemPresentation presentation;
    private final FileUriSupport fileUriSupport;

    private record LSPItemPresentation(String name, SymbolKind symbolKind, String locationString) implements ItemPresentation {

        public String name() {
            return name;
        }

        @Override
        public @NlsSafe @Nullable String getPresentableText() {
            return name();
        }

        @Override
        public @Nullable Icon getIcon(boolean unused) {
            return IconMapper.getIcon(symbolKind);
        }

        @Override
        public @NlsSafe @Nullable String getLocationString() {
            return locationString;
        }

    }

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
        this.symbolKind = symbolKind;
        this.fileUri = fileUri;
        this.position = position;
        this.project = project;
        this.file = FileUriSupport.findFileByUri(fileUri, fileUriSupport);
        this.fileUriSupport = fileUriSupport;
        String locationString = file != null ? getLocationString(project, file) : fileUri;
        this.presentation = new LSPItemPresentation(name, symbolKind, locationString);
    }

    @Nullable
    public VirtualFile getFile() {
        return file;
    }

    public @Nullable SymbolKind getSymbolKind() {
        return symbolKind;
    }

    @Override
    public @Nullable @NlsSafe String getName() {
        return presentation.name();
    }

    @Override
    public @Nullable ItemPresentation getPresentation() {
        return presentation;
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

    /**
     * This code is a copy/paste from https://github.com/JetBrains/intellij-community/blob/22243811e3e8342918b5c064cbb94c7886d8e3ed/plugins/htmltools/src/com/intellij/htmltools/html/HtmlGotoSymbolProvider.java#L46
     * @param project
     * @param file
     * @return
     */
    private static @Nullable String getLocationString(@NotNull Project project, @NotNull VirtualFile file) {
        return Optional.ofNullable(ProjectUtil.guessProjectDir(project))
                .map(projectDir -> VfsUtilCore.getRelativePath(file, projectDir, File.separatorChar))
                .map(path -> "(" + path + ")")
                .orElse(null);
    }
}
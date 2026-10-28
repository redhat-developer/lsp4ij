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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.ui.IconMapper;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
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
    private @Nullable Position position;
    private final Project project;
    // A symbol whose location has no range, and the language server which resolves it when it is opened
    private final @Nullable WorkspaceSymbol unresolvedSymbol;
    private final @Nullable LanguageServerItem languageServer;

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
        this(name, symbolKind, fileUri, position, fileUriSupport, project, null, null);
    }

    /**
     * Creates the item of a workspace symbol whose location has a URI but no range. Its range is resolved with
     * 'workspaceSymbol/resolve' when the symbol is opened, if the language server supports it.
     *
     * @param symbol         the workspace symbol.
     * @param languageServer the language server which returned the symbol.
     * @param project        the project.
     */
    public WorkspaceSymbolData(@NotNull WorkspaceSymbol symbol,
                               @NotNull LanguageServerItem languageServer,
                               Project project) {
        this(symbol.getName(), symbol.getKind(), symbol.getLocation().getRight().getUri(), null,
                languageServer.getClientFeatures(), project, symbol, languageServer);
    }

    private WorkspaceSymbolData(String name,
                                SymbolKind symbolKind,
                                String fileUri,
                                @Nullable Position position,
                                FileUriSupport fileUriSupport,
                                Project project,
                                @Nullable WorkspaceSymbol unresolvedSymbol,
                                @Nullable LanguageServerItem languageServer) {
        super(PsiManager.getInstance(project), Language.ANY);
        this.name = name;
        this.symbolKind = symbolKind;
        this.fileUri = fileUri;
        this.position = position;
        this.fileUriSupport = fileUriSupport;
        this.project = project;
        this.unresolvedSymbol = unresolvedSymbol;
        this.languageServer = languageServer;
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
        if (position == null && unresolvedSymbol != null && languageServer != null
                && languageServer.getClientFeatures().getWorkspaceSymbolFeature().isResolveSupported()) {
            Ref<Position> resolved = Ref.create();
            if (!resolvePosition(unresolvedSymbol, languageServer, resolved)) {
                // The user cancelled
                return;
            }
            // Without a resolved range, the file opens without a position, as before
            position = resolved.get();
        }
        LSPIJUtils.openInEditor(fileUri, position, requestFocus, false, fileUriSupport, project);
    }

    /**
     * Resolves the range of a symbol with 'workspaceSymbol/resolve', under a progress which the user can cancel.
     *
     * @param symbol         the symbol whose location has no range.
     * @param languageServer the language server which returned the symbol.
     * @param resolved       set to the start of the resolved range, or to null if the server gives none.
     * @return false if the user cancelled, and true otherwise.
     */
    private boolean resolvePosition(@NotNull WorkspaceSymbol symbol,
                                    @NotNull LanguageServerItem languageServer,
                                    @NotNull Ref<Position> resolved) {
        return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            var cancellationSupport = new CancellationSupport();
            var future = cancellationSupport.execute(languageServer.getWorkspaceService()
                    .resolveWorkspaceSymbol(symbol), languageServer, LSPRequestConstants.WORKSPACE_SYMBOL_RESOLVE);
            try {
                WorkspaceSymbol resolvedSymbol = CancellationSupport.awaitWithCheckCanceled(future);
                if (resolvedSymbol != null && resolvedSymbol.getLocation() != null && resolvedSymbol.getLocation().isLeft()) {
                    resolved.set(resolvedSymbol.getLocation().getLeft().getRange().getStart());
                }
            } catch (ProcessCanceledException e) {
                cancellationSupport.cancel();
                throw e;
            } catch (Exception e) {
                // The symbol is opened without its range
            }
        }, LanguageServerBundle.message("workspace.symbol.resolve.progress", getName()), true, project);
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
            if (position == null) {
                // The location of the symbol has no range
                return null;
            }
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
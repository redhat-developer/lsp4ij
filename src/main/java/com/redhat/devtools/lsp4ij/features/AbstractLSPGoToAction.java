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
package com.redhat.devtools.lsp4ij.features;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.usages.LSPUsageType;
import com.redhat.devtools.lsp4ij.usages.LSPUsagesManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;

/**
 * Abstract class for Go To Implementation, Reference etc
 */
public abstract class AbstractLSPGoToAction extends AnAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLSPGoToAction.class);

    @NotNull
    private final LSPUsageType usageType;

    public AbstractLSPGoToAction(@NotNull LSPUsageType usageType) {
        this.usageType = usageType;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile == null) {
            return;
        }

        VirtualFile file = psiFile.getVirtualFile();
        if (file == null) {
            return;
        }

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }

        Document document = LSPIJUtils.getDocument(file);
        if (document == null) {
            return;
        }
        // Consume LSP 'textDocument/implementation' request
        int offset = TargetElementUtil.adjustOffset(psiFile, document, editor.getCaretModel().getOffset());
        CompletableFuture<List<Location>> locationsFuture = getLocations(psiFile, document, editor, offset);

        if (isDoneNormally(locationsFuture)) {
            // textDocument/implementations has been collected correctly
            List<Location> locations = locationsFuture.getNow(null);
            if (locations != null) {
                DataContext dataContext = e.getDataContext();
                // Call "Find Usages" in popup mode.
                LSPUsagesManager.getInstance(project).findShowUsagesInPopup(locations, usageType, dataContext, null);
            }
        }
    }

    protected abstract CompletableFuture<List<Location>> getLocations(PsiFile psiFile, Document document, Editor editor, int offset);

    @Override
    public final void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(canSupportAction(e));
    }


    private boolean canSupportAction(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || project.isDisposed()) {
            return false;
        }

        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) {
            return false;
        }
        if (!LanguageServersRegistry.getInstance().isFileSupported(file, project)) {
            // The file is not associated to a language server
            return false;
        }
        // Check if the file can support the feature
        try {
            return !LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(file, this::canSupportFeature)
                    .get(200, TimeUnit.MILLISECONDS)
                    .isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }

    protected abstract boolean canSupportFeature(ServerCapabilities serverCapabilities);

    @Override
    public final @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}

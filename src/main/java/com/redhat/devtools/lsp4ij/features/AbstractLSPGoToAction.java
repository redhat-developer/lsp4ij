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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.usages.LSPUsageType;
import com.redhat.devtools.lsp4ij.usages.LSPUsagesManager;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.eclipse.lsp4j.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;

/**
 * Abstract class for Go To Implementation, Reference etc
 */
public abstract class AbstractLSPGoToAction extends AnAction {

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

        ProgressManager.getInstance().run(new Task.Backgroundable(project, getProgressTitle(psiFile, offset), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                List<LocationData> locations = null;
                try {
                    CompletableFuture<List<LocationData>> locationsFuture = getLocations(psiFile, document, editor, offset);

                    if (isDoneNormally(locationsFuture)) {
                        // textDocument/(declaration|implementation|references|typeDefinition) has been collected correctly
                        locations = locationsFuture.getNow(null);
                    }
                } finally {
                    final List<LocationData> resultLocations = locations != null ? locations : Collections.emptyList();
                    DataContext dataContext = e.getDataContext();
                    // Call "Find Usages" in popup mode.
                    ApplicationManager.getApplication()
                            .invokeLater(() ->
                                    LSPUsagesManager.getInstance(project).findShowUsagesInPopup(resultLocations,
                                            usageType,
                                            dataContext,
                                            null)
                            );
                }
            }
        });
    }

    @Override
    public final void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(canSupportAction(e));
    }


    private boolean canSupportAction(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || project.isDisposed()) {
            return false;
        }

        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (file == null) {
            return false;
        }
        if (ProjectIndexingManager.canExecuteLSPFeature(file) != ExecuteLSPFeatureStatus.NOW) {
            // The file is not associated to a language server
            return false;
        }
        // Check if the file can support the feature
        return LanguageServiceAccessor.getInstance(project)
                .hasAny(file, ls -> canSupportFeature(ls.getClientFeatures(), file));
    }


    /**
     * Returns the progress title used by the task monitor which execute the LSP GoTo feature.
     *
     * @param psiFile the Psi file.
     * @param offset  the offset.
     * @return the progress title used by the task monitor which execute the LSP GoTo feature.
     */
    protected abstract @NlsContexts.ProgressTitle @NotNull String getProgressTitle(@NotNull PsiFile psiFile,
                                                                                   int offset);

    /**
     * Returns the LSP {@link Location} list result of the execution of the LSP GoTo feature.
     *
     * @param psiFile  the Psi file.
     * @param document the document.
     * @param editor   the editor.
     * @param offset   the offset.
     * @return the LSP {@link Location} list result of the execution of the LSP GoTo feature.
     */
    protected abstract CompletableFuture<List<LocationData>> getLocations(@NotNull PsiFile psiFile,
                                                                          @NotNull Document document,
                                                                          @NotNull Editor editor,
                                                                          int offset);

    /**
     * Returns true if the action is supported by the client features of the language server and false otherwise.
     *
     * @param clientFeatures the client features.
     * @param file           the Psi file.
     * @return true if the action is supported by the client features of the language server and false otherwise.
     */
    protected abstract boolean canSupportFeature(@NotNull LSPClientFeatures clientFeatures,
                                                 @NotNull PsiFile file);

    @Override
    public final @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}

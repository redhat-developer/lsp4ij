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
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.List;

/**
 * LSP usage manager.
 */
public class LSPUsagesManager {

    public static LSPUsagesManager getInstance(@NotNull Project project) {
        return project.getService(LSPUsagesManager.class);
    }

    private final @NotNull Project project;

    private LSPUsagesManager(@NotNull Project project) {
        this.project = project;
    }

    // Show references, implementation, etc in Popup

    public void findShowUsagesInPopup(@NotNull List<Location> locations,
                                      @NotNull DataContext dataContext,
                                      @Nullable MouseEvent event) {
        switch (locations.size()) {
            case 0: {
                showNoUsage(dataContext);
                break;
            }
            case 1: {
                // On result, open editor with the given location
                Location ref = locations.get(0);
                openLocation(ref, project);
                break;
            }
            default: {
                // Open locations in a Popup
                @Nullable Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
                Location ref = locations.get(0);
                LSPUsageTriggeredPsiElement element = toUsageTriggeredPsiElement(ref, project);
                element.setLSPReferences(locations);
                GotoDeclarationAction.startFindUsages(editor, element.getProject(), element, event == null ? null : new RelativePoint(event));
            }
        }
    }

    @Nullable
    public static LSPUsagePsiElement toPsiElement(@NotNull Location location,
                                                  @NotNull LSPUsagePsiElement.UsageKind kind,
                                                  @NotNull Project project) {
        return toPsiElement(location.getUri(), location.getRange(), kind, project);
    }

    @Nullable
    public static LSPUsagePsiElement toPsiElement(@NotNull LocationLink location,
                                                  @NotNull LSPUsagePsiElement.UsageKind kind,
                                                  @NotNull Project project) {
        return toPsiElement(location.getTargetUri(), location.getTargetRange(), kind, project);
    }

    @Nullable
    private static LSPUsagePsiElement toPsiElement(@NotNull String uri,
                                                   @NotNull Range range,
                                                   @NotNull LSPUsagePsiElement.UsageKind kind, Project project) {
        VirtualFile file = LSPIJUtils.findResourceFor(uri);
        if (file == null) {
            return null;
        }
        Document document = LSPIJUtils.getDocument(file);
        if (document == null) {
            return null;
        }
        TextRange textRange = LSPIJUtils.toTextRange(range, document);
        PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
        return new LSPUsagePsiElement(psiFile, textRange, kind);
    }

    @Nullable
    public static LSPUsageTriggeredPsiElement toUsageTriggeredPsiElement(@NotNull Location location,
                                                                         @NotNull Project project) {
        VirtualFile file = LSPIJUtils.findResourceFor(location.getUri());
        if (file == null) {
            return null;
        }
        Document document = LSPIJUtils.getDocument(file);
        if (document == null) {
            return null;
        }
        TextRange textRange = LSPIJUtils.toTextRange(location.getRange(), document);
        PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
        return new LSPUsageTriggeredPsiElement(psiFile, textRange);
    }

    private void showNoUsage(DataContext dataContext) {
        @Nullable Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
        @NotNull String message = "Cannot search for references from this location.\nPlace the caret on the element to find references for and try again."; //FindBundle.message("find.no.usages.at.cursor.error")
        if (editor == null) {
            Messages.showMessageDialog(project, message, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        } else {
            HintManager.getInstance().showErrorHint(editor, message);
        }
    }

    private static void openLocation(@NotNull Location location, @NotNull Project project) {
        ApplicationManager.getApplication().invokeLater(() -> LSPIJUtils.openInEditor(location, project));
    }
}

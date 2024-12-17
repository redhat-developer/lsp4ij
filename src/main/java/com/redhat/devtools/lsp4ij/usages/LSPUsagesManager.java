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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.awt.RelativePoint;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.features.LSPPsiElementFactory;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.List;

/**
 * LSP usage manager.
 */
public class LSPUsagesManager {

    private static final LSPPsiElementFactory<LSPUsagePsiElement> USAGE_ELEMENT_FACTORY
            = (psiFile, textRange) -> new LSPUsagePsiElement(psiFile, textRange);

    private static final LSPPsiElementFactory<LSPUsageTriggeredPsiElement> USAGE_TRIGGERED_ELEMENT_FACTORY
            = (psiFile, textRange) -> new LSPUsageTriggeredPsiElement(psiFile, textRange);

    public static LSPUsagesManager getInstance(@NotNull Project project) {
        return project.getService(LSPUsagesManager.class);
    }

    private final @NotNull Project project;

    private LSPUsagesManager(@NotNull Project project) {
        this.project = project;
    }

    // Show references, implementation, etc in Popup

    public void findShowUsagesInPopup(@NotNull List<LocationData> locations,
                                      @NotNull LSPUsageType usageType,
                                      @NotNull DataContext dataContext,
                                      @Nullable MouseEvent event) {
        switch (locations.size()) {
            case 0: {
                showNoUsage(usageType, dataContext);
                break;
            }
            case 1: {
                // On response, open editor with the given location
                LocationData ref = locations.get(0);
                openLocation(ref.location(), ref.languageServer().getClientFeatures(), project);
                break;
            }
            default: {
                // Open locations in a Popup
                @Nullable Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
                LocationData ref = locations.get(0);
                LSPUsageTriggeredPsiElement element = toUsageTriggeredPsiElement(ref.location(), ref.languageServer().getClientFeatures(), project);
                if (element != null) {
                    element.setLSPReferences(locations);
                }
                GotoDeclarationAction.startFindUsages(editor, element.getProject(), element, event == null ? null : new RelativePoint(event));
            }
        }
    }

    @Nullable
    public static LSPUsagePsiElement toPsiElement(@NotNull Location location,
                                                  @Nullable FileUriSupport fileUriSupport,
                                                  @NotNull LSPUsagePsiElement.UsageKind kind,
                                                  @NotNull Project project) {
        LSPUsagePsiElement element = LSPPsiElementFactory.toPsiElement(location, fileUriSupport, project, USAGE_ELEMENT_FACTORY);
        if (element != null) {
            element.setKind(kind);
        }
        return element;
    }

    @Nullable
    public static LSPUsagePsiElement toPsiElement(@NotNull LocationLink location,
                                                  @Nullable FileUriSupport fileUriSupport,
                                                  @NotNull LSPUsagePsiElement.UsageKind kind,
                                                  @NotNull Project project) {
        LSPUsagePsiElement element = LSPPsiElementFactory.toPsiElement(location, fileUriSupport, project, USAGE_ELEMENT_FACTORY);
        if (element != null) {
            element.setKind(kind);
        }
        return element;
    }

    @Nullable
    public static LSPUsageTriggeredPsiElement toUsageTriggeredPsiElement(@NotNull Location location,
                                                                         @Nullable FileUriSupport fileUriSupport,
                                                                         @NotNull Project project) {
        return LSPPsiElementFactory.toPsiElement(location, fileUriSupport, project, USAGE_TRIGGERED_ELEMENT_FACTORY);
    }

    private void showNoUsage(@NotNull LSPUsageType usageType, DataContext dataContext) {
        @Nullable Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
        @NotNull String key = getNoUsageMessageKey(usageType);
        String message = LanguageServerBundle.message(key);
        if (editor == null) {
            Messages.showMessageDialog(project, message, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        } else {
            HintManager.getInstance().showErrorHint(editor, message);
        }
    }

    private String getNoUsageMessageKey(LSPUsageType usageType) {
        switch (usageType) {
            case Declarations -> {
                return "no.usage.type.declarations";
            }
            case TypeDefinitions -> {
                return "no.usage.type.typeDefinitions";
            }
            case Implementations -> {
                return "no.usage.type.implementations";
            }
            case References -> {
                return "no.usage.type.references";
            }
        }
        return null;
    }

    private static void openLocation(@NotNull Location location,
                                     @Nullable FileUriSupport fileUriSupport,
                                     @NotNull Project project) {
        ApplicationManager.getApplication().invokeLater(() -> LSPIJUtils.openInEditor(location, fileUriSupport, project));
    }
}

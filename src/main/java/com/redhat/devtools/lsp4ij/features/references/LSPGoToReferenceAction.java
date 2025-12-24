/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and reference
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.references;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.features.AbstractLSPGoToAction;
import com.redhat.devtools.lsp4ij.usages.LSPUsageType;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LSP Go To Reference.
 */
public class LSPGoToReferenceAction extends AbstractLSPGoToAction {

    public LSPGoToReferenceAction() {
        super(LSPUsageType.References);
    }

    @Override
    protected CompletableFuture<List<LocationData>> getLocations(@NotNull PsiFile psiFile,
                                                                 @NotNull Document document,
                                                                 @NotNull Editor editor,
                                                                 int offset) {
        List<LocationData> locations = LSPReferenceCollector.collect(psiFile, document, offset);
        return CompletableFuture.completedFuture(locations);
    }

    @Override
    protected boolean canSupportFeature(@NotNull LSPClientFeatures clientFeatures, @NotNull PsiFile file) {
        return clientFeatures.getReferencesFeature().isReferencesSupported(file);
    }

    @Override
    protected @NotNull String getProgressTitle(@NotNull PsiFile psiFile,
                                               int offset) {
        return LanguageServerBundle.message("lsp.goto.reference.progress.title", psiFile.getName(), offset);
    }
}

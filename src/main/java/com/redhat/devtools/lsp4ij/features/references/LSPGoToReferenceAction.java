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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.features.AbstractLSPGoToAction;
import com.redhat.devtools.lsp4ij.usages.LSPUsageType;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP Go To Reference.
 */
public class LSPGoToReferenceAction extends AbstractLSPGoToAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPGoToReferenceAction.class);

    public LSPGoToReferenceAction() {
        super(LSPUsageType.References);
    }

    @Override
    protected CompletableFuture<List<LocationData>> getLocations(PsiFile psiFile, Document document, Editor editor, int offset) {
        LSPReferenceSupport referenceSupport = LSPFileSupport.getSupport(psiFile).getReferenceSupport();
        var params = new LSPReferenceParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()), LSPIJUtils.toPosition(offset, document), offset);
        CompletableFuture<List<LocationData>> referencesFuture = referenceSupport.getReferences(params);
        try {
            waitUntilDone(referencesFuture, psiFile);
        } catch (ProcessCanceledException ex) {
            // cancel the LSP requests textDocument/references
            referenceSupport.cancel();
        } catch (CancellationException ex) {
            // cancel the LSP requests textDocument/references
            referenceSupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/references' request", e);
        }
        return referencesFuture;
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

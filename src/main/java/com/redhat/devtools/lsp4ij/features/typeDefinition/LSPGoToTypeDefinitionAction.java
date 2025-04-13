/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and typeDefinition
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.typeDefinition;

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
 * LSP Go To TypeDefinition.
 */
public class LSPGoToTypeDefinitionAction extends AbstractLSPGoToAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPGoToTypeDefinitionAction.class);

    public LSPGoToTypeDefinitionAction() {
        super(LSPUsageType.TypeDefinitions);
    }

    @Override
    protected CompletableFuture<List<LocationData>> getLocations(@NotNull PsiFile psiFile,
                                                                 @NotNull Document document,
                                                                 @NotNull Editor editor,
                                                                 int offset) {
        LSPTypeDefinitionSupport typeDefinitionSupport = LSPFileSupport.getSupport(psiFile).getTypeDefinitionSupport();
        var params = new LSPTypeDefinitionParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()), LSPIJUtils.toPosition(offset, document), offset);
        CompletableFuture<List<LocationData>> typeDefinitionsFuture = typeDefinitionSupport.getTypeDefinitions(params);
        try {
            waitUntilDone(typeDefinitionsFuture, psiFile);
        } catch (ProcessCanceledException ex) {
            // cancel the LSP requests textDocument/typeDefinition
            typeDefinitionSupport.cancel();
        } catch (CancellationException ex) {
            // cancel the LSP requests textDocument/typeDefinition
            typeDefinitionSupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/typeDefinition' request", e);
        }
        return typeDefinitionsFuture;
    }

    @Override
    protected boolean canSupportFeature(@NotNull LSPClientFeatures clientFeatures, @NotNull PsiFile file) {
        return clientFeatures.getTypeDefinitionFeature().isTypeDefinitionSupported(file);
    }

    @Override
    protected @NotNull String getProgressTitle(@NotNull PsiFile psiFile,
                                               int offset) {
        return LanguageServerBundle.message("lsp.goto.typeDefinition.progress.title", psiFile.getName(), offset);
    }
}

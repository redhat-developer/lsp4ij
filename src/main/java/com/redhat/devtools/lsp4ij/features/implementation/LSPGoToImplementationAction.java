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
package com.redhat.devtools.lsp4ij.features.implementation;

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
 * LSP Go To Implementation.
 */
public class LSPGoToImplementationAction extends AbstractLSPGoToAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPGoToImplementationAction.class);

    public LSPGoToImplementationAction() {
        super(LSPUsageType.Implementations);
    }

    @Override
    protected CompletableFuture<List<LocationData>> getLocations(@NotNull PsiFile psiFile,
                                                                 @NotNull Document document,
                                                                 @NotNull Editor editor,
                                                                 int offset) {
        LSPImplementationSupport implementationSupport = LSPFileSupport.getSupport(psiFile).getImplementationSupport();
        var params = new LSPImplementationParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()), LSPIJUtils.toPosition(offset, document), offset);
        CompletableFuture<List<LocationData>> implementationsFuture = implementationSupport.getImplementations(params);
        try {
            waitUntilDone(implementationsFuture, psiFile);
        } catch (ProcessCanceledException ex) {
            // cancel the LSP requests textDocument/implementation
            implementationSupport.cancel();
        } catch (CancellationException ex) {
            // cancel the LSP requests textDocument/implementation
            implementationSupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/implementation' request", e);
        }
        return implementationsFuture;
    }

    @Override
    protected boolean canSupportFeature(@NotNull LSPClientFeatures clientFeatures, @NotNull PsiFile file) {
        return clientFeatures.getImplementationFeature().isImplementationSupported(file);
    }

    @Override
    protected @NotNull String getProgressTitle(@NotNull PsiFile psiFile,
                                               int offset) {
        return LanguageServerBundle.message("lsp.goto.implementation.progress.title", psiFile.getName(), offset);
    }

}

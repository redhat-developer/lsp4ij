/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.features.LSPPsiElementFactory.toPsiElement;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

public class LSPGotoDeclarationHandler implements GotoDeclarationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPGotoDeclarationHandler.class);

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        Project project = editor.getProject();
        if (project == null || project.isDisposed()) {
            return PsiElement.EMPTY_ARRAY;
        }
        PsiFile psiFile = sourceElement.getContainingFile();
        if (psiFile == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        if (!LanguageServersRegistry.getInstance().isFileSupported(psiFile)) {
            return PsiElement.EMPTY_ARRAY;
        }
        VirtualFile file = LSPIJUtils.getFile(sourceElement);
        if (file == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        Document document = editor.getDocument();

        // Consume LSP 'textDocument/definition' request
        LSPDefinitionSupport definitionSupport = LSPFileSupport.getSupport(psiFile).getDefinitionSupport();
        var params = new LSPDefinitionParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()), LSPIJUtils.toPosition(offset, document), offset);
        CompletableFuture<List<LocationData>> definitionsFuture = definitionSupport.getDefinitions(params);
        try {
            waitUntilDone(definitionsFuture, psiFile);
        } catch (ProcessCanceledException ex) {
            // cancel the LSP requests textDocument/definition
            definitionSupport.cancel();
        } catch (CancellationException ex) {
            // cancel the LSP requests textDocument/definition
            definitionSupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/definition' request", e);
        }

        if (isDoneNormally(definitionsFuture)) {
            // textDocument/definition has been collected correctly
            List<LocationData> locations = definitionsFuture.getNow(null);
            if (locations != null) {
                return locations
                        .stream()
                        .map(location -> toPsiElement(location.location(), location.languageServer().getClientFeatures(), project))
                        .filter(Objects::nonNull)
                        .toArray(PsiElement[]::new);
            }
        }
        return PsiElement.EMPTY_ARRAY;
    }

}

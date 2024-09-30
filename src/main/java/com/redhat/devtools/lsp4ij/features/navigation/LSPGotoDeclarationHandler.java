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
import com.intellij.openapi.application.ApplicationManager;
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
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import com.redhat.devtools.lsp4ij.features.definition.LSPDefinitionParams;
import com.redhat.devtools.lsp4ij.features.definition.LSPDefinitionSupport;
import com.redhat.devtools.lsp4ij.features.references.LSPReferenceParams;
import com.redhat.devtools.lsp4ij.features.references.LSPReferenceSupport;
import com.redhat.devtools.lsp4ij.usages.LSPUsageType;
import com.redhat.devtools.lsp4ij.usages.LSPUsagesManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.util.Ranges;
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
        PsiFile psiFile = sourceElement.getContainingFile();
        if (!LanguageServersRegistry.getInstance().isFileSupported(psiFile)) {
            return null;
        }
        VirtualFile file = LSPIJUtils.getFile(sourceElement);
        if (file == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        Document document = editor.getDocument();
        
        LSPDefinitionSupport definitionSupport = LSPFileSupport.getSupport(psiFile).getDefinitionSupport();
        var params = new LSPDefinitionParams(LSPIJUtils.toTextDocumentIdentifier(file), LSPIJUtils.toPosition(offset, document), offset);
        CompletableFuture<List<Location>> definitionsFuture = definitionSupport.getDefinitions(params);
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
            List<Location> definitions = definitionsFuture.getNow(null);
            if (definitions != null && !definitions.isEmpty()) {
                if (definitions.size() == 1) {
                    Location location = definitions.get(0);
                    if (LSPIJUtils.findResourceFor(location.getUri()).equals(file)
                     && Ranges.containsPosition(location.getRange(), params.getPosition())  ) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            openReference(offset, psiFile, file, document, editor);
                        });
                        return PsiElement.EMPTY_ARRAY;
                    }
                }
                Project project = editor.getProject();
                List<LSPPsiElement> targets = definitions
                        .stream()
                        .map(location -> toPsiElement(location, project))
                        .filter(Objects::nonNull)
                        .toList();
                return targets.toArray(new PsiElement[targets.size()]);
            }
        }
        return PsiElement.EMPTY_ARRAY;
    }

    private static void openReference(int offset, PsiFile psiFile, VirtualFile file, Document document, Editor editor) {
        LSPReferenceSupport referenceSupport = LSPFileSupport.getSupport(psiFile).getReferenceSupport();
        var params = new LSPReferenceParams(LSPIJUtils.toTextDocumentIdentifier(file), LSPIJUtils.toPosition(offset, document), offset);
        CompletableFuture<List<Location>> referencesFuture = referenceSupport.getReferences(params);
        try {
            waitUntilDone(referencesFuture, psiFile);
        } catch (ProcessCanceledException ex) {
            // cancel the LSP requests textDocument/reference
            referenceSupport.cancel();
        } catch (CancellationException ex) {
            // cancel the LSP requests textDocument/reference
            referenceSupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/reference' request", e);
        }

        if (isDoneNormally(referencesFuture)) {
            // textDocument/reference has been collected correctly
            List<Location> references = referencesFuture.getNow(null);
            if (references != null && !references.isEmpty()) {

                // Call "Find Usages" in popup mode.
                LSPUsagesManager.getInstance(psiFile.getProject())
                        .findShowUsagesInPopup(references, LSPUsageType.References, editor, null);

            }
        }
    }
}

/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.workspaceSymbol;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.features.LSPPsiElementFactory;
import com.redhat.devtools.lsp4ij.features.implementation.LSPImplementationParams;
import com.redhat.devtools.lsp4ij.features.implementation.LSPImplementationSupport;
import com.redhat.devtools.lsp4ij.ui.LSP4IJUiUtils;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * Implements the IDE's standard Go To Implementation(s) action using LSP textDocument/implementation. -->
 */
public class LSPWorkspaceImplementationsSearch extends QueryExecutorBase<PsiElement, DefinitionsScopedSearch.SearchParameters> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPWorkspaceImplementationsSearch.class);

    public LSPWorkspaceImplementationsSearch() {
        // It requires a Read Action,
        // because call of element.getContainingFile() could require it when element is an PsiLeafElement
        super(true);
    }

    @Override
    public void processQuery(DefinitionsScopedSearch.@NotNull SearchParameters queryParameters, @NotNull Processor<? super PsiElement> consumer) {
        Project project = queryParameters.getProject();
        if (project.isDisposed()) {
            return;
        }

        PsiElement element = queryParameters.getElement();
        if (!element.isValid()) {
            return;
        }

        PsiFile file = element.getContainingFile();
        if ((file == null) || !file.isValid()) {
            return;
        }

        if (ProjectIndexingManager.canExecuteLSPFeature(file) != ExecuteLSPFeatureStatus.NOW) {
            // The file is not associated to a language server
            return;
        }

        // Check if the file can support the feature
        if (!LanguageServiceAccessor.getInstance(project)
                .hasAny(file.getVirtualFile(), ls -> ls.getClientFeatures().getImplementationFeature().isImplementationSupported(file))) {
            return;
        }

        Document document = LSPIJUtils.getDocument(file);
        if (document == null) {
            return;
        }

        int offset = element.getTextRange().getStartOffset();
        LSPImplementationParams params = new LSPImplementationParams(
                LSPIJUtils.toTextDocumentIdentifier(file.getVirtualFile()),
                LSPIJUtils.toPosition(offset, document),
                offset
        );
        LSPImplementationSupport implementationSupport = LSPFileSupport.getSupport(file).getImplementationSupport();
        CompletableFuture<List<LocationData>> implementationsFuture = implementationSupport.getImplementations(params);
        try {
            waitUntilDone(implementationsFuture, file);
        } catch (ProcessCanceledException ex) {
            // cancel the LSP requests textDocument/implementation
            implementationSupport.cancel();
        } catch (CancellationException ex) {
            // cancel the LSP requests textDocument/implementation
            implementationSupport.cancel();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/implementation' request", e);
        }

        if (isDoneNormally(implementationsFuture)) {
            List<LocationData> implementations = implementationsFuture.getNow(null);
            if (ContainerUtil.isEmpty(implementations)) {
                // No implementations found
                LSP4IJUiUtils.showErrorHint(file, CodeInsightBundle.message("goto.implementation.notFound"));
            } else {
                // textDocument/implementations has been collected correctly
                for (LocationData implementation : implementations) {
                    if (!consumer.process(LSPPsiElementFactory.toPsiElement(implementation.location(), implementation.languageServer().getClientFeatures(), project))) {
                        return;
                    }
                }
            }
        }
    }
}

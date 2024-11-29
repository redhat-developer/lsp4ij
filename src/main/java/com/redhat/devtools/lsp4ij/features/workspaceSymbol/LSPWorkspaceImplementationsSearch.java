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
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.features.LSPPsiElementFactory;
import com.redhat.devtools.lsp4ij.features.implementation.LSPImplementationParams;
import com.redhat.devtools.lsp4ij.features.implementation.LSPImplementationSupport;
import org.eclipse.lsp4j.Location;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * Implements the IDE's standard Go To Implementation(s) action using LSP textDocument/implementation. -->
 */
public class LSPWorkspaceImplementationsSearch implements QueryExecutor<PsiElement, DefinitionsScopedSearch.SearchParameters> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPWorkspaceImplementationsSearch.class);

    @Override
    public boolean execute(@NotNull DefinitionsScopedSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiElement> consumer) {
        Project project = queryParameters.getProject();
        if (project.isDisposed()) {
            return true;
        }

        PsiElement element = queryParameters.getElement();
        if (!element.isValid()) {
            return true;
        }

        PsiFile file = element.getContainingFile();
        if ((file == null) || !file.isValid()) {
            return true;
        }

        if (ProjectIndexingManager.canExecuteLSPFeature(file) != ExecuteLSPFeatureStatus.NOW) {
            // The file is not associated to a language server
            return true;
        }

        // Check if the file can support the feature
        if (!LanguageServiceAccessor.getInstance(project)
                .hasAny(file.getVirtualFile(), ls -> ls.getClientFeatures().getImplementationFeature().isImplementationSupported(file))) {
            return true;
        }

        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) {
            return true;
        }

        int offset = element.getTextRange().getStartOffset();
        LSPImplementationParams params = new LSPImplementationParams(
                LSPIJUtils.toTextDocumentIdentifier(file.getVirtualFile()),
                LSPIJUtils.toPosition(offset, document),
                offset
        );
        LSPImplementationSupport implementationSupport = LSPFileSupport.getSupport(file).getImplementationSupport();
        CompletableFuture<List<Location>> implementationsFuture = implementationSupport.getImplementations(params);
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
            List<Location> implementations = implementationsFuture.getNow(null);
            if (ContainerUtil.isEmpty(implementations)) {
                // No implementations found
                Editor editor = PsiEditorUtil.findEditor(file);
                if (editor != null) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        String notFoundMessage = CodeInsightBundle.message("goto.implementation.notFound");
                        JComponent label = HintUtil.createErrorLabel(notFoundMessage);
                        label.setBorder(HintUtil.createHintBorder());
                        RelativePoint relativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(editor);
                        int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
                        HintManager.getInstance().showHint(label, relativePoint, flags, 0);
                    });
                } else {
                    LOGGER.warn("No implementations found but could not find an active editor for file '{}' to show the hint.", file.getName());
                }
            } else {
                // textDocument/implementations has been collected correctly
                for (Location implementation : implementations) {
                    if (!consumer.process(LSPPsiElementFactory.toPsiElement(implementation, project))) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}

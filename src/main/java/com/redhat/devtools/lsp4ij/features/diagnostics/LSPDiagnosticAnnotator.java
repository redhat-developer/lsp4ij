/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.OpenedDocument;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.features.AbstractLSPExternalAnnotator;
import org.eclipse.lsp4j.Diagnostic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * Intellij {@link ExternalAnnotator} implementation which get the current LSP diagnostics for a given file and translate
 * them into Intellij {@link com.intellij.lang.annotation.Annotation}.
 */
public class LSPDiagnosticAnnotator extends AbstractLSPExternalAnnotator<Boolean, Boolean> {

    private static final Key<Boolean> APPLIED_KEY = Key.create("lsp.diagnostic.annotator.applied");

    public LSPDiagnosticAnnotator() {
        super(APPLIED_KEY);
    }

    @Nullable
    @Override
    public Boolean collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        // Loop for language server which report diagnostics for the given file
        // and mark all opened documents as 'displaying diagnostics'
        var servers = LanguageServiceAccessor.getInstance(file.getProject())
                .getStartedServers();
        if (servers.isEmpty()) {
            return Boolean.FALSE;
        }
        boolean hasOpenedDocument = false;
        for (var ls : servers) {
            URI fileUri = FileUriSupport.getFileUri(file.getVirtualFile(), ls.getClientFeatures());
            OpenedDocument openedDocument = ls.getOpenedDocument(fileUri);
            if (openedDocument != null) {
                hasOpenedDocument = true;
                openedDocument.markAsDisplayingDiagnostics();
            }
        }
        return hasOpenedDocument;
    }

    @Override
    public @Nullable Boolean doAnnotate(Boolean result) {
        return result;
    }

    @Override
    public void doApply(@NotNull PsiFile psiFile, Boolean applyAnnotator, @NotNull AnnotationHolder holder) {
        if (!applyAnnotator) {
            return;
        }
        var servers = LanguageServiceAccessor.getInstance(psiFile.getProject())
                .getStartedServers();
        if (servers.isEmpty()) {
            return;
        }
        VirtualFile file = psiFile.getVirtualFile();
        if (file == null) {
            return;
        }

        Document document = LSPIJUtils.getDocument(file);
        if (document == null) {
            return;
        }
        // Loop for language server which report diagnostics for the given file
        for (var ls : servers) {
            URI fileUri = FileUriSupport.getFileUri(file, ls.getClientFeatures());
            OpenedDocument openedDocument = ls.getOpenedDocument(fileUri);
            if (openedDocument != null) {
                // The file is mapped with the current language server
                var ds = openedDocument.getDiagnosticsForServer();
                // Loop for LSP diagnostics to transform it to Intellij annotation.
                for (Diagnostic diagnostic : openedDocument.getDiagnostics()) {
                    ProgressManager.checkCanceled();
                    createAnnotation(diagnostic, document, psiFile, ds, holder);
                }
            }
        }
    }

    private static void createAnnotation(@NotNull Diagnostic diagnostic,
                                         @NotNull Document document,
                                         @NotNull PsiFile file,
                                         @NotNull LSPDiagnosticsForServer diagnosticsForServer,
                                         @NotNull AnnotationHolder holder) {
        var clientFeatures = diagnosticsForServer.getClientFeatures();
        var diagnosticSupport = clientFeatures.getDiagnosticFeature();
        if (!diagnosticSupport.isEnabled(file)) {
            return;
        }
        List<IntentionAction> fixes = Collections.emptyList();
        var codeActionFeature = clientFeatures.getCodeActionFeature();
        if (codeActionFeature.isQuickFixesEnabled(file)) {
            fixes = diagnosticsForServer.getQuickFixesFor(diagnostic, file);
        }
        diagnosticSupport.createAnnotation(diagnostic, document, fixes, holder);
    }

}
/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPDocumentBase;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.eclipse.lsp4j.Diagnostic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for LSP inspection tool.
 */
public abstract class LSPLocalInspectionToolBase extends LocalInspectionTool {

    @Override
    public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        if (!acceptFile(file, manager, isOnTheFly)) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        // Collect started servers which supports this inspection tool.
        List<LanguageServerWrapper> servers = new ArrayList<>();
        LanguageServiceAccessor.getInstance(file.getProject())
                .processLanguageServers(file, ls -> {
                    var diagnosticFeatures = ls.getClientFeatures().getDiagnosticFeature();
                    if (diagnosticFeatures.isEnabled(file)
                            && diagnosticFeatures.isSupported(file)
                            && diagnosticFeatures.isInspectionApplicableFor(file, this)) {
                        servers.add(ls);
                    }
                });
        if (servers.isEmpty()) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        // Several started servers are applicable for this inspection tool
        URI fileUri = LSPIJUtils.toUri(file);
        Document document = LSPIJUtils.getDocument(file.getVirtualFile());

        List<ProblemDescriptor> problemDescriptors = new ArrayList<>();
        // Loop for language server which report diagnostics for the given file
        for (var ls : servers) {
            // Get the opened/closed document of the server which hosts the published diagnostics
            LSPDocumentBase lspDocument = getOpenedOrClosedDocument(ls, fileUri);
            if (lspDocument != null) {
                // Loop for LSP diagnostics to transform it to Intellij problem descriptor.
                for (Diagnostic diagnostic : lspDocument.getDiagnostics()) {
                    var clientFeatures = ls.getClientFeatures();
                    var diagnosticFeature = clientFeatures.getDiagnosticFeature();
                    // check if diagnostic is application for this inspection tool
                    if (diagnosticFeature.isInspectionApplicableFor(diagnostic, this)) {
                        ProblemHighlightType problemHighlightType = diagnosticFeature.getProblemHighlightType(diagnostic);
                        if (problemHighlightType != null) {
                            // Create the Intellij problem descriptor
                            String message = diagnosticFeature.getMessage(diagnostic);
                            PsiElement psiElement = new LSPPsiElement(file, LSPIJUtils.toTextRange(diagnostic.getRange(), document, file, true)) {
                                @Override
                                public boolean isPhysical() {
                                    return true;
                                }
                            };
                            var problemDescriptor = manager.createProblemDescriptor(
                                    psiElement,
                                    message,
                                    false,
                                    problemHighlightType,
                                    isOnTheFly
                            );
                            problemDescriptors.add(problemDescriptor);
                        }
                    }
                }
            }
        }
        return problemDescriptors.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }

    private static @Nullable LSPDocumentBase getOpenedOrClosedDocument(LanguageServerWrapper ls, URI fileUri) {
        var openedDocument = ls.getOpenedDocument(fileUri);
        if (openedDocument != null) {
            return openedDocument;
        }
        return ls.getClosedDocument(fileUri, false);
    }

    protected boolean acceptFile(@NotNull PsiFile file,
                                 @NotNull InspectionManager manager,
                                 boolean isOnTheFly) {
        if (isOnTheFly) {
            return false;
        }
        List<PsiFile> files = file.getViewProvider().getAllFiles();
        if (files.size() > 1 && files.indexOf(file) > 0) {
            return false;
        }
        return true;
    }
}

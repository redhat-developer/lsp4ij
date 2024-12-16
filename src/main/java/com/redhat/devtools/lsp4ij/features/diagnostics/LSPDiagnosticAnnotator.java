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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.features.AbstractLSPExternalAnnotator;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * Intellij {@link ExternalAnnotator} implementation which get the current LSP diagnostics for a given file and translate
 * them into Intellij {@link com.intellij.lang.annotation.Annotation}.
 */
public class LSPDiagnosticAnnotator extends AbstractLSPExternalAnnotator<Boolean, Boolean> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPDiagnosticAnnotator.class);

    private static final Key<Boolean> APPLIED_KEY = Key.create("lsp.diagnostic.annotator.applied");

    private final boolean forBulkInspection;

    public LSPDiagnosticAnnotator() {
        this(false);
    }

    LSPDiagnosticAnnotator(boolean forBulkInspection) {
        super(APPLIED_KEY);
        this.forBulkInspection = forBulkInspection;
    }

    @Nullable
    @Override
    public Boolean collectInformation(@NotNull PsiFile file) {
        if (!LanguageServersRegistry.getInstance().isFileSupported(file)) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    @Nullable
    @Override
    public Boolean collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        return collectInformation(file);
    }

    @Override
    public @Nullable Boolean doAnnotate(Boolean result) {
        return result;
    }

    @Override
    public void doApply(@NotNull PsiFile file, Boolean applyAnnotator, @NotNull AnnotationHolder holder) {
        if (!applyAnnotator) {
            return;
        }
        URI fileUri = LSPIJUtils.toUri(file);
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
            return;
        }
        Document document = LSPIJUtils.getDocument(virtualFile);
        if (document == null) {
            return;
        }

        // Loop for language server which report diagnostics for the given file
        var servers = LanguageServiceAccessor.getInstance(file.getProject()).getStartedServers();
        for (var ls : servers) {
            // TODO: Is there not a way to check whether the server is valid for this file here?
            // Force a connection for batch inspection
            boolean needsDisconnect = forBulkInspection && connectIfNecessary(ls, file);
            try {
                // Check again as the connection above may not have succeeded
                if (ls.isConnectedTo(fileUri)) {
                    LSPVirtualFileData data = ls.getLSPVirtualFileData(fileUri);
                    if (data != null) {
                        // The file is mapped with the current language server
                        var ds = data.getDiagnosticsForServer();
                        // Loop for LSP diagnostics to transform it to Intellij annotation.
                        // TODO: When running for bulk inspection, we need to wait for diagnostics here; not sure how to
                        //  do that since it seems it's all based on publishDiagnostics notifications
                        for (Diagnostic diagnostic : ds.getDiagnostics()) {
                            ProgressManager.checkCanceled();
                            createAnnotation(diagnostic, document, file, ds, holder);
                        }
                    }
                }
            } finally {
                if (needsDisconnect) {
                    ls.disconnect(fileUri, false);
                }
            }
        }
    }

    private boolean connectIfNecessary(@NotNull LanguageServerWrapper ls, @NotNull PsiFile file) {
        // Not while indexing
        if (ProjectIndexingManager.canExecuteLSPFeature(file) != ExecuteLSPFeatureStatus.NOW) {
            return false;
        }

        URI fileUri = LSPIJUtils.toUri(file);
        if (!ls.isConnectedTo(fileUri)) {
            VirtualFile virtualFile = file.getVirtualFile();
            Document document = virtualFile != null ? LSPIJUtils.getDocument(virtualFile) : null;
            if (document != null) {
                CompletableFuture<LanguageServer> connectionResultFuture = ls.connect(virtualFile, document);

                try {
                    // Wait until the future is finished and stop the wait if there are some ProcessCanceledException.
                    waitUntilDone(connectionResultFuture, file);
                } catch (ProcessCanceledException ignore) {
                    //Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
                    //TODO delete block when minimum required version is 2024.2
                    return false;
                } catch (CancellationException ignore) {
                    return false;
                } catch (ExecutionException e) {
                    LOGGER.error("Error while connecting file '{}' to language server '{}'.", file, ls, e);
                    return false;
                }

                ProgressManager.checkCanceled();
                if (isDoneNormally(connectionResultFuture)) {
                    LanguageServer languageServer = connectionResultFuture.getNow(null);
                    return languageServer != null;
                }
            }
        }

        return false;
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
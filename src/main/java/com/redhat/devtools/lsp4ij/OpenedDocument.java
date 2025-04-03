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
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticsForServer;
import org.eclipse.lsp4j.Diagnostic;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * LSP opened document for a given language server.
 *
 * @author Angelo ZERR
 */
public class OpenedDocument extends LSPDocumentBase {

    private volatile Alarm refreshDiagnosticsAlarm = null;

    private final VirtualFile file;

    private final LSPDiagnosticsForServer diagnosticsForServer;

    private final DocumentContentSynchronizer synchronizer;
    private long updatedDiagnosticsTime; // time when diagnostics has been updated
    private long displayingDiagnosticsTime; // time when diagnostics has been displayed with the LSPDiagnosticAnnotator.

    public OpenedDocument(@NotNull LanguageServerItem languageServer,
                          @NotNull VirtualFile file,
                          @NotNull DocumentContentSynchronizer synchronizer) {
        this.file = file;
        this.synchronizer = synchronizer;
        this.diagnosticsForServer = new LSPDiagnosticsForServer(languageServer,file);
    }

    /**
     * Returns the virtual file.
     *
     * @return the virtual file.
     */
    public VirtualFile getFile() {
        return file;
    }

    public DocumentContentSynchronizer getSynchronizer() {
        return synchronizer;
    }

    public LSPDiagnosticsForServer getDiagnosticsForServer() {
        return diagnosticsForServer;
    }

    @Override
    public boolean updateDiagnostics(@NotNull List<Diagnostic> diagnostics) {
        updatedDiagnosticsTime = System.currentTimeMillis();
        if (diagnosticsForServer.update(diagnostics)) {
            // LSP diagnostics has changed
            final PsiFile psiFile = LSPIJUtils.getPsiFile(file, diagnosticsForServer.getClientFeatures().getProject());
            if (psiFile != null) {
                // Trigger Intellij validation to execute
                // {@link LSPDiagnosticAnnotator}.
                // which translates LSP Diagnostics into Intellij Annotation
                final long currentDisplayingDiagnosticsTime = getDisplayingDiagnosticsTime();
                LSPFileSupport.getSupport(psiFile).restartDaemonCodeAnalyzerWithDebounce(() -> {
                    if (!isDiagnosticsMustBeRefreshed(currentDisplayingDiagnosticsTime)) {
                        throw new CancellationException();
                    }
                });
            }
            return true;
        }
        return false;
    }

    @Override
    public Collection<Diagnostic> getDiagnostics() {
        return diagnosticsForServer.getDiagnostics();
    }

    public void markAsDisplayingDiagnostics() {
        displayingDiagnosticsTime = System.currentTimeMillis();
    }

    boolean isDiagnosticsMustBeRefreshed(long displayingDiagnosticsTime) {
        long lastDisplayingDiagnosticsTime = getDisplayingDiagnosticsTime();
        return displayingDiagnosticsTime !=  lastDisplayingDiagnosticsTime// is diagnostics are already displayed with LSPDiagnosticAnnotator ?
                || updatedDiagnosticsTime < lastDisplayingDiagnosticsTime; // is update of diagnostics has been done after the last display of diagnostics
    }

    long getDisplayingDiagnosticsTime() {
        return displayingDiagnosticsTime;
    }
}

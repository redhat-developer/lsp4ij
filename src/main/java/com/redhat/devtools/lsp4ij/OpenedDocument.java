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
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticsApplier;
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticsForServer;
import org.eclipse.lsp4j.Diagnostic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LSP opened document for a given language server.
 *
 * @author Angelo ZERR
 */
public class OpenedDocument extends LSPDocumentBase {

    private final VirtualFile file;

    private final LSPDiagnosticsForServer diagnosticsForServer;

    private final @Nullable DocumentContentSynchronizer synchronizer;

    // Whether the next diagnostics update refreshes the file's marks though it changes none of this document's
    // diagnostics: a connected document may replace one without a connection, whose diagnostics the file is marked
    // with until this document is reported for the first time
    private final AtomicBoolean refreshOnNextUpdate;

    public OpenedDocument(@NotNull LanguageServerItem languageServer,
                          @NotNull VirtualFile file,
                          @Nullable DocumentContentSynchronizer synchronizer) {
        this.file = file;
        this.synchronizer = synchronizer;
        this.refreshOnNextUpdate = new AtomicBoolean(synchronizer != null);
        this.diagnosticsForServer = new LSPDiagnosticsForServer(languageServer, file, synchronizer);
    }

    /**
     * Returns the virtual file.
     *
     * @return the virtual file.
     */
    public VirtualFile getFile() {
        return file;
    }

    public @Nullable DocumentContentSynchronizer getSynchronizer() {
        return synchronizer;
    }

    public LSPDiagnosticsForServer getDiagnosticsForServer() {
        return diagnosticsForServer;
    }

    @Override
    public boolean updateDiagnostics(@NotNull String identifier,
                                     @NotNull List<Diagnostic> diagnostics,
                                     @Nullable Integer version) {
        if (version != null && synchronizer != null) {
            if (version != synchronizer.getVersion()) {
                return false;
            }
        }
        boolean changed = diagnosticsForServer.update(identifier, diagnostics);
        if (changed | refreshOnNextUpdate.getAndSet(false)) {
            // LSP diagnostics has changed, or this connected document is reported for the first time

            // Schedule reactive diagnostics refresh (apply immediately without daemon restart)
            var project = diagnosticsForServer.getClientFeatures().getProject();
            LSPDiagnosticsApplier.getInstance(project).scheduleRefresh(file, synchronizer != null ? synchronizer.getDocument() : null);
        }
        return changed;
    }

    @Override
    public Collection<Diagnostic> getDiagnostics() {
        return diagnosticsForServer.getDiagnostics();
    }

    @Override
    public boolean hasErrors() {
        return diagnosticsForServer.hasErrors();
    }
}

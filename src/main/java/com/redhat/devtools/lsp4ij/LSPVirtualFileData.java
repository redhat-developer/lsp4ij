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
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticsForServer;
import org.eclipse.lsp4j.Diagnostic;

import java.util.List;

/**
 * LSP data stored in {@link VirtualFile} which are used by some LSP operations.
 *
 * @author Angelo ZERR
 */
public class LSPVirtualFileData {

    private final VirtualFile file;

    private final LSPDiagnosticsForServer diagnosticsForServer;

    private final DocumentContentSynchronizer synchronizer;


    public LSPVirtualFileData(LanguageServerWrapper languageServerWrapper, VirtualFile file, DocumentContentSynchronizer synchronizer) {
        this.file = file;
        this.synchronizer = synchronizer;
        this.diagnosticsForServer = new LSPDiagnosticsForServer(languageServerWrapper,file);
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

    public void updateDiagnostics(List<Diagnostic> diagnostics) {
        diagnosticsForServer.update(diagnostics);
    }
}

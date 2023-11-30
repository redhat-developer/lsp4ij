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
package com.redhat.devtools.intellij.lsp4ij;

import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.intellij.lsp4ij.operations.diagnostics.LSPDiagnosticsForServer;
import com.redhat.devtools.intellij.lsp4ij.operations.documentLink.LSPDocumentLinkForServer;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentLink;

import java.util.List;

/**
 * LSP data stored in {@link VirtualFile} which are used by some LSP operations.
 *
 * @author Angelo ZERR
 */
public class LSPVirtualFileData {

    private final LSPDiagnosticsForServer diagnosticsForServer;

    private final LSPDocumentLinkForServer documentLinkForServer;

    private final DocumentContentSynchronizer synchronizer;

    public LSPVirtualFileData(LanguageServerWrapper languageServerWrapper, VirtualFile file, DocumentContentSynchronizer synchronizer) {
        this.synchronizer = synchronizer;
        this.diagnosticsForServer = new LSPDiagnosticsForServer(languageServerWrapper,file);
        this.documentLinkForServer = new LSPDocumentLinkForServer(languageServerWrapper, file);
    }

    public DocumentContentSynchronizer getSynchronizer() {
        return synchronizer;
    }

    public LSPDiagnosticsForServer getDiagnosticsForServer() {
        return diagnosticsForServer;
    }

    public LSPDocumentLinkForServer getDocumentLinkForServer() {
        return documentLinkForServer;
    }

    public void updateDiagnostics(List<Diagnostic> diagnostics) {
        diagnosticsForServer.update(diagnostics);
    }

    public void updateDocumentLink(List<DocumentLink> documentLinks) {
        documentLinkForServer.update(documentLinks);
    }
}

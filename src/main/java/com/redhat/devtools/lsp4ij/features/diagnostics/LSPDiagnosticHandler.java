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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.ClosedDocument;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.OpenedDocument;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.function.Consumer;

/**
 * Utility class which receive LSP {@link PublishDiagnosticsParams}
 * from a language server and refresh the Annotation of the Intellij editor.
 *
 * @author Angelo ZERR
 */
public class LSPDiagnosticHandler implements Consumer<PublishDiagnosticsParams> {

    private final LanguageServerWrapper languageServerWrapper;

    public LSPDiagnosticHandler(LanguageServerWrapper languageServerWrapper) {
        this.languageServerWrapper = languageServerWrapper;
    }

    @Override
    public void accept(PublishDiagnosticsParams params) {
        Project project = languageServerWrapper.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }
        updateDiagnostics(params, project);
    }

    private void updateDiagnostics(@NotNull PublishDiagnosticsParams params, @NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }
        VirtualFile file = FileUriSupport.findFileByUri(params.getUri(), languageServerWrapper.getClientFeatures());
        if (file == null) {
            return;
        }

        // Update LSP diagnostic reported by the language server id
        URI fileURI = LSPIJUtils.toUri(file);
        OpenedDocument openedDocument = languageServerWrapper.getOpenedDocument(fileURI);
        if (openedDocument != null) {
            // Update diagnostics for opened file
            synchronized (openedDocument) {
                openedDocument.updateDiagnostics(params.getDiagnostics());
            }
        } else {
            // Update diagnostics for closed file
            ClosedDocument closedDocument = languageServerWrapper.getClosedDocument(fileURI, true);
            closedDocument.updateDiagnostics(params.getDiagnostics());
        }
    }

}
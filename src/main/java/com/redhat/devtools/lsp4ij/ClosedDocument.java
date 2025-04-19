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
package com.redhat.devtools.lsp4ij;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticUtils.isDiagnosticsChanged;

/**
 * LSP closed document for a given language server.
 *
 * @author Angelo ZERR
 */
public class ClosedDocument extends LSPDocumentBase {

    private List<Diagnostic> diagnostics;
    private boolean hasErrors;

    @Override
    public boolean updateDiagnostics(@NotNull String identifier,
                                     @NotNull List<Diagnostic> diagnostics) {
        boolean changed = isDiagnosticsChanged(this.diagnostics != null ? this.diagnostics : Collections.emptyList(), diagnostics);
        this.diagnostics = diagnostics;
        hasErrors = diagnostics
                .stream()
                .anyMatch(diagnostic -> diagnostic.getSeverity() != null && diagnostic.getSeverity() == DiagnosticSeverity.Error);
        return changed;

    }

    @Override
    public Collection<Diagnostic> getDiagnostics() {
        return diagnostics != null ? diagnostics : Collections.emptyList();
    }

    @Override
    public boolean hasErrors() {
        return hasErrors;
    }
}

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
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticUtils.isDiagnosticsChanged;

/**
 * LSP closed document for a given language server.
 *
 * @author Angelo ZERR
 */
public class ClosedDocument extends LSPDocumentBase {

    private final Map<String, List<Diagnostic>> diagnosticsPerIdentifier = new HashMap<>();
    // Publish the diagnostics and their derived error status together.
    private volatile DiagnosticSnapshot snapshot = new DiagnosticSnapshot(List.of(), false);

    @Override
    public synchronized boolean updateDiagnostics(@NotNull String identifier,
                                     @NotNull List<Diagnostic> diagnostics, @Nullable Integer version) {
        if (diagnostics.isEmpty()) {
            diagnosticsPerIdentifier.remove(identifier);
        } else {
            diagnosticsPerIdentifier.put(identifier, List.copyOf(diagnostics));
        }
        var combined = diagnosticsPerIdentifier.values().stream().flatMap(List::stream).distinct().toList();
        boolean changed = isDiagnosticsChanged(snapshot.diagnostics(), combined);
        boolean hasErrors = combined
                .stream()
                .anyMatch(diagnostic -> diagnostic.getSeverity() != null && diagnostic.getSeverity() == DiagnosticSeverity.Error);
        snapshot = new DiagnosticSnapshot(combined, hasErrors);
        return changed;
    }

    @Override
    public Collection<Diagnostic> getDiagnostics() {
        return snapshot.diagnostics();
    }

    @Override
    public boolean hasErrors() {
        return snapshot.hasErrors();
    }

    private record DiagnosticSnapshot(List<Diagnostic> diagnostics, boolean hasErrors) {
    }
}

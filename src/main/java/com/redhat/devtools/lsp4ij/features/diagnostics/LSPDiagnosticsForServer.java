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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.features.codeAction.quickfix.LSPLazyCodeActions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.util.Ranges;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * LSP diagnostics holder for a file reported by a language server. This class holds:
 *
 * <ul>
 *     <li>the current LSP diagnostics reported by the language server.</li>
 *     <li>load for each diagnostic the available LSP code actions (QuickFix)</li>
 * </ul>
 *
 * @author Angelo ZERR
 */
public class LSPDiagnosticsForServer {

    private record DiagnosticData(Range range, List<Diagnostic> diagnostics) {};

    private final LanguageServerItem languageServer;

    private final VirtualFile file;

    // Map which contains all current diagnostics (as key) and future which load associated quick fixes (as value)
    private Map<Diagnostic, LSPLazyCodeActions> diagnostics;

    public LSPDiagnosticsForServer(LanguageServerItem languageServer, VirtualFile file) {
        this.languageServer = languageServer;
        this.file = file;
        this.diagnostics = Collections.emptyMap();
    }

    public LSPClientFeatures getClientFeatures() {
        return languageServer.getClientFeatures();
    }
    /**
     * Update the new LSP published diagnosics.
     *
     * @param diagnostics the new LSP published diagnosics
     */
    public void update(List<Diagnostic> diagnostics) {
        // initialize diagnostics map
        this.diagnostics = toMap(diagnostics, this.diagnostics);
    }

    private Map<Diagnostic, LSPLazyCodeActions> toMap(List<Diagnostic> diagnostics,
                                                      Map<Diagnostic, LSPLazyCodeActions> existingDiagnostics) {
        // Collect quick fixes from LSP code action
        Map<Diagnostic, LSPLazyCodeActions> map = new HashMap<>(diagnostics.size());
        // Sort diagnostics by range
        List<Diagnostic> sortedDiagnostics = diagnostics
                .stream()
                .sorted((d1, d2) -> {
                    if (Ranges.containsRange(d1.getRange(), d2.getRange())) {
                        return -1;
                    }
                    return 1;
                })
                .toList();
        // Group diagnostics by covered range
        List<DiagnosticData> diagnosticsGroupByCoveredRange = new ArrayList<>();
        for (Diagnostic diagnostic : sortedDiagnostics) {
            DiagnosticData data = getDiagnosticWhichCoversTheRange(diagnostic.getRange(), diagnosticsGroupByCoveredRange);
            if (data != null) {
                data.diagnostics().add(diagnostic);
            } else {
                List<Diagnostic> list = new ArrayList<>();
                list.add(diagnostic);
                Position start = diagnostic.getRange().getStart();
                Position end = diagnostic.getRange().getEnd();
                data = new DiagnosticData(new Range(new Position(start.getLine(), start.getCharacter()),
                        new Position(end.getLine(), end.getCharacter())), list);
                diagnosticsGroupByCoveredRange.add(data);
            }
        }
        // Associate each diagnostic with the list of code actions to load for a given range
        for (DiagnosticData data : diagnosticsGroupByCoveredRange) {
            var action = new LSPLazyCodeActions(data.diagnostics(), file, languageServer);
            data.diagnostics()
                    .forEach(d -> {
                        // Get the existing LSP lazy code actions for the current diagnostic
                        LSPLazyCodeActions actions = existingDiagnostics != null ? existingDiagnostics.get(d) : null;
                        if (actions != null) {
                            // cancel the LSP textDocument/codeAction request if needed
                            actions.cancel();
                        }
                        map.put(d, action);
                    });

        }
        return map;
    }

    @Nullable
    private static DiagnosticData getDiagnosticWhichCoversTheRange(Range diagnosticRange, List<DiagnosticData> diagnosticsGroupByCoveredRange) {
        for (DiagnosticData data : diagnosticsGroupByCoveredRange) {
            if (Ranges.containsRange(data.range(), diagnosticRange)) {
                return data;
            }
        }
        return null;
    }

    /**
     * Returns the current diagnostics for the file reported by the language server.
     *
     * @return the current diagnostics for the file reported by the language server.
     */
    public Set<Diagnostic> getDiagnostics() {
        return diagnostics.keySet();
    }

    /**
     * Returns Intellij quickfixes for the given diagnostic if there available.
     *
     * @param diagnostic the diagnostic.
     * @param file
     * @return Intellij quickfixes for the given diagnostic if there available.
     */
    public List<IntentionAction> getQuickFixesFor(@NotNull Diagnostic diagnostic,
                                                  @NotNull PsiFile file) {
        boolean codeActionSupported = isCodeActionSupported(languageServer.getServerWrapper(), file);
        if (!codeActionSupported || diagnostics.isEmpty()) {
            return Collections.emptyList();
        }
        LSPLazyCodeActions codeActions = diagnostics.get(diagnostic);
        return codeActions != null ? codeActions.getCodeActions() : Collections.emptyList();
    }

    private static boolean isCodeActionSupported(@NotNull LanguageServerWrapper languageServerWrapper,
                                                 @NotNull PsiFile file) {
        if (!languageServerWrapper.isActive() || languageServerWrapper.isStopping()) {
            // This use-case comes from when a diagnostics is published and the language server is stopped
            // We cannot use here languageServerWrapper.getServerCapabilities() otherwise it will restart the language server.
            return false;
        }
        return languageServerWrapper.getClientFeatures().getCodeActionFeature().isCodeActionSupported(file);
    }

}

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
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.util.Ranges;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticUtils.isDiagnosticsChanged;

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

    private boolean hasErrors;

    private record DiagnosticData(Range range, List<Diagnostic> diagnostics) {};

    private final LanguageServerItem languageServer;

    private final @Nullable VirtualFile file;

    // Map which contains all current diagnostics (as key) and future which load associated quick fixes (as value)
    private @NotNull Map<Diagnostic, LSPLazyCodeActions> diagnostics;

    private @Nullable Map<String /* diagnostic identifier */, Collection<Diagnostic>> diagnosticsPerIdentifier;

    private String firstIdentifier;

    public LSPDiagnosticsForServer(@NotNull LanguageServerItem languageServer,
                                   @Nullable VirtualFile file) {
        this.languageServer = languageServer;
        this.file = file;
        this.diagnostics = Collections.emptyMap();
    }

    /**
     * Update the new LSP published diagnostics.
     *
     * @param identifier the diagnostic identifier
     * @param diagnostics the new LSP published/pulled diagnostics
     */
    public boolean update(@NotNull String identifier,
                          @NotNull List<Diagnostic> diagnostics) {
        if (diagnosticsPerIdentifier == null) {
            // At this step there are
            // - no cached diagnostics
            // - cached diagnostics which was only published or only pulled
            if (firstIdentifier == null) {
                // Store the first diagnostic identifier which updates the diagnostics cache (ex: lsp4ij.publish, lsp4ij.pull)
                firstIdentifier = identifier;
            } else {
                // If the new identifier is different from the first diagnostic identifier
                // we use diagnosticsPerIdentifier which maintains diagnostics for each identifier (pull, publish)
                if (!firstIdentifier.equals(identifier)) {
                    // We need to have several diagnostic cache
                    diagnosticsPerIdentifier = new HashMap<>();
                    diagnosticsPerIdentifier.put(firstIdentifier, this.diagnostics.keySet());
                }
            }
        }

        Collection<Diagnostic> oldDiagnostics = getOldDiagnostics();
        Collection<Diagnostic> newDiagnostics = getNewDiagnostics(identifier, diagnostics);
        boolean changed = isDiagnosticsChanged(oldDiagnostics, newDiagnostics);
        // initialize diagnostics map
        this.diagnostics = toMap(newDiagnostics, this.diagnostics);
        if (diagnosticsPerIdentifier != null) {
            // Cache must manage several diagnostic identifier (pull, publish), we store the new diagnostics in the cache for the given identifier
            diagnosticsPerIdentifier.put(identifier, diagnostics);
        }
        return changed;
    }

    private Collection<Diagnostic> getOldDiagnostics() {
        return this.diagnostics != null ? this.diagnostics.keySet() : Collections.emptySet();
    }

    private Collection<Diagnostic> getNewDiagnostics(@NotNull String identifier,
                                                     @NotNull List<Diagnostic> diagnostics) {
        if (diagnosticsPerIdentifier == null) {
            // Just one diagnostic identifier, use the new diagnostics
            return diagnostics;
        }
        // The cache manages several diagnostic identifiers (pull, publish)
        // Merge of the new diagnostics list with diagnostics stored for other identifiers.
        Set<Diagnostic> newDiagnostics = new HashSet<>(diagnostics);
        for(var entry : diagnosticsPerIdentifier.entrySet()) {
            if (!entry.getKey().equals(identifier)) {
                newDiagnostics.addAll(entry.getValue());
            }
        }
        return newDiagnostics;
    }

    private Map<Diagnostic, LSPLazyCodeActions> toMap(@NotNull Collection<Diagnostic> diagnostics,
                                                      @NotNull Map<Diagnostic, LSPLazyCodeActions> existingDiagnostics) {
        hasErrors = false;
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
            if (!hasErrors && diagnostic.getSeverity() != null && diagnostic.getSeverity() == DiagnosticSeverity.Error) {
                hasErrors = true;
            }
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
    public Collection<Diagnostic> getDiagnostics() {
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

    /**
     * Returns the client features.
     *
     * @return the client features.
     */
    public LSPClientFeatures getClientFeatures() {
        return languageServer.getClientFeatures();
    }

    public boolean hasErrors() {
        return hasErrors;
    }
}

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
package com.redhat.devtools.intellij.lsp4ij.operations.diagnostics;

import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.intellij.lsp4ij.operations.codeactions.LSPLazyCodeActionIntentionAction;
import com.redhat.devtools.intellij.lsp4ij.operations.codeactions.LSPLazyCodeActions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.ServerCapabilities;

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

    private final LanguageServerWrapper languageServerWrapper;

    private final VirtualFile file;

    // Map which contains all current diagnostics (as key) and future which load associated quick fixes (as value)
    private Map<Diagnostic, LSPLazyCodeActions> diagnostics;

    public LSPDiagnosticsForServer(LanguageServerWrapper languageServerWrapper, VirtualFile file) {
        this.languageServerWrapper = languageServerWrapper;
        this.file = file;
        this.diagnostics = Collections.emptyMap();
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

    private Map<Diagnostic, LSPLazyCodeActions> toMap(List<Diagnostic> diagnostics, Map<Diagnostic, LSPLazyCodeActions> existingsDiagnostics) {
        Map<Diagnostic, LSPLazyCodeActions> map = new HashMap<>(diagnostics.size());
        for (Diagnostic diagnostic : diagnostics) {
            // Get the existing LSP lazy code actions for the current diagnostic
            LSPLazyCodeActions actions = existingsDiagnostics != null ? existingsDiagnostics.get(diagnostic) : null;
            if (actions != null) {
                // cancel the LSP textDocument/codeAction request if needed
                actions.cancel();
            }
            map.put(diagnostic, new LSPLazyCodeActions(diagnostic, file, languageServerWrapper));
        }
        return map;
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
     * @return Intellij quickfixes for the given diagnostic if there available.
     */
    public List<LSPLazyCodeActionIntentionAction> getQuickFixesFor(Diagnostic diagnostic) {
        boolean codeActionSupported = isCodeActionSupported(languageServerWrapper);
        if (!codeActionSupported || diagnostics.isEmpty()) {
            return Collections.emptyList();
        }
        LSPLazyCodeActions codeActions = diagnostics.get(diagnostic);
        return codeActions != null ? codeActions.getCodeActions() : Collections.emptyList();
    }

    private static boolean isCodeActionSupported(LanguageServerWrapper languageServerWrapper) {
        if (!languageServerWrapper.isActive() || languageServerWrapper.isStopping()) {
            // This use-case comes from when a diagnostics is published and the language server is stopped
            // We cannot use here languageServerWrapper.getServerCapabilities() otherwise it will restart the language server.
            return false;
        }
        ServerCapabilities serverCapabilities = languageServerWrapper.getServerCapabilities();
        return serverCapabilities != null && LSPIJUtils.hasCapability(serverCapabilities.getCodeActionProvider());
    }

}

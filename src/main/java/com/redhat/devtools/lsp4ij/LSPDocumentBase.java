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
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Base class for LSP opened/closed document for a given language server.
 */
public abstract class LSPDocumentBase {

    /**
     * Update the diagnostics
     *
     * @param diagnostics the new diagnostics.
     */
    public abstract void updateDiagnostics(@NotNull List<Diagnostic> diagnostics);

    /**
     * Returns the current diagnostics for the file reported by the language server.
     *
     * @return the current diagnostics for the file reported by the language server.
     */
    public abstract Collection<Diagnostic> getDiagnostics();
}

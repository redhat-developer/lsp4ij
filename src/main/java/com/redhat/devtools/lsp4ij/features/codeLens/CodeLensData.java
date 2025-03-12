/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeLens;

import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.eclipse.lsp4j.CodeLens;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Code lens Data
 *
 * @param codeLens               the LSP codeLens
 * @param languageServer         the language server which has created the codeLens.
 * @param resolvedCodeLensFuture the codeLens/resolve future and null otherwise.
 */
class CodeLensData {

    private @NotNull CodeLens codeLens;
    private final @NotNull LanguageServerItem languageServer;
    private @Nullable boolean toResolve;
    private CompletableFuture<CodeLens> resolveCodeLensFuture;

    public CodeLensData(@NotNull CodeLens codeLens,
                        @NotNull LanguageServerItem languageServer,
                        @Nullable boolean toResolve) {
        this.codeLens = codeLens;
        this.languageServer = languageServer;
        this.toResolve = toResolve;
    }

    public @NotNull CodeLens getCodeLens() {
        return codeLens;
    }

    public @NotNull LanguageServerItem getLanguageServer() {
        return languageServer;
    }

    public @Nullable boolean isToResolve() {
        return toResolve;
    }

    public CompletableFuture<CodeLens> resolveCodeLens() {
        if (resolveCodeLensFuture != null) {
            return resolveCodeLensFuture;
        }
        resolveCodeLensFuture = languageServer
                .getTextDocumentService()
                .resolveCodeLens(codeLens);
        resolveCodeLensFuture
                .thenAccept(cl -> {
                    if(cl != null) {
                        codeLens = cl;
                        toResolve = false;
                    }
                });
        return resolveCodeLensFuture;
    }
}

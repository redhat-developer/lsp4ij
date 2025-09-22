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

import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.CodeLens;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Code lens Data
 */
public class CodeLensData {

    private final @NotNull LanguageServerItem languageServer;
    private @NotNull CodeLens codeLens;
    private boolean toResolve;
    private CompletableFuture<CodeLens> resolveCodeLensFuture;
    private CodeLensDataResult result;

    public CodeLensData(@NotNull CodeLens codeLens,
                        @NotNull LanguageServerItem languageServer,
                        boolean toResolve) {
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

    public boolean isToResolve() {
        return toResolve;
    }

    public CompletableFuture<CodeLens> resolveCodeLens() {
        if (resolveCodeLensFuture != null) {
            return resolveCodeLensFuture;
        }
        // Use a CancellationSupport to catch errors like ResponseErrorException
        // and ignore theme
        CancellationSupport cancellationSupport = new CancellationSupport();
        resolveCodeLensFuture = cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .resolveCodeLens(codeLens),
                languageServer,
                LSPRequestConstants.CODE_LENS_RESOLVE);
        resolveCodeLensFuture
                .thenAccept(cl -> {
                    // mark codelens as resolved
                    if (cl != null) {
                        codeLens = cl;
                        toResolve = false;
                        result.decrementResolve();
                    }
                });
        return resolveCodeLensFuture;
    }

    void setResult(CodeLensDataResult result) {
        this.result = result;
    }
}

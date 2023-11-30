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
package com.redhat.devtools.intellij.lsp4ij.client;

import com.intellij.openapi.progress.ProgressIndicator;
import com.redhat.devtools.intellij.lsp4ij.internal.PromiseToCompletableFuture;

import java.util.function.Function;

/**
 * LSP completable future which execute a given function code in a non blocking reading action promise.
 */
public class LSPCompletableFuture<R> extends PromiseToCompletableFuture<R> {
    private final IndexAwareLanguageClient languageClient;

    public LSPCompletableFuture(Function<ProgressIndicator, R> code, String progressTitle, IndexAwareLanguageClient languageClient, Object coalesceBy) {
        super(code, progressTitle, languageClient.getProject(), languageClient, coalesceBy);
        this.languageClient = languageClient;
        init();
    }

    @Override
    protected ProgressIndicator createProgressIndicator() {
        return new LSPProgressIndicator(languageClient);
    }

}

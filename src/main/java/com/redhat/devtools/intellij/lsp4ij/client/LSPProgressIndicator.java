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

import com.intellij.openapi.progress.StandardProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.redhat.devtools.intellij.lsp4ij.client.LanguageClientImpl;

/**
 * LSP progress indicator which check that language client is not stopped.
 */
class LSPProgressIndicator extends AbstractProgressIndicatorBase implements StandardProgressIndicator {

    private final LanguageClientImpl languageClient;

    public LSPProgressIndicator(LanguageClientImpl languageClient) {
        this.languageClient = languageClient;
    }
    @Override
    public final boolean isCanceled() {
        return super.isCanceled() || languageClient.isDisposed();
    }
}

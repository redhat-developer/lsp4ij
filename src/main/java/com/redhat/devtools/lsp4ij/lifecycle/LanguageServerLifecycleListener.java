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
package com.redhat.devtools.lsp4ij.lifecycle;

import com.intellij.openapi.Disposable;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Language server lifecycle listener
 *
 * @author Angelo ZERR
 */
@ApiStatus.Internal
public interface LanguageServerLifecycleListener extends Disposable {

    /**
     * Callback called when the given language server status changed.
     *
     * @param languageServer the language server.
     */
    void handleStatusChanged(@NotNull LanguageServerWrapper languageServer);

    /**
     * Callback called when a new LSP message (request/response) comes from the given language server status changed.
     *
     * @param message the LSP request/response message.
     * @param messageConsumer the message consumer.
     * @param languageServer the language server.
     */
    void handleLSPMessage(@NotNull Message message,
                          @NotNull MessageConsumer messageConsumer,
                          @NotNull LanguageServerWrapper languageServer);

    /**
     * Callback called when there is an error with the given language.
     *
     * @param languageServer the language server.
     * @param exception the error.
     */
    void handleError(@NotNull LanguageServerWrapper languageServer,
                     @NotNull Throwable exception);

}

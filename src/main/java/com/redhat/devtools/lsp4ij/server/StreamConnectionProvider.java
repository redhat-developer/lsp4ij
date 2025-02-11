/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import com.intellij.openapi.vfs.VirtualFile;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Stream connection provider API used to:
 * <ul>
 *     <li>start a language server.</li>
 *     <li>get the stream of LSP requests, responses and notifications.</li>
 * </ul>
 */
public interface StreamConnectionProvider {

    void start() throws CannotStartProcessException;

    InputStream getInputStream();

    OutputStream getOutputStream();


    default void addLogErrorHandler(LanguageServerLogErrorHandler handler) {

    }

    default void addUnexpectedServerStopHandler(Runnable handler) {

    }

    /**
     * User provided initialization options.
     */
    default Object getInitializationOptions(VirtualFile rootUri) {
        return null;
    }

    /**
     * Returns an object that describes the experimental features supported
     * by the client.
     *
     * @return an object whose fields represent the different experimental features
     * supported by the client.
     * @implNote The returned object gets serialized by LSP4J, which itself uses
     * GSon, so a GSon object can work too.
     * @since 0.12
     */
    default Object getExperimentalFeaturesPOJO() {
        return null;
    }

    /**
     * Provides trace level to be set on language server initialization.<br>
     * Legal values: "off" | "messages" | "verbose".
     *
     * @param rootUri the workspace root URI.
     * @return the initial trace level to set
     * @see "https://microsoft.github.io/language-server-protocol/specification#initialize"
     */
    default String getTrace(VirtualFile rootUri) {
        return "off"; //$NON-NLS-1$
    }

    void stop();

    /**
     * Allows to hook custom behavior on messages.
     *
     * @param message        a message.
     * @param languageServer the language server receiving/sending the message.
     * @param rootUri        the root Uri.
     */
    default void handleMessage(Message message, LanguageServer languageServer, VirtualFile rootUri) {
    }

    /**
     * Returns true if the connection provider is alive and false otherwise.
     *
     * @return true if the connection provider is alive and false otherwise.
     */
    default boolean isAlive() {
        return true;
    }

    /**
     * Ensure that process is alive.
     *
     * @throws CannotStartProcessException if process is not alive.
     */
    default void ensureIsAlive() throws CannotStartProcessException {
        if (!isAlive()) {
            throw new CannotStartProcessException("Unable to start language server: " + this.toString()); //$NON-NLS-1$
        }
    }

}

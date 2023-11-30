package com.redhat.devtools.intellij.lsp4ij.server;

import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.services.LanguageServer;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

public interface StreamConnectionProvider {

    void start() throws CannotStartProcessException;

    InputStream getInputStream();

    OutputStream getOutputStream();

    /**
     * Returns the {@link InputStream} connected to the error output of the process
     * running the language server. If the error output is redirected to standard
     * output it returns <code>null</code>.
     *
     * @return the {@link InputStream} connected to the error output of the language
     * server process or <code>null</code> if it's redirected or process not
     * yet started.
     */
    @Nullable
    InputStream getErrorStream();

    /**
     * User provided initialization options.
     */
    default Object getInitializationOptions(URI rootUri) {
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
    default String getTrace(URI rootUri) {
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
    default void handleMessage(Message message, LanguageServer languageServer, URI rootUri) {
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

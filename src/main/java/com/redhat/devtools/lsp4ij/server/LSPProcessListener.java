/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * IntelliJ process listener used to read the process of the started language server in an InputStream
 * and write Std error in the LSP console tab log.
 */
class LSPProcessListener implements ProcessListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPProcessListener.class);

    private final PipedOutputStream outputStream;
    private final OutputStreamWriter outputStreamWriter;
    private final PipedInputStream inputStream;
    private final @NotNull OSProcessStreamConnectionProvider provider;
    private int nbMessagesForEmulatingMissingHeader;

    public LSPProcessListener(@NotNull OSProcessStreamConnectionProvider provider) throws IOException {
        this.outputStream = new PipedOutputStream();
        this.outputStreamWriter = new OutputStreamWriter(this.outputStream, StandardCharsets.UTF_8);
        this.inputStream = new PipedInputStream(this.outputStream);
        this.provider = provider;
    }

    private static String removeEndLine(String text) {
        if (text.endsWith("\r\n")) {
            return text.substring(0, text.length() - 2);
        }
        if (text.endsWith("\n")) {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }

    @NotNull
    public final InputStream getInputStream() {
        return this.inputStream;
    }

    //private int i = 0;
    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (ProcessOutputType.isStdout(outputType)) {
            // Flush the output
            try {
                synchronized (this.outputStreamWriter) {
                    this.outputStreamWriter.write(event.getText());
                    this.outputStreamWriter.flush();
                }
                // Uncomment this code to emulate the "Missing header" problem in StreamMessageProducer
                // emulateMissingHeader();
            } catch (IOException e) {
                // Some IO error exception, stop the process
                ExecutionManagerImpl.stopProcess(event.getProcessHandler());
                LOGGER.warn("LSP process crashes.", e);
            }
        } else if (ProcessOutputType.isStderr(outputType)) {
            // Log the error in the 'Log' tab console
            for (var handler : provider.getHandlers()) {
                handler.logError(removeEndLine(event.getText()));
            }
        }
    }

    private void emulateMissingHeader() throws IOException {
        nbMessagesForEmulatingMissingHeader++;
        if (nbMessagesForEmulatingMissingHeader == 100) {
            nbMessagesForEmulatingMissingHeader = 0;
            synchronized (this.outputStreamWriter) {
                this.outputStreamWriter.write("\n\n\n");
                this.outputStreamWriter.flush();
            }
        }
    }

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
        // Close the output
        try {
            synchronized (this.outputStreamWriter) {
                this.outputStreamWriter.close();
                this.outputStream.close();
            }
        } catch (IOException e) {
            // Do nothing
        }
        if (!provider.isStopped()) {
            // The provider was not stopped by LSP4IJ (with Stop/Pause button, when all files are closed, etc)
            // It is an unexpected error, notify it
            for (var handler : provider.getUnexpectedServerStopHandlers()) {
                handler.run();
            }
        }
    }

}

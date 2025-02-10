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

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * IntelliJ process listener used to read the process of the started language server in an InputStream
 * and write Std error in the LSP console tab log.
 */
class LSPProcessListener implements ProcessListener {

    private final PipedOutputStream outputStream;
    private final OutputStreamWriter outputStreamWriter;
    private final PipedInputStream inputStream;
    private final @NotNull OSProcessStreamConnectionProvider provider;

    public LSPProcessListener(@NotNull OSProcessStreamConnectionProvider provider) throws IOException {
        this.outputStream = new PipedOutputStream();
        this.outputStreamWriter = new OutputStreamWriter(this.outputStream, StandardCharsets.UTF_8);
        this.inputStream = new PipedInputStream(this.outputStream);
        this.provider = provider;
    }

    @NotNull
    public final InputStream getInputStream() {
        return this.inputStream;
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (ProcessOutputType.isStdout(outputType)) {
            // Flush the output
            try {
                this.outputStreamWriter.write(event.getText());
                this.outputStreamWriter.flush();
            } catch (IOException e) {
                // Some IO error exception, stop the process
                ExecutionManagerImpl.stopProcess(event.getProcessHandler());
            }
        } else if (ProcessOutputType.isStderr(outputType)) {
            // Log the error in the 'Log' tab console
            for (var handler : provider.getHandlers()) {
                handler.logError(removeEndLine(event.getText()));
            }
        }
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

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
        // Close the output
        try {
            outputStreamWriter.close();
            outputStream.close();
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

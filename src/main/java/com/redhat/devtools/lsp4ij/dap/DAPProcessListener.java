/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * IntelliJ process listener used to read the process of the started DAP server in an InputStream.
 */
class DAPProcessListener implements ProcessListener {

    private final PipedOutputStream outputStream;
    private final OutputStreamWriter outputStreamWriter;
    private final PipedInputStream inputStream;

    public DAPProcessListener() throws IOException {
        this.outputStream = new PipedOutputStream();
        this.outputStreamWriter = new OutputStreamWriter(this.outputStream, StandardCharsets.UTF_8);
        this.inputStream = new PipedInputStream(this.outputStream);
    }

    @NotNull
    public final InputStream getInputStream() {
        return this.inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
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
        }
    }

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
        try {
            outputStreamWriter.close();
            outputStream.close();
        } catch (IOException e) {
            // Do nothing
        }
    }

}
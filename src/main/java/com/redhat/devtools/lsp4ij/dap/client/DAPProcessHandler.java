/*
 * Copyright 2023-2024 FalsePattern
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

public class DAPProcessHandler extends KillableColoredProcessHandler implements AnsiEscapeDecoder.ColoredTextAcceptor {

    public DAPProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
        super(commandLine);
        setHasPty(commandLine instanceof PtyCommandLine);
        setShouldDestroyProcessRecursively(!hasPty());
    }

    public DAPProcessHandler(@NotNull Process process, String commandLine, @NotNull Charset charset) {
        super(process, commandLine, charset);
        setHasPty(process instanceof PtyProcess);
        setShouldDestroyProcessRecursively(!hasPty());
    }
}
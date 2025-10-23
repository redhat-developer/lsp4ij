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
package com.redhat.devtools.lsp4ij.dap.disassembly;

import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.files.DeferredSourcePosition;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a deferred source position for a disassembly instruction in a {@link DisassemblyFile}.
 * <p>
 * The line corresponding to the instruction pointer reference is resolved asynchronously.
 * If the instruction is not yet loaded in the document, the required range of instructions
 * will be fetched from the debug adapter, the document will be updated, and then the line is returned.
 */
public class DisassemblyDeferredSourcePosition extends DeferredSourcePosition<String, DisassemblyFile> {

    /**
     * Creates a deferred source position for a disassembly instruction.
     *
     * @param instructionPointerReference the instruction pointer reference identifying the instruction
     * @param file                        the disassembly file associated with this position
     * @param client                      the DAP client used to fetch information if needed
     */
    public DisassemblyDeferredSourcePosition(@NotNull String instructionPointerReference,
                                             @NotNull DisassemblyFile file,
                                             @NotNull DAPClient client) {
        super(instructionPointerReference, file, client);
    }

    /**
     * Asynchronously loads the line number corresponding to the instruction pointer.
     * <p>
     * This implementation delegates to {@link DisassemblyFile#getInstructionIndex(String, int, DAPClient)}.
     * <ul>
     *     <li>If the instruction is already present in the document, it returns the existing line index.</li>
     *     <li>If the instruction is not present, it fetches the relevant instruction range from the debug adapter,
     *     updates the document with the new instructions, and then returns the resolved line.</li>
     * </ul>
     *
     * @param instructionPointerReference the instruction pointer reference to resolve
     * @param disassemblyFile             the file containing the disassembly instructions
     * @param client                      the DAP client used to fetch additional instructions if necessary
     * @return a {@link CompletableFuture} completing with the resolved line index
     */
    @Override
    protected CompletableFuture<Integer> loadAndResolveLineAsync(@NotNull String instructionPointerReference,
                                                                 @NotNull DisassemblyFile disassemblyFile,
                                                                 @NotNull DAPClient client) {
        // Resolves the line of the instruction asynchronously.
        // If not found, fetches the instruction range, updates the document, then resolves the line.
        return disassemblyFile.getInstructionIndex(instructionPointerReference, 0, client);
    }
}

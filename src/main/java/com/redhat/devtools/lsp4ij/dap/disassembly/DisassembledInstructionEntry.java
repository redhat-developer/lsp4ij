/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * Angelo Zerr - implementation of DAP disassembly virtual file support
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.disassembly;

import org.eclipse.lsp4j.debug.DisassembledInstruction;

import java.math.BigInteger;

/**
 * Represents a single disassembled instruction entry in a DisassemblyFile.
 * <p>
 * Each entry contains:
 * <ul>
 *     <li>{@code instructionReference} - the memory reference or identifier from the DAP server</li>
 *     <li>{@code instructionReferenceOffset} - offset from the instruction reference in memory</li>
 *     <li>{@code instructionOffset} - offset of the instruction within a batch of instructions</li>
 *     <li>{@code address} - the resolved memory address of this instruction</li>
 *     <li>{@code instr} - the original {@link DisassembledInstruction} object from the DAP server</li>
 * </ul>
 */
public record DisassembledInstructionEntry(
        String instructionReference,
        Integer instructionReferenceOffset,
        Integer instructionOffset,
        BigInteger address,
        DisassembledInstruction instr
) {
}

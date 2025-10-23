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

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.files.DAPFile;
import org.eclipse.lsp4j.debug.DisassembleArguments;
import org.eclipse.lsp4j.debug.DisassembleResponse;
import org.eclipse.lsp4j.debug.DisassembledInstruction;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyUtils.binarySearch2;
import static com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyUtils.splice;

/**
 * Represents a virtual file that contains disassembled machine instructions
 * from a DAP (Debug Adapter Protocol) server.
 * <p>
 * Each instance is associated with a specific run configuration and project.
 * The file dynamically fetches disassembly instructions from the server
 * and caches them for display in the disassembly editor.
 */
public class DisassemblyFile extends DAPFile {

    public static final String FILE_NAME = "disassembly";
    private static final Logger LOGGER = LoggerFactory.getLogger(DisassemblyFile.class);
    /**
     * Number of instructions to load initially or per batch.
     */
    private static final int NUM_INSTRUCTIONS_TO_LOAD = 50;
    private final SimpleModificationTracker modificationTracker = new SimpleModificationTracker();
    /**
     * Maps instruction references to memory addresses.
     */
    private final Map<String, BigInteger> referenceToMemoryAddress = new HashMap<>();

    /**
     * All disassembled instructions loaded so far.
     */
    private final List<DisassembledInstructionEntry> disassembledInstructions = new ArrayList<>();
    private @NotNull
    @NlsSafe String presentableName;

    /**
     * Creates a new DisassemblyFile for the given configuration and project.
     *
     * @param configName the run configuration name
     * @param path       the unique path for this file (project hash + config)
     * @param project    the associated IntelliJ project
     */
    public DisassemblyFile(@NotNull String configName,
                           @NotNull String path,
                           @NotNull Project project) {
        super("Disassembly (" + configName + ").disasm", path, DisassemblyFileType.INSTANCE, project);
        presentableName = "Disassembly (" + configName + ")";
    }

    @Override
    public @NotNull @NlsSafe String getPresentableName() {
        return presentableName;
    }

    /**
     * Sends a disassemble request to the DAP server.
     *
     * @param instructionReference memory reference to disassemble
     * @param offset               memory offset from reference
     * @param instructionOffset    instruction offset for batch loading
     * @param instructionCount     number of instructions to request
     * @param server               the DAP server instance
     * @return a future containing the disassembly response
     */
    private static CompletableFuture<DisassembleResponse> disassemble(String instructionReference,
                                                                      Integer offset,
                                                                      Integer instructionOffset,
                                                                      Integer instructionCount,
                                                                      IDebugProtocolServer server) {
        DisassembleArguments args = new DisassembleArguments();
        args.setMemoryReference(instructionReference);
        args.setOffset(offset);
        args.setInstructionOffset(instructionOffset);
        args.setInstructionCount(instructionCount);
        args.setResolveSymbols(true);
        return server.disassemble(args);
    }

    /**
     * Returns the index of the instruction corresponding to a memory reference
     * and offset, loading instructions from the server if necessary.
     *
     * @param instructionReference the instruction reference identifier
     * @param offset               offset in memory from the reference
     * @param client               the DAP client to fetch instructions
     * @return a CompletableFuture with the index of the instruction in the list
     */
    public CompletableFuture<Integer> getInstructionIndex(String instructionReference, int offset, DAPClient client) {
        BigInteger addr = referenceToMemoryAddress.get(instructionReference);
        if (addr == null) {
            return loadDisassembledInstructions(instructionReference, 0, -NUM_INSTRUCTIONS_TO_LOAD, NUM_INSTRUCTIONS_TO_LOAD * 2, client)
                    .thenApply(_unused -> {
                        BigInteger memoryAddress = referenceToMemoryAddress.get(instructionReference);
                        if (memoryAddress != null) {
                            return getIndexFromAddress(memoryAddress.add(BigInteger.valueOf(offset)));
                        }
                        return -1;
                    });
        } else {
            return CompletableFuture.completedFuture(getIndexFromAddress(addr.add(BigInteger.valueOf(offset))));
        }
    }

    /**
     * Loads disassembled instructions from the DAP server, merges them with
     * the existing list, and updates the document text in the editor.
     *
     * @param instructionReference the instruction reference identifier
     * @param offset               memory offset
     * @param instructionOffset    instruction offset within batch
     * @param instructionCount     number of instructions to fetch
     * @param client               DAP client to use for fetching
     * @return a future with the number of instructions loaded
     */
    private CompletableFuture<Integer> loadDisassembledInstructions(String instructionReference,
                                                                    Integer offset,
                                                                    Integer instructionOffset,
                                                                    Integer instructionCount,
                                                                    DAPClient client) {
        var server = client.getDebugProtocolServer();
        if (server == null) {
            return CompletableFuture.completedFuture(0);
        }

        CompletableFuture<DisassembleResponse> resultEntriesFuture =
                disassemble(instructionReference, offset, instructionOffset, instructionCount, server);

        CompletableFuture<Integer> ensureBaseLineInstructions = null;
        if (referenceToMemoryAddress.containsKey(instructionReference) &&
                instructionOffset != null && instructionOffset != 0) {
            ensureBaseLineInstructions =
                    this.loadDisassembledInstructions(instructionReference, 0, 0, NUM_INSTRUCTIONS_TO_LOAD, client);
        }

        CompletableFuture<DisassembleResponse> result = ensureBaseLineInstructions != null ?
                ensureBaseLineInstructions.thenCompose(index -> resultEntriesFuture) :
                resultEntriesFuture;

        return result.thenCompose(disassembleResponse -> {
            if (disassembleResponse == null) {
                return CompletableFuture.completedFuture(0);
            }

            var resultEntries = disassembleResponse.getInstructions();
            if (resultEntries == null || resultEntries.length == 0) {
                return CompletableFuture.completedFuture(0);
            }

            List<DisassembledInstructionEntry> newEntries = new ArrayList<>();
            Source lastLocation = null;

            for (int i = 0; i < resultEntries.length; i++) {
                DisassembledInstruction instr = resultEntries[i];
                int thisInstructionOffset = instructionOffset + i;

                if (instr.getLocation() != null) lastLocation = instr.getLocation();
                if (instr.getLine() != null) instr.setLocation(lastLocation);

                BigInteger address;
                try {
                    address = DisassemblyUtils.parseBigInteger(instr.getAddress());
                } catch (Exception ex) {
                    LOGGER.warn("Could not parse disassembly address {}", instr.getAddress());
                    continue;
                }
                if (address.equals(BigInteger.valueOf(-1))) continue;

                DisassembledInstructionEntry entry = new DisassembledInstructionEntry(
                        instructionReference, offset, thisInstructionOffset, address, instr);
                newEntries.add(entry);

                if (offset == 0 && thisInstructionOffset == 0) {
                    referenceToMemoryAddress.put(instructionReference, address);
                }
            }

            if (newEntries.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }

            BigInteger firstAddr = newEntries.get(0).address();
            BigInteger lastAddr = newEntries.get(newEntries.size() - 1).address();

            int startN = binarySearch2(disassembledInstructions.size(), i ->
                    disassembledInstructions.get(i).address().subtract(firstAddr).intValue());
            int start = startN < 0 ? ~startN : startN;
            int endN = binarySearch2(disassembledInstructions.size(), i ->
                    disassembledInstructions.get(i).address().subtract(lastAddr).intValue());
            int end = endN < 0 ? ~endN : endN + 1;

            int toDelete = end - start;
            splice(disassembledInstructions, start, toDelete, newEntries);

            // Update the editor document asynchronously
            CompletableFuture<Integer> updatedDoc = new CompletableFuture<>();
            WriteCommandAction.runWriteCommandAction(getProject(), () -> {
                var doc = FileDocumentManager.getInstance().getDocument(this);
                StringBuilder sb = new StringBuilder();
                for (var instr : new ArrayList<>(disassembledInstructions)) {
                    sb.append(String.format("%s: %s %s\n",
                            instr.instr().getAddress(),
                            instr.instr().getInstructionBytes(),
                            instr.instr().getInstruction()));
                }
                doc.setText(sb.toString());
                DisassemblyFile.this.modificationTracker.incModificationCount();
                updatedDoc.complete(newEntries.size() - toDelete);
            });
            return updatedDoc;
        });
    }

    /**
     * Returns the index of the instruction in the loaded list that corresponds
     * to the given memory address. Returns -1 if not found.
     */
    private int getIndexFromAddress(BigInteger address) {
        int low = 0, high = disassembledInstructions.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            BigInteger rowAddr = disassembledInstructions.get(mid).address();
            int cmp = rowAddr.compareTo(address);
            if (cmp == 0) return mid;
            if (cmp < 0) low = mid + 1;
            else high = mid - 1;
        }
        return -1;
    }

    /**
     * Returns the memory address corresponding to an instruction reference, if loaded.
     */
    public @Nullable BigInteger getReferenceAddress(String instructionReference) {
        return referenceToMemoryAddress.get(instructionReference);
    }

    public void dispose() {
        // Nothing special to dispose for now
    }

    public @Nullable DisassembledInstructionEntry getInstructionAt(int line) {
        return line < disassembledInstructions.size() ? disassembledInstructions.get(line) : null;
    }

    @Override
    public long getModificationCount() {
        return modificationTracker.getModificationCount();
    }

}

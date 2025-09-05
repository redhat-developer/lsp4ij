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
package com.redhat.devtools.lsp4ij.dap.disassembly.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.breakpoints.BreakpointHandlerBase;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFile;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.InstructionBreakpoint;
import org.eclipse.lsp4j.debug.SetInstructionBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetInstructionBreakpointsResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for handling breakpoints in disassembly files.
 * <p>
 * This handler installs instruction breakpoints for {@link DisassemblyFile} instances
 * and communicates with the DAP server to set or update breakpoints at the instruction level.
 *
 * @param <B> the type of XBreakpoint handled
 */
public class DisassemblyBreakpointHandlerBase<B extends XBreakpoint<?>> extends BreakpointHandlerBase<B> {

    /**
     * Constructs a new handler for disassembly breakpoints.
     *
     * @param breakpointTypeClass      the breakpoint type class
     * @param debugSession             the XDebugSession this handler is associated with
     * @param debugAdapterDescriptor   the descriptor of the debug adapter
     * @param project                  the current IntelliJ project
     */
    public DisassemblyBreakpointHandlerBase(@NotNull Class<? extends XBreakpointType<B, ?>> breakpointTypeClass,
                                            @NotNull XDebugSession debugSession,
                                            @NotNull DebugAdapterDescriptor debugAdapterDescriptor,
                                            @NotNull Project project) {
        super(breakpointTypeClass, debugSession, debugAdapterDescriptor, project);
    }

    /**
     * Returns whether this handler can install the given breakpoint.
     *
     * @param breakpoint the breakpoint to test
     * @return true if the breakpoint is supported and located in a DisassemblyFile, false otherwise
     */
    @Override
    public boolean supportsBreakpoint(B breakpoint) {
        if (!supportsBreakpointType(breakpoint)) {
            return false;
        }

        XSourcePosition sourcePosition = breakpoint.getSourcePosition();
        if (sourcePosition == null) {
            return false;
        }
        return sourcePosition.getFile() instanceof DisassemblyFile;
    }

    /**
     * Checks if the breakpoint type is a Disassembly breakpoint type.
     *
     * @param breakpoint the breakpoint to check
     * @param <B1>       generic breakpoint type
     * @return true if the breakpoint type is supported, false otherwise
     */
    private <B1 extends XBreakpoint<?>> boolean supportsBreakpointType(B1 breakpoint) {
        return breakpoint.getType() instanceof DisassemblyBreakpointTypeBase<?>;
    }

    /**
     * Sends all current breakpoints to the DAP server.
     *
     * @param debugProtocolServer  the debug protocol server to send breakpoints to (nullable)
     * @param temporaryBreakpoint  a temporary breakpoint used for testing (nullable)
     * @return a CompletableFuture that completes when all breakpoints are set
     */
    @Override
    protected @NotNull CompletableFuture<@Nullable Void> doSendBreakpoints(@Nullable IDebugProtocolServer debugProtocolServer,
                                                                           @Nullable TemporaryBreakpoint temporaryBreakpoint) {
        var disassemblyFile = ((DAPDebugProcess) debugSession.getDebugProcess()).getDisassemblyFile();
        if (disassemblyFile == null) {
            // Should never occur
            return CompletableFuture.completedFuture(null);
        }

        var arguments = new SetInstructionBreakpointsArguments();
        var instructionBreakpoints = new ArrayList<InstructionBreakpoint>();

        for (B breakpoint : breakpoints) {
            var file = (DisassemblyFile) breakpoint.getSourcePosition().getFile();
            if (disassemblyFile.equals(file)) {
                var instr = file.getInstructionAt(breakpoint.getSourcePosition().getLine());
                if (instr != null) {
                    String instructionReference = instr.instructionReference();
                    BigInteger referenceAddress = file.getReferenceAddress(instructionReference);
                    int offset = referenceAddress != null
                            ? instr.address().subtract(referenceAddress).intValue()
                            : instr.address().intValue();
                    InstructionBreakpoint instructionBreakpoint = new InstructionBreakpoint();
                    instructionBreakpoint.setInstructionReference(instructionReference);
                    instructionBreakpoint.setOffset(offset);
                    instructionBreakpoints.add(instructionBreakpoint);
                }
            }
        }

        arguments.setBreakpoints(instructionBreakpoints.toArray(new InstructionBreakpoint[0]));

        if (debugProtocolServer != null) {
            // Send breakpoints to the given debug protocol server
            return setInstructionBreakpoints(debugProtocolServer, arguments);
        }

        // Send breakpoints to all registered debug protocol servers
        var setInstructionBreakpointsFutures = new ArrayList<CompletableFuture<@Nullable Void>>();
        for (var server : debugProtocolServers) {
            setInstructionBreakpointsFutures.add(setInstructionBreakpoints(server, arguments));
        }
        return CompletableFuture.allOf(setInstructionBreakpointsFutures.toArray(EMPTY_COMPLETABLE_FUTURES));
    }

    /**
     * Sends a setInstructionBreakpoints request to the given DAP server.
     *
     * @param debugProtocolServer the DAP server
     * @param arguments           the instruction breakpoints arguments
     * @return a CompletableFuture that completes when the breakpoints have been set
     */
    private CompletableFuture<Void> setInstructionBreakpoints(@NotNull IDebugProtocolServer debugProtocolServer,
                                                              @NotNull SetInstructionBreakpointsArguments arguments) {
        CompletableFuture<SetInstructionBreakpointsResponse> future = debugProtocolServer.setInstructionBreakpoints(arguments);
        return future.thenAccept(response -> {
            var breakpoints = response.getBreakpoints();
            if (breakpoints == null) {
                return;
            }
            for (int i = 0; i < breakpoints.length; i++) {
                Breakpoint breakpointResponse = breakpoints[i];
                // TODO: implement mapping between DAP response and IntelliJ breakpoint if needed
            }
        });
    }
}

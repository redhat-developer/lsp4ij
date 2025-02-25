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
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.dap.DAPIJUtils.getFileName;
import static com.redhat.devtools.lsp4ij.dap.DAPIJUtils.getFilePath;

/**
 * Debug Adapter Protocol (DAP) breakpoint handler.
 */
public class DAPBreakpointHandler extends XBreakpointHandler<XLineBreakpoint<DAPBreakpointProperties>> {

    private final @NotNull DebugAdapterDescriptor adapterDescriptor;
    private final @NotNull Project project;
    private IDebugProtocolServer debugProtocolServer;
    private @Nullable Capabilities capabilities;
    private final List<XBreakpoint> breakpoints = ContainerUtil.createConcurrentList();

    public DAPBreakpointHandler(@NotNull DebugAdapterDescriptor adapterDescriptor,
                                @NotNull Project project) {
        super(DAPBreakpointType.class);
        this.adapterDescriptor = adapterDescriptor;
        this.project = project;
    }

    @Override
    public void registerBreakpoint(@NotNull XLineBreakpoint<DAPBreakpointProperties> breakpoint) {
        if (!(breakpoint.isEnabled() &&
                supportsBreakpoint(breakpoint))) {
            return;
        }

        breakpoints.add(breakpoint);
        sendBreakpoints();
    }

    @Override
    public void unregisterBreakpoint(@NotNull XLineBreakpoint<DAPBreakpointProperties> breakpoint, boolean temporary) {
        if (!supportsBreakpoint(breakpoint)) {
            return;
        }
        breakpoints.remove(breakpoint);
        sendBreakpoints();
    }

    public CompletableFuture<@Nullable Void> initialize(@NotNull IDebugProtocolServer debugProtocolServer,
                                                        @Nullable Capabilities capabilities) {
        this.debugProtocolServer = debugProtocolServer;
        this.capabilities = capabilities;
        return sendBreakpoints();
    }

    private CompletableFuture<@Nullable Void> sendBreakpoints() {
        return sendBreakpoints(null);
    }

    private CompletableFuture<@Nullable Void> sendBreakpoints(@Nullable XSourcePosition temporaryBreakpoint) {
        if (debugProtocolServer == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Convert list of IJ XBreakpoint -> LSP SourceBreakpoint
        Map<Source, List<SourceBreakpoint>> targetBreakpoints = new HashMap<>();
        for (XBreakpoint breakpoint : breakpoints) {
            var sourcePosition = breakpoint.getSourcePosition();
            addSourceBreakpoint(sourcePosition, targetBreakpoints);
        }

        if (temporaryBreakpoint != null) {
            addSourceBreakpoint(temporaryBreakpoint, targetBreakpoints);
        }

        final var setBreakpointsFutures = new ArrayList<CompletableFuture<Void>>();
        for (Iterator<Map.Entry<Source, List<SourceBreakpoint>>> iterator = targetBreakpoints.entrySet()
                .iterator(); iterator.hasNext(); ) {
            Map.Entry<Source, List<SourceBreakpoint>> entry = iterator.next();

            Source source = entry.getKey();
            List<SourceBreakpoint> sourceBreakpoints = entry.getValue();
            int[] lines = sourceBreakpoints.stream().mapToInt(SourceBreakpoint::getLine).toArray();
            SourceBreakpoint[] sourceBps = sourceBreakpoints.toArray(new SourceBreakpoint[sourceBreakpoints.size()]);

            final var arguments = new SetBreakpointsArguments();
            arguments.setSource(source);
            arguments.setLines(lines);
            arguments.setBreakpoints(sourceBps);
            arguments.setSourceModified(false);
            CompletableFuture<SetBreakpointsResponse> future = debugProtocolServer.setBreakpoints(arguments);
            CompletableFuture<Void> future2 = future.thenAccept((SetBreakpointsResponse bpResponse) -> {
                // TODO update platform breakpoint with new info
            });
            setBreakpointsFutures.add(future2);

            // Once we told adapter there are no breakpoints for a source file, we can stop
            // tracking that file
            if (sourceBreakpoints.isEmpty()) {
                iterator.remove();
            }
        }
        return CompletableFuture.allOf(setBreakpointsFutures.toArray(new CompletableFuture[setBreakpointsFutures.size()]));
    }

    private static void addSourceBreakpoint(XSourcePosition sourcePosition, Map<Source, List<SourceBreakpoint>> targetBreakpoints) {
        Source source = toDAPSource(sourcePosition);
        List<SourceBreakpoint> sourceBreakpoints = targetBreakpoints
                .computeIfAbsent(source, s -> new ArrayList<>());

        int lineNumber = getLineNumber(sourcePosition);
        SourceBreakpoint sourceBreakpoint = new SourceBreakpoint();
        sourceBreakpoint.setLine(lineNumber);
        sourceBreakpoints.add(sourceBreakpoint);
    }

    private static @NotNull Source toDAPSource(XSourcePosition sourcePosition) {
        Source source = new Source();
        source.setPath(getFilePath(sourcePosition.getFile()));
        source.setName(getFileName(sourcePosition.getFile()));
        return source;
    }

    public CompletableFuture<@Nullable Void> sendTemporaryBreakpoint(@NotNull XSourcePosition sourcePosition) {
        return sendBreakpoints(sourcePosition);
    }

    /**
     * Returns whether this target can install the given breakpoint.
     *
     * @param breakpoint breakpoint to consider
     * @return whether this target can install the given breakpoint
     */
    public boolean supportsBreakpoint(XBreakpoint breakpoint) {
        if (!(breakpoint.getType() instanceof DAPBreakpointType)) {
            return false;
        }

        XSourcePosition sourcePosition = breakpoint.getSourcePosition();
        if (sourcePosition == null) {
            return false;
        }
        return adapterDescriptor.isDebuggableFile(sourcePosition.getFile(), project);
    }

    private static int getLineNumber(@NotNull XSourcePosition sourcePosition) {
        return sourcePosition.getLine() + 1;
    }


    @Nullable
    public XBreakpoint<DAPBreakpointProperties> findBreakpoint(@NotNull StackFrame stackFrame) {
        Path filePath = Paths.get(stackFrame.getSource().getPath().trim());
        if (filePath == null) {
            return null;
        }
        int lineNumber = stackFrame.getLine();

        for (XBreakpoint breakpoint : breakpoints) {
            XSourcePosition breakpointPosition = breakpoint.getSourcePosition();
            if (breakpointPosition == null) {
                continue;
            }
            VirtualFile fileInBreakpoint = breakpointPosition.getFile();
            int line = breakpointPosition.getLine() + 1;
            if (fileInBreakpoint.toNioPath().equals(filePath) && line == lineNumber) {
                return breakpoint;
            }
        }
        return null;
    }
}

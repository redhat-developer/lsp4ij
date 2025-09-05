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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.xdebugger.XAlternativeSourceHandler;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Alternative source handler that provides a disassembly view for a stack frame.
 * <p>
 * This handler listens for editor tab selection changes and activates the disassembly view
 * when the currently selected file corresponds to a disassembly file associated
 * with the {@link DAPDebugProcess}.
 */
public class DAPAlternativeSourceHandler implements XAlternativeSourceHandler, Disposable {

    /**
     * State flow indicating whether the alternative source (disassembly) is currently available.
     */
    private final MutableStateFlow<Boolean> alternativeSourceAvailable = StateFlowKt.MutableStateFlow(false);

    /**
     * The DAP debug process associated with this handler.
     */
    private final DAPDebugProcess debugProcess;

    /**
     * Listener that tracks changes to the selected editor tab in order to activate the disassembly view.
     */
    private final FileEditorManagerListener selectedFileListener;

    /**
     * Constructs a new DAPAlternativeSourceHandler for the given DAP debug process.
     *
     * @param debugProcess the debug process this handler is associated with
     */
    public DAPAlternativeSourceHandler(@NotNull DAPDebugProcess debugProcess) {
        this.debugProcess = debugProcess;
        this.alternativeSourceAvailable.setValue(false);

        // Initialize the listener for editor tab changes
        this.selectedFileListener = new FileEditorManagerListener() {
            @Override
            public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                // Activate the disassembly view if the newly selected file is the disassembly file
                var disassemblyFile = debugProcess.getDisassemblyFile();
                boolean activateDisassembly = (disassemblyFile != null && disassemblyFile.equals(event.getNewFile()));
                alternativeSourceAvailable.setValue(activateDisassembly);
            }
        };

        // Subscribe the listener to the project's message bus
        debugProcess.getProject().getMessageBus().connect()
                .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, selectedFileListener);
    }

    /**
     * Returns the state flow representing whether an alternative source (disassembly) is available.
     *
     * @return a {@link StateFlow} of boolean indicating alternative source availability
     */
    @Override
    public @NotNull StateFlow<@NotNull Boolean> getAlternativeSourceKindState() {
        return alternativeSourceAvailable;
    }

    /**
     * Determines if the alternative source kind (disassembly) should be preferred for a given suspend context.
     *
     * @param context the current suspend context
     * @return true if the disassembly view should be preferred, false otherwise
     */
    @Override
    public boolean isAlternativeSourceKindPreferred(@NotNull XSuspendContext context) {
        return getAlternativeSourceKindState().getValue();
    }

    /**
     * Returns the alternative source position for the given stack frame.
     *
     * @param stackFrame the stack frame to obtain an alternative position for
     * @return the {@link XSourcePosition} corresponding to the disassembly, or null if not available
     */
    @Override
    public @Nullable XSourcePosition getAlternativePosition(@NotNull XStackFrame stackFrame) {
        if (!(stackFrame instanceof DAPStackFrame dapFrame)) {
            return null;
        }
        return dapFrame.getAlternativePosition();
    }

    /**
     * Disposes this handler and unregisters its listener from the message bus.
     */
    @Override
    public void dispose() {
        debugProcess.getProject().getMessageBus().connect(debugProcess)
                .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, selectedFileListener);
    }
}

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
package com.redhat.devtools.lsp4ij.dap.client.variables.providers;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import org.eclipse.lsp4j.debug.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The {@code DebugVariableContext} class encapsulates the context information
 * necessary for registering and retrieving variable positions and ranges during debugging.
 * It provides methods to configure variable positions and ranges for a given stack frame
 * and manages the association between variable names and their corresponding positions/ranges.
 */
public class DebugVariableContext {

    private final DAPStackFrame stackFrame;
    private final Map<String, TextRange> variableRanges;
    private final Map<String, XSourcePosition> variablePositions;
    private final int endLineOffset;
    private final @NotNull Collection<DebugVariablePositionProvider> providers;
    private @Nullable Editor editor;

    /**
     * Constructs a {@code DebugVariableContext} for the given stack frame.
     *
     * @param stackFrame The stack frame for which the variable context is created.
     */
    public DebugVariableContext(@NotNull DAPStackFrame stackFrame) {
        this.stackFrame = stackFrame;
        this.providers = stackFrame.getClient().getServerDescriptor().getVariableSupport().getDebugVariablePositionProvider();
        this.variableRanges = new HashMap<>();
        this.variablePositions = new HashMap<>();
        Editor[] editors = LSPIJUtils.editorsForFile(getFile(), stackFrame.getClient().getProject());
        editor = editors.length > 0 ? editors[0] : null;
        if (editor != null) {
            endLineOffset = editor.getDocument().getLineEndOffset(stackFrame.getSourcePosition().getLine());
        } else {
            endLineOffset = -1;
        }
    }

    /**
     * Configures the context for variable position providers.
     * This method invokes {@link DebugVariablePositionProvider#configureContext(DebugVariableContext)}
     * for each registered provider to initialize variable positions and ranges.
     */
    public void configureContext() {
        for (var provider : providers) {
            provider.configureContext(this);
        }
    }

    /**
     * Returns the file associated with this debug variable context.
     *
     * @return The virtual file associated with the current stack frame.
     */
    public @NotNull VirtualFile getFile() {
        return stackFrame.getSourcePosition().getFile();
    }

    /**
     * Returns the end line offset of the current context.
     *
     * @return The end line offset.
     */
    public int getEndLineOffset() {
        return endLineOffset;
    }

    /**
     * Returns the editor associated with this context, if available.
     *
     * @return The editor or null if no editor is available.
     */
    @Nullable
    public Editor getEditor() {
        return editor;
    }

    /**
     * Retrieves the source position for a given variable by its name.
     * If the source position has not been registered yet, it will attempt
     * to create a new source position from the variable's range.
     *
     * @param name The name of the variable.
     * @return The source position associated with the variable, or null if not found.
     */
    public @Nullable XSourcePosition getSourcePosition(String name) {
        var position = variablePositions.get(name);
        if (position != null) {
            return position;
        }
        var textRange = variableRanges.get(name);
        if (textRange == null) {
            return null;
        }
        var range = LSPIJUtils.toRange(textRange, editor.getDocument());
        var variablePosition = XDebuggerUtil.getInstance()
                .createPosition(getFile(), range.getStart().getLine(), range.getEnd().getCharacter());
        addVariablePosition(name,  variablePosition);
        return variablePosition;
    }

    /**
     * Registers the source position for a variable by its name.
     *
     * @param variableName The name of the variable.
     * @param textRange    The text range of the variable in the document.
     */
    public void addVariablePosition(String variableName, XSourcePosition textRange) {
        variablePositions.put(variableName, textRange);
    }

    /**
     * Registers a variable range with the specified name and text range.
     *
     * @param variableName The name of the variable.
     * @param textRange    The text range of the variable.
     */
    public void addVariableRange(String variableName, TextRange textRange) {
        variableRanges.put(variableName, textRange);
    }

    /**
     * Retrieves the source position for a given variable.
     * This method delegates to registered position providers to find the appropriate position.
     *
     * @param variable The variable whose source position is to be retrieved.
     * @return The source position for the variable, or null if not found.
     */
    public XSourcePosition getSourcePositionFor(@NotNull Variable variable) {
        for (var provider : providers) {
            var sourcePosition = provider.getSourcePosition(variable, this);
            if (sourcePosition != null) {
                return sourcePosition;
            }
        }
        return null;
    }
}

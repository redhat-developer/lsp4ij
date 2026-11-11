/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client.variables;

import com.intellij.util.ThreeState;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablePresentationHintAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

/**
 * Debug Adapter Protocol (DAP) value.
 */
public class DAPValue extends XNamedValue {

    @NotNull
    private final DAPStackFrame stackFrame;
    @NotNull
    private final Variable variable;
    private final @Nullable Integer parentVariablesReference;
    @Nullable
    private final Icon icon;
    // The variable with the value of a lazy variable, once the user asked for it
    private volatile @Nullable Variable resolved;

    public DAPValue(@NotNull DAPStackFrame stackFrame,
                    @NotNull Variable variable,
                    @Nullable Integer parentVariablesReference) {
        super(variable.getName());
        this.stackFrame = stackFrame;
        this.variable = variable;
        this.parentVariablesReference = parentVariablesReference;
        this.icon = getClient().getServerDescriptor().getVariableSupport().getIcon(variable);
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        var server = getClient().getDebugProtocolServer();
        if (resolved == null && server != null && DAPLazyValueEvaluator.isLazy(variable)) {
            // Getting the value may run code: get it when the user asks for it
            node.setPresentation(icon, getPresentation(variable), false);
            var format = getClient().getValueFormat();
            node.setFullValueEvaluator(new DAPLazyValueEvaluator(server, variable.getVariablesReference(), format, value -> {
                resolved = value;
                node.setPresentation(icon, getPresentation(value), value.getVariablesReference() > 0);
            }));
            return;
        }
        Variable shown = getShownVariable();
        node.setPresentation(icon, getPresentation(shown), shown.getVariablesReference() > 0);
    }

    /**
     * Returns the variable which this value shows: the variable with the value of a lazy variable once the user asked
     * for it, and the variable itself otherwise.
     *
     * @return the variable which this value shows.
     */
    @NotNull Variable getShownVariable() {
        Variable value = resolved;
        return value != null ? value : variable;
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        var server = getClient().getDebugProtocolServer();
        if (server == null) {
            return;
        }

        Variable shown = getShownVariable();
        int parentVariablesReference = shown.getVariablesReference();
        // a large variable, such as a large array, is read a page at a time
        new DAPVariableChildren(server, shown, getClient().getValueFormat(),
                child -> new DAPValue(stackFrame, child, parentVariablesReference))
                .load(node);
    }

    @Nullable
    @Override
    public XValueModifier getModifier() {
        if (!canSetValue(getClient().isSupportsSetVariable(), variable)) {
            // Disable the 'Set Value...' menu and F2.
            return null;
        }
        return new DAPValueModifier(this);
    }

    /**
     * Returns true if the user can set the value of the given variable, and false otherwise: the DAP server must
     * support setting a variable, and must not mark this variable as read-only.
     *
     * @param supportsSetVariable true if the DAP server supports the 'setVariable' request.
     * @param variable            the DAP variable.
     * @return true if the user can set the value of the given variable, and false otherwise.
     */
    static boolean canSetValue(boolean supportsSetVariable, @NotNull Variable variable) {
        return supportsSetVariable && !isReadOnly(variable);
    }

    /**
     * Returns true if the DAP server marks the given variable as read-only with the 'readOnly' presentation hint
     * attribute, and false otherwise.
     *
     * @param variable the DAP variable.
     * @return true if the DAP server marks the given variable as read-only, and false otherwise.
     */
    static boolean isReadOnly(@NotNull Variable variable) {
        var hint = variable.getPresentationHint();
        var attributes = hint != null ? hint.getAttributes() : null;
        return attributes != null && Arrays.asList(attributes).contains(VariablePresentationHintAttributes.READ_ONLY);
    }

    @NotNull
    private XValuePresentation getPresentation(@NotNull Variable shown) {
        return getClient().getServerDescriptor().getVariableSupport().getValuePresentation(shown);
    }


    @Override
    public void computeSourcePosition(@NotNull XNavigatable navigatable) {
        stackFrame
                .getSourcePositionFor(variable)
                .thenAccept(sourcePosition -> {
                    if (sourcePosition != null) {
                        navigatable.setSourcePosition(sourcePosition);
                    }
                });
    }

    @NotNull
    @Override
    public ThreeState computeInlineDebuggerData(@NotNull XInlineDebuggerDataCallback callback) {
        computeSourcePosition(callback::computed);
        return ThreeState.YES;
    }

    @Override
    public boolean canNavigateToSource() {
        return true;
    }

    @Override
    public boolean canNavigateToTypeSource() {
        return false;
    }

    @Override
    public void computeTypeSourcePosition(@NotNull XNavigatable navigatable) {

    }

    public @NotNull DAPClient getClient() {
        return stackFrame.getClient();
    }

    public @NotNull DAPStackFrame getStackFrame() {
        return stackFrame;
    }

    public @Nullable Integer getParentVariablesReference() {
        return parentVariablesReference;
    }

    public @NotNull Variable getVariable() {
        return variable;
    }
}
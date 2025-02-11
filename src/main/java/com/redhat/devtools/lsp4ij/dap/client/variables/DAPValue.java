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
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
        XValuePresentation presentation = getPresentation();
        boolean hasChildren = variable.getVariablesReference() > 0;
        node.setPresentation(icon, presentation, hasChildren);
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        var server = getClient().getDebugProtocolServer();
        if (server == null) {
            return;
        }

        int parentVariablesReference = variable.getVariablesReference();
        VariablesArguments varArgs = new VariablesArguments();
        varArgs.setVariablesReference(parentVariablesReference);
        server.variables(varArgs)
                .thenAccept(variablesResponse -> {
                    XValueChildrenList list = new XValueChildrenList();
                    for (Variable variable : variablesResponse.getVariables()) {
                        list.add(variable.getName(), new DAPValue(stackFrame, variable, parentVariablesReference));
                    }
                    node.addChildren(list, true);
                });
    }

    @Nullable
    @Override
    public XValueModifier getModifier() {
        if (!getClient().isSupportsSetVariable()) {
            // The DAP server doesn't support the setting variable, Disable the 'Set Value...' menu.
            return null;
        }
        return new DAPValueModifier(this);
    }

    @NotNull
    private XValuePresentation getPresentation() {
        return getClient().getServerDescriptor().getVariableSupport().getValuePresentation(variable);
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

    public @Nullable Integer getParentVariablesReference() {
        return parentVariablesReference;
    }

    public @NotNull Variable getVariable() {
        return variable;
    }
}
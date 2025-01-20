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
package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.eclipse.lsp4j.debug.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Debug Adapter Protocol (DAP) value group.
 */
public class DAPValueGroup extends XValueGroup {

    private final @NotNull List<Variable> variables;
    private final @NotNull DAPClient client;
    private final int parentVariablesReference;

    public DAPValueGroup(@NotNull DAPClient client,
                         @NotNull String name,
                         @NotNull List<Variable> variables,
                         int parentVariablesReference) {
        super(name);
        this.variables = variables;
        this.parentVariablesReference = parentVariablesReference;
        this.client = client;
    }

    @Override
    public boolean isRestoreExpansion() {
        return true;
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return AllIcons.Debugger.Value;
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        if (variables.isEmpty()) {
            super.computeChildren(node);
        } else {
            XValueChildrenList children = new XValueChildrenList();
            for (Variable variable : variables) {
                children.add(variable.getName(), new DAPValue(client, variable, getParentVariablesReference()));
            }
            node.addChildren(children, true);
        }
    }

    public int getParentVariablesReference() {
        return parentVariablesReference;
    }
}
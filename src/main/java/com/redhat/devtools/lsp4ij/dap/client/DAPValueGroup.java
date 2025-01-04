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

import static com.redhat.devtools.lsp4ij.dap.client.DAPValue.getIconFor;

public class DAPValueGroup extends XValueGroup {
    private final List<Variable> variables;
    private final DAPClient client;

    public DAPValueGroup(DAPClient client, String name, List<Variable> variables) {
        super(name);
        this.variables = variables;
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
            XValueChildrenList list = new XValueChildrenList();
            for (Variable variable : variables) {
                list.add(variable.getName(), new DAPValue(client, variable, getIconFor(variable)));
            }
            node.addChildren(list, true);
        }
    }
}
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
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
    private final DAPClient client;
    @NotNull
    private final Variable variable;
    private final @Nullable Integer parentVariablesReference;
    @Nullable
    private final Icon icon;

    public DAPValue(@NotNull DAPClient client,
                    @NotNull Variable variable,
                    @Nullable Integer parentVariablesReference) {
        super(variable.getName());
        this.client = client;
        this.variable = variable;
        this.parentVariablesReference = parentVariablesReference;
        this.icon = client.getServerDescriptor().getVariableSupport().getIcon(variable);
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        XValuePresentation presentation = getPresentation();
        boolean hasChildren = variable.getVariablesReference() > 0;
        node.setPresentation(icon, presentation, hasChildren);
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        var server = client.getDebugProtocolServer();
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
                        list.add(variable.getName(), new DAPValue(client, variable, parentVariablesReference));
                    }
                    node.addChildren(list, true);
                });
    }

    @Nullable
    @Override
    public XValueModifier getModifier() {
        if (!client.isSupportsSetVariable()) {
            // The DAP server doesn't support the setting variable, Disable the 'Set Value...' menu.
            return null;
        }
        return new DAPValueModifier(this);
    }

    @NotNull
    private XValuePresentation getPresentation() {
        return client.getServerDescriptor().getVariableSupport().getValuePresentation(variable);
    }


    @Override
    public void computeSourcePosition(@NotNull XNavigatable navigatable) {
        readActionInPooledThread(new Runnable() {

            @Override
            public void run() {
                // TODO : use locationReference
              //  navigatable.setSourcePosition(findPosition());
            }

            /*@Nullable
            private XSourcePosition findPosition() {
                XDebugSession debugSession = client.getSession();
                if (debugSession == null) {
                    return null;
                }
                XStackFrame stackFrame = debugSession.getCurrentStackFrame();
                if (stackFrame == null) {
                    return null;
                }
                Project project = debugSession.getProject();
                XSourcePosition position = debugSession.getCurrentPosition();
                Editor editor = ((FileEditorManagerImpl) FileEditorManager.getInstance(project))
                        .getSelectedTextEditor(true);
                if (editor == null || position == null) {
                    return null;
                }
                VirtualFile virtualFile = null;
                int offset = 0;
                return XDebuggerUtil.getInstance().createPositionByOffset(virtualFile, resolved.getTextOffset());
            }*/
        });
    }

    private static void readActionInPooledThread(@NotNull Runnable runnable) {
        ApplicationManager.getApplication().executeOnPooledThread(() ->
                ApplicationManager.getApplication().runReadAction(runnable));
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
        return client;
    }

    public @Nullable Integer getParentVariablesReference() {
        return parentVariablesReference;
    }

    public @NotNull Variable getVariable() {
        return variable;
    }
}
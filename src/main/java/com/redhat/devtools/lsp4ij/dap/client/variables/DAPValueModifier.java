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

import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XValueModifier;
import com.redhat.devtools.lsp4ij.dap.evaluation.DAPDebuggerEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debug Adapter Protocol (DAP) value modifier to modify a value with the standard "Set Value" menu, with the
 * 'setVariable' request.
 */
public class DAPValueModifier extends XValueModifier {

    private final @NotNull DAPValue value;

    public DAPValueModifier(@NotNull DAPValue value) {
        this.value = value;
    }

    @Override
    public void setValue(@NotNull XExpression expression, @NotNull XModificationCallback callback) {
        String whyItCantBeSet = value.getWhyItCantBeSet(value.getClient().isSupportsSetVariable());
        if (whyItCantBeSet != null) {
            DAPValue.reportWhyItCantBeSet(callback, whyItCantBeSet);
            return;
        }
        final var variable = value.getVariable();
        var client = value.getClient();
        String name = variable.getName();
        int parentVariablesReference = value.getParentVariablesReference();
        String newValue = expression.getExpression();
        // The 'variablesReference' of the variable's parent is valid only until the program resumes
        client.sendInExecutionEpoch(value.getStackFrame().getExecutionEpoch(),
                        () -> client.setVariable(name, parentVariablesReference, newValue))
                .thenAccept(setVariableResponse -> {
                    if (setVariableResponse == null) {
                        callback.errorOccurred("response null");
                        return;
                    }
                    // Update the DAP variable with the response
                    if (setVariableResponse.getVariablesReference() != null) {
                        variable.setVariablesReference(setVariableResponse.getVariablesReference());
                    }
                    variable.setIndexedVariables(setVariableResponse.getIndexedVariables());
                    variable.setNamedVariables(setVariableResponse.getNamedVariables());
                    variable.setValue(setVariableResponse.getValue());
                    variable.setType(setVariableResponse.getType());
                    // The new value has its own location, if any, as in VS Code
                    variable.setValueLocationReference(setVariableResponse.getValueLocationReference());
                    callback.valueModified();
                })
                .exceptionally(error -> {
                    DAPDebuggerEvaluator.errorOccurred(error, callback);
                    return null;
                });
    }

    @Override
    public @Nullable String getInitialValueEditorText() {
        // The value shown, rather than the placeholder of a lazy variable whose value the user asked for
        return value.getShownVariable().getValue();
    }

}

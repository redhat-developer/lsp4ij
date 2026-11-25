/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client.variables;

import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XValueModifier;
import com.redhat.devtools.lsp4ij.dap.evaluation.DAPDebuggerEvaluator;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.SetExpressionResponse;
import org.eclipse.lsp4j.debug.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debug Adapter Protocol (DAP) value modifier which sets a value with the standard "Set Value" menu by assigning it to
 * an expression with the 'setExpression' request: the expression of an Evaluate result, or the 'evaluateName' of a
 * variable.
 */
public class DAPExpressionModifier extends XValueModifier {

    private final @NotNull DAPValue value;
    private final @NotNull String expression;

    /**
     * @param value      the value which is set.
     * @param expression the expression which the value is assigned to: the evaluated expression of an Evaluate
     *                   result, or the 'evaluateName' of a variable.
     */
    public DAPExpressionModifier(@NotNull DAPValue value, @NotNull String expression) {
        this.value = value;
        this.expression = expression;
    }

    @Override
    public void setValue(@NotNull XExpression expression, @NotNull XModificationCallback callback) {
        String whyItCantBeSet = value.getWhyItCantBeSet(value.getClient().isSupportsSetExpression());
        if (whyItCantBeSet != null) {
            DAPValue.reportWhyItCantBeSet(callback, whyItCantBeSet);
            return;
        }
        var client = value.getClient();
        var frame = value.getStackFrame();
        String assignedExpression = getAssignedExpression();
        int frameId = frame.getFrameId();
        String newValue = expression.getExpression();
        // The id of the frame, in which the expression is assigned to, is valid only until the program resumes, or the
        // thread of the frame continues alone
        client.sendInExecutionEpoch(frame,
                        () -> client.setExpression(assignedExpression, frameId, newValue))
                .thenAccept(setExpressionResponse -> {
                    if (setExpressionResponse == null) {
                        callback.errorOccurred("response null");
                        return;
                    }
                    update(value.getShownVariable(), setExpressionResponse);
                    callback.valueModified();
                })
                .exceptionally(error -> {
                    DAPDebuggerEvaluator.errorOccurred(error, callback);
                    return null;
                });
    }

    /**
     * Returns the expression which the value is assigned to: the 'evaluateName' of the value which a lazy variable
     * shows once the user asked for it, if it has one, as in VS Code, and the expression of this modifier otherwise.
     *
     * @return the expression which the value is assigned to.
     */
    @NotNull String getAssignedExpression() {
        String evaluateName = value.getShownVariable().getEvaluateName();
        return value.getParentVariablesReference() != null && StringUtils.isNotBlank(evaluateName) ?
                evaluateName : expression;
    }

    /**
     * Updates the given variable with the new value of the 'setExpression' response.
     *
     * @param variable the variable which the value shows.
     * @param response the 'setExpression' response.
     */
    static void update(@NotNull Variable variable, @NotNull SetExpressionResponse response) {
        variable.setValue(response.getValue());
        variable.setType(response.getType());
        // A response without 'variablesReference' describes a new value without children, as VS Code reads the
        // responses of 'setExpression' and 'setVariable' alike
        Integer variablesReference = response.getVariablesReference();
        variable.setVariablesReference(variablesReference != null ? variablesReference : 0);
        variable.setNamedVariables(response.getNamedVariables());
        variable.setIndexedVariables(response.getIndexedVariables());
        variable.setPresentationHint(response.getPresentationHint());
        variable.setMemoryReference(response.getMemoryReference());
        variable.setValueLocationReference(response.getValueLocationReference());
    }

    @Override
    public @Nullable String getInitialValueEditorText() {
        // The value shown, rather than the placeholder of a lazy variable whose value the user asked for
        return value.getShownVariable().getValue();
    }

}

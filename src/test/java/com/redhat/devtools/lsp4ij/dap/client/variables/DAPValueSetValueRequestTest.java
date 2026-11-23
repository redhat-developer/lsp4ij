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

import com.redhat.devtools.lsp4ij.dap.client.variables.DAPValue.SetValueRequest;
import com.redhat.devtools.lsp4ij.dap.client.variables.DAPValue.SetValueRequest.SetExpression;
import com.redhat.devtools.lsp4ij.dap.client.variables.DAPValue.SetValueRequest.SetVariable;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablePresentationHint;
import org.eclipse.lsp4j.debug.VariablePresentationHintAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The DAP request which "Set Value…" (F2) sends: 'setVariable' for a variable with the 'variablesReference' of its
 * parent, and 'setExpression' for an Evaluate result, which has none, or for a variable with an 'evaluateName' when the
 * DAP server can't set variables.
 */
public class DAPValueSetValueRequestTest {

    private static final int LOCALS_REFERENCE = 1;

    // Evaluate results, which have no parent

    @Test
    void anEvaluateResultIsSetWithSetExpressionOfTheEvaluatedExpression() {
        var result = evaluateResult("answer");

        assertEquals(new SetExpression("answer"), request(false, true, null, result));
        assertEquals(new SetExpression("answer"), request(true, true, null, result));
    }

    @Test
    void anEvaluateResultCantBeSetWithoutSetExpression() {
        var result = evaluateResult("answer");

        // 'setVariable' can't name a value without a parent
        assertNull(request(true, false, null, result));
        assertNull(request(false, false, null, result));
    }

    @Test
    void aReadOnlyEvaluateResultCantBeSet() {
        assertNull(request(true, true, null, readOnly(evaluateResult("answer"))));
    }

    // Variables, which have the 'variablesReference' of their parent

    @Test
    void aVariableIsSetWithSetVariable() {
        assertEquals(new SetVariable(), request(true, false, LOCALS_REFERENCE, variable("answer", null)));
        assertEquals(new SetVariable(), request(true, false, LOCALS_REFERENCE, variable("answer", "frame.answer")));
    }

    @Test
    void aVariableIsSetWithSetVariableWhenTheServerSupportsBothRequests() {
        // As VS Code, which uses 'setExpression' for a variable only when the DAP server can't set variables
        assertEquals(new SetVariable(), request(true, true, LOCALS_REFERENCE, variable("answer", "frame.answer")));
        assertEquals(new SetVariable(), request(true, true, LOCALS_REFERENCE, variable("answer", null)));
    }

    @Test
    void aVariableIsSetWithSetExpressionOfItsEvaluateNameWithoutSetVariable() {
        assertEquals(new SetExpression("frame.answer"),
                request(false, true, LOCALS_REFERENCE, variable("answer", "frame.answer")));
    }

    @Test
    void aVariableWithoutEvaluateNameCantBeSetWithoutSetVariable() {
        assertNull(request(false, true, LOCALS_REFERENCE, variable("answer", null)));
        assertNull(request(false, true, LOCALS_REFERENCE, variable("answer", "")));
    }

    @Test
    void aVariableCantBeSetWithoutEitherRequest() {
        assertNull(request(false, false, LOCALS_REFERENCE, variable("answer", "frame.answer")));
    }

    @Test
    void aReadOnlyVariableCantBeSet() {
        var variable = readOnly(variable("answer", "frame.answer"));

        assertNull(request(true, false, LOCALS_REFERENCE, variable));
        assertNull(request(false, true, LOCALS_REFERENCE, variable));
        assertNull(request(true, true, LOCALS_REFERENCE, variable));
    }

    // Lazy variables, whose value the user asked for

    @Test
    void aLazyVariableIsSetWithSetExpressionOfTheEvaluateNameOfItsValue() {
        var lazy = variable("size", null);
        var value = variable("size", "this.size");

        assertNull(request(false, true, LOCALS_REFERENCE, lazy, lazy));
        assertEquals(new SetExpression("this.size"), request(false, true, LOCALS_REFERENCE, lazy, value));
        assertEquals(new SetVariable(), request(true, true, LOCALS_REFERENCE, lazy, value));
    }

    @Test
    void aLazyVariableWhoseValueIsReadOnlyCantBeSet() {
        var lazy = variable("size", "this.size");

        assertNull(request(true, true, LOCALS_REFERENCE, lazy, readOnly(variable("size", "this.size"))));
        assertNull(request(true, true, LOCALS_REFERENCE, readOnly(lazy), variable("size", "this.size")));
    }

    private static @Nullable SetValueRequest request(boolean supportsSetVariable,
                                                     boolean supportsSetExpression,
                                                     @Nullable Integer parentVariablesReference,
                                                     @NotNull Variable variable) {
        return request(supportsSetVariable, supportsSetExpression, parentVariablesReference, variable, variable);
    }

    private static @Nullable SetValueRequest request(boolean supportsSetVariable,
                                                     boolean supportsSetExpression,
                                                     @Nullable Integer parentVariablesReference,
                                                     @NotNull Variable variable,
                                                     @NotNull Variable shown) {
        return DAPValue.getSetValueRequest(supportsSetVariable, supportsSetExpression, parentVariablesReference,
                variable, shown);
    }

    /**
     * Returns the variable of an Evaluate result, as DAPDebuggerEvaluator creates it: named by the evaluated expression.
     */
    private static @NotNull Variable evaluateResult(@NotNull String expression) {
        var variable = new Variable();
        variable.setName(expression);
        variable.setValue("41");
        return variable;
    }

    private static @NotNull Variable variable(@NotNull String name, @Nullable String evaluateName) {
        var variable = new Variable();
        variable.setName(name);
        variable.setValue("41");
        variable.setEvaluateName(evaluateName);
        return variable;
    }

    private static @NotNull Variable readOnly(@NotNull Variable variable) {
        var hint = new VariablePresentationHint();
        hint.setAttributes(new String[]{VariablePresentationHintAttributes.READ_ONLY});
        variable.setPresentationHint(hint);
        return variable;
    }
}

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

import com.redhat.devtools.lsp4ij.dap.client.DAPCapabilities;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.CapabilitiesEventArguments;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablePresentationHint;
import org.eclipse.lsp4j.debug.VariablePresentationHintAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Set Value…" and F2 follow the 'supportsSetVariable' and 'supportsSetExpression' capabilities which a 'capabilities'
 * event enables after the 'initialize' response, and still honor the 'readOnly' hint of each variable.
 */
public class DAPValueSetVariableCapabilityTest {

    private static final int LOCALS_REFERENCE = 1;

    private final DAPCapabilities capabilities = new DAPCapabilities();

    @BeforeEach
    void initialize() {
        // the initialize response of the debug adapter, without 'supportsSetVariable'
        capabilities.initialize(new Capabilities());
    }

    @Test
    void cannotSetAVariableBeforeTheCapabilityIsEnabled() {
        assertFalse(canSetValue(variable("fromVar")));
    }

    @Test
    void setsAVariableWithoutReadOnlyHintOnceTheEventEnablesTheCapability() {
        enableSetVariable(true);

        assertTrue(canSetValue(variable("fromVar")));
        assertTrue(canSetValue(variable("leaf")));
    }

    @Test
    void cannotSetAReadOnlyVariableEvenWhenTheCapabilityIsEnabled() {
        enableSetVariable(true);

        assertFalse(canSetValue(readOnly(variable("bonus"))));
    }

    @Test
    void cannotSetAVariableOnceTheEventDisablesTheCapability() {
        enableSetVariable(true);
        enableSetVariable(false);

        assertFalse(canSetValue(variable("fromVar")));
        assertFalse(canSetValue(readOnly(variable("bonus"))));
    }

    @Test
    void anEventAboutOtherCapabilitiesKeepsSetVariable() {
        enableSetVariable(true);

        var update = new Capabilities();
        update.setSupportsLogPoints(true);
        capabilities.update(event(update));

        assertTrue(canSetValue(variable("fromVar")));
    }

    @Test
    void setsAnEvaluateResultOnceTheEventEnablesSetExpression() {
        enableSetVariable(true);
        assertFalse(canSetEvaluateResult(variable("answer")));

        enableSetExpression(true);
        assertTrue(canSetEvaluateResult(variable("answer")));
        assertFalse(canSetEvaluateResult(readOnly(variable("answer"))));

        enableSetExpression(false);
        assertFalse(canSetEvaluateResult(variable("answer")));
    }

    /**
     * As DAPValue.getModifier() for a variable of the Variables view, which has the 'variablesReference' of its
     * parent: the client reads 'supportsSetVariable' and 'supportsSetExpression' from its current capabilities.
     */
    private boolean canSetValue(Variable variable) {
        return getSetValueRequest(LOCALS_REFERENCE, variable) != null;
    }

    /**
     * As DAPValue.getModifier() for an Evaluate result, which has no parent 'variablesReference'.
     */
    private boolean canSetEvaluateResult(Variable variable) {
        return getSetValueRequest(null, variable) != null;
    }

    private DAPValue.SetValueRequest getSetValueRequest(Integer parentVariablesReference, Variable variable) {
        var current = capabilities.get();
        return DAPValue.getSetValueRequest(Boolean.TRUE.equals(current.getSupportsSetVariable()),
                Boolean.TRUE.equals(current.getSupportsSetExpression()), parentVariablesReference,
                DAPValue.isReadOnly(variable), DAPValue.getAssignableExpression(parentVariablesReference, variable));
    }

    private void enableSetVariable(boolean supported) {
        var update = new Capabilities();
        update.setSupportsSetVariable(supported);
        capabilities.update(event(update));
    }

    private void enableSetExpression(boolean supported) {
        var update = new Capabilities();
        update.setSupportsSetExpression(supported);
        capabilities.update(event(update));
    }

    private static CapabilitiesEventArguments event(Capabilities update) {
        var args = new CapabilitiesEventArguments();
        args.setCapabilities(update);
        return args;
    }

    private static Variable variable(String name) {
        var variable = new Variable();
        variable.setName(name);
        variable.setValue("7");
        variable.setType("number");
        variable.setVariablesReference(0);
        return variable;
    }

    private static Variable readOnly(Variable variable) {
        var hint = new VariablePresentationHint();
        hint.setAttributes(new String[]{VariablePresentationHintAttributes.READ_ONLY});
        variable.setPresentationHint(hint);
        return variable;
    }
}

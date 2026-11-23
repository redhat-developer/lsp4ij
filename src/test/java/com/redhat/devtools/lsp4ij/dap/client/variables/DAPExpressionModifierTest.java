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

import org.eclipse.lsp4j.debug.SetExpressionResponse;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablePresentationHint;
import org.eclipse.lsp4j.debug.VariablePresentationHintAttributes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The variable which a value shows takes the new value of the 'setExpression' response.
 */
public class DAPExpressionModifierTest {

    @Test
    void theVariableTakesTheNewValue() {
        var variable = structured();
        var hint = new VariablePresentationHint();
        hint.setAttributes(new String[]{VariablePresentationHintAttributes.READ_ONLY});
        var response = new SetExpressionResponse();
        response.setValue("Answer{42}");
        response.setType("Answer");
        response.setVariablesReference(9);
        response.setNamedVariables(2);
        response.setIndexedVariables(3);
        response.setPresentationHint(hint);
        response.setMemoryReference("0x2a");
        response.setValueLocationReference(5);

        DAPExpressionModifier.update(variable, response);

        assertEquals("answer", variable.getName());
        assertEquals("frame.answer", variable.getEvaluateName());
        assertEquals("Answer{42}", variable.getValue());
        assertEquals("Answer", variable.getType());
        assertEquals(9, variable.getVariablesReference());
        assertEquals(Integer.valueOf(2), variable.getNamedVariables());
        assertEquals(Integer.valueOf(3), variable.getIndexedVariables());
        assertArrayEquals(new String[]{VariablePresentationHintAttributes.READ_ONLY},
                variable.getPresentationHint().getAttributes());
        assertEquals("0x2a", variable.getMemoryReference());
        assertEquals(Integer.valueOf(5), variable.getValueLocationReference());
    }

    @Test
    void aNewValueWithoutVariablesReferenceHasNoChildren() {
        var variable = structured();
        var response = new SetExpressionResponse();
        response.setValue("42");
        response.setType("int");

        DAPExpressionModifier.update(variable, response);

        assertEquals("42", variable.getValue());
        assertEquals("int", variable.getType());
        assertEquals(0, variable.getVariablesReference());
        assertNull(variable.getNamedVariables());
        assertNull(variable.getIndexedVariables());
        assertNull(variable.getPresentationHint());
        assertNull(variable.getMemoryReference());
        assertNull(variable.getValueLocationReference());
    }

    private static Variable structured() {
        var variable = new Variable();
        variable.setName("answer");
        variable.setEvaluateName("frame.answer");
        variable.setValue("Answer{41}");
        variable.setType("Answer");
        variable.setVariablesReference(8);
        variable.setNamedVariables(1);
        variable.setIndexedVariables(1);
        variable.setPresentationHint(new VariablePresentationHint());
        variable.setMemoryReference("0x29");
        variable.setValueLocationReference(4);
        return variable;
    }
}

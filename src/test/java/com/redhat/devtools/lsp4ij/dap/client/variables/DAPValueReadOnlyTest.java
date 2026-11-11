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

import junit.framework.TestCase;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablePresentationHint;

/** A variable is read-only when its own presentation hint has the 'readOnly' attribute. */
public class DAPValueReadOnlyTest extends TestCase {

    public void testWithoutPresentationHint() {
        assertFalse(DAPValue.isReadOnly(new Variable()));
    }

    public void testWithoutAttributes() {
        assertFalse(DAPValue.isReadOnly(variable(new VariablePresentationHint())));
    }

    public void testOtherAttributes() {
        assertFalse(DAPValue.isReadOnly(variable(hint("static"))));
    }

    public void testReadOnly() {
        assertTrue(DAPValue.isReadOnly(variable(hint("readOnly"))));
    }

    public void testReadOnlyAmongOtherAttributes() {
        assertTrue(DAPValue.isReadOnly(variable(hint("static", "readOnly"))));
    }

    public void testSetValueOfAWritableVariable() {
        assertTrue(DAPValue.canSetValue(true, new Variable()));
        assertTrue(DAPValue.canSetValue(true, variable(hint("static"))));
    }

    public void testNoSetValueOfAReadOnlyVariable() {
        assertFalse(DAPValue.canSetValue(true, variable(hint("readOnly"))));
    }

    public void testNoSetValueWithoutSetVariable() {
        assertFalse(DAPValue.canSetValue(false, new Variable()));
        assertFalse(DAPValue.canSetValue(false, variable(hint("readOnly"))));
    }

    private static VariablePresentationHint hint(String... attributes) {
        var hint = new VariablePresentationHint();
        hint.setAttributes(attributes);
        return hint;
    }

    private static Variable variable(VariablePresentationHint hint) {
        var variable = new Variable();
        variable.setPresentationHint(hint);
        return variable;
    }
}

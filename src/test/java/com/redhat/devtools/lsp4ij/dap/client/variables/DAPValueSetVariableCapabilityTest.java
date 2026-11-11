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

import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.CapabilitiesEventArguments;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablePresentationHint;
import org.eclipse.lsp4j.debug.VariablePresentationHintAttributes;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Set Value…" and F2 follow the 'supportsSetVariable' capability which a 'capabilities' event enables after the
 * 'initialize' response, and still honor the 'readOnly' hint of each variable.
 */
public class DAPValueSetVariableCapabilityTest {

    /**
     * The initialize response of the debug adapter, without 'supportsSetVariable'.
     */
    private final CompletableFuture<Capabilities> capabilities = CompletableFuture.completedFuture(new Capabilities());

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
        DAPClient.updateCapabilities(capabilities, event(update));

        assertTrue(canSetValue(variable("fromVar")));
    }

    /**
     * As DAPValue.getModifier(): the client reads 'supportsSetVariable' from its current capabilities.
     */
    private boolean canSetValue(Variable variable) {
        return DAPValue.canSetValue(DAPClient.isSupportsSetVariable(capabilities.join()), variable);
    }

    private void enableSetVariable(boolean supported) {
        var update = new Capabilities();
        update.setSupportsSetVariable(supported);
        DAPClient.updateCapabilities(capabilities, event(update));
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

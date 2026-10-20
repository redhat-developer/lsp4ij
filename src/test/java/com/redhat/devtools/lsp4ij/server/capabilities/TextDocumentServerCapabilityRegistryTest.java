/*******************************************************************************
 * Copyright (c) 2026 Contributors to the LSP4IJ project.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.capabilities;

import com.google.gson.JsonObject;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentRegistrationOptions;
import org.junit.Test;

import static org.junit.Assert.*;

public class TextDocumentServerCapabilityRegistryTest {
    @Test
    public void dynamicCapabilityIterationSurvivesRegistrationChanges() {
        var registry = new TextDocumentServerCapabilityRegistry<TextDocumentRegistrationOptions>(new LSPClientFeatures()) {
            @Override
            protected TextDocumentRegistrationOptions create(JsonObject options) {
                return new TextDocumentRegistrationOptions();
            }
        };
        var first = registry.registerCapability(new JsonObject());
        var second = registry.registerCapability(new JsonObject());
        var iteration = registry.getOptions().iterator();
        assertSame(first, iteration.next());

        // Interleave changes as an asynchronous registration handler would.
        registry.unregisterCapability(first);
        var third = registry.registerCapability(new JsonObject());
        assertSame(second, iteration.next());
        assertFalse(iteration.hasNext());
        assertEquals(2, registry.getOptions().size());
        assertSame(second, registry.getOptions().get(0));
        assertSame(third, registry.getOptions().get(1));

        var beforeReset = registry.getOptions().iterator();
        registry.setServerCapabilities(new ServerCapabilities());
        assertTrue(registry.getOptions().isEmpty());
        assertSame(second, beforeReset.next());
        assertSame(third, beforeReset.next());
        assertFalse(beforeReset.hasNext());
    }
}

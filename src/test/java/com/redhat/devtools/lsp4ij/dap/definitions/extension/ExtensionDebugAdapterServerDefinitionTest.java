/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.definitions.extension;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests that a debug adapter from the {@code debugAdapterServer} extension point has the description that the
 * extension declares.
 */
public class ExtensionDebugAdapterServerDefinitionTest {

    @Test
    public void theDescriptionComesFromTheExtension() throws Exception {
        var extension = extension("<b>Demo</b> debug adapter");
        var definition = new ExtensionDebugAdapterServerDefinition(extension, List.of());
        assertEquals("<b>Demo</b> debug adapter", definition.getDescription());
    }

    @Test
    public void anExtensionWithoutDescriptionHasNone() throws Exception {
        var definition = new ExtensionDebugAdapterServerDefinition(extension(null), List.of());
        assertNull(definition.getDescription());
    }

    /**
     * Creates the extension as IntelliJ does from the {@code plugin.xml}, which sets the private fields of the bean.
     */
    private static DebugAdapterServerExtensionPointBean extension(String description) throws Exception {
        var extension = new DebugAdapterServerExtensionPointBean();
        set(extension, "id", "demoDap");
        set(extension, "name", "Demo Debug Adapter");
        set(extension, "description", description);
        return extension;
    }

    private static void set(DebugAdapterServerExtensionPointBean extension, String name, String value) throws Exception {
        Field field = DebugAdapterServerExtensionPointBean.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(extension, value);
    }
}

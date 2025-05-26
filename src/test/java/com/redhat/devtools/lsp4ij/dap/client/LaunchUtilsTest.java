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

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LaunchUtilsTest {

    @Test
    void testGetDapParameters_withInteger() {
        String json = "{\"port\": 1234}";
        Map<String, Object> result = LaunchUtils.getDapParameters(json, null);

        assertTrue(result.containsKey("port"), "Result should contain key 'port'");
        assertInstanceOf(Integer.class, result.get("port"), "port should be an Integer");
        assertEquals(1234, result.get("port"));
    }

    @Test
    void testGetDapParameters_withDouble() {
        String json = "{\"timeout\": 1.5}";
        Map<String, Object> result = LaunchUtils.getDapParameters(json, null);

        assertInstanceOf(Double.class, result.get("timeout"), "timeout should be a Double");
        assertEquals(1.5, (Double) result.get("timeout"), 0.0001);
    }

    @Test
    void testGetDapParameters_withNestedObject() {
        String json = "{\"server\": {\"port\": 8080}}";
        Map<String, Object> result = LaunchUtils.getDapParameters(json, null);

        assertInstanceOf(Map.class, result.get("server"));
        Map<?, ?> server = (Map<?, ?>) result.get("server");

        assertInstanceOf(Integer.class, server.get("port"));
        assertEquals(8080, server.get("port"));
    }

    @Test
    void testGetDapParameters_withContextReplacement() {
        String json = "{\"program\": \"${file}\"}";
        Map<String, String> context = Map.of("${file}", "/path/to/app.js");

        Map<String, Object> result = LaunchUtils.getDapParameters(json, context);

        assertEquals("/path/to/app.js", result.get("program"));
    }

    @Test
    void testResolveAttachPort_withInteger() {
        String json = "{\"debug\": {\"port\": 3000}}";
        Map<String, Object> result = LaunchUtils.getDapParameters(json, null);

        int port = LaunchUtils.resolveAttachPort("$debug.port", result);
        assertEquals(3000, port);
    }

    @Test
    void testResolveAttachPort_withDouble() {
        String json = "{\"debug\": {\"port\": 1234.0}}";
        Map<String, Object> result = LaunchUtils.getDapParameters(json, null);

        int port = LaunchUtils.resolveAttachPort("$debug.port", result);
        assertEquals(1234, port);
    }

    @Test
    void testResolveAttachAddress_withNestedPath() {
        String json = "{\"connection\": {\"host\": \"127.0.0.1\"}}";
        Map<String, Object> result = LaunchUtils.getDapParameters(json, null);

        String address = LaunchUtils.resolveAttachAddress("$connection.host", result);
        assertEquals("127.0.0.1", address);
    }

    @Test
    void testResolveAttachPort_withFallback() {
        int port = LaunchUtils.resolveAttachPort("5678", Map.of());
        assertEquals(5678, port);
    }

    @Test
    void testResolveAttachPort_withInvalidFallback() {
        int port = LaunchUtils.resolveAttachPort("notANumber", Map.of());
        assertEquals(-1, port);
    }
}

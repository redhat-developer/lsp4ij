/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LSP {@link SettingsHelper#parseJson(String, boolean)}.
 */
public class SettingsHelper_parseJsonTest {

    @Test
    void testParseJsonWithoutExpand() {
        String json = """
            {
              "gopls": {
                "ui": {
                  "inlayhint": {
                    "hints": {
                      "assignVariableTypes": true
                    }
                  }
                }
              }
            }
            """;

        JsonElement result = SettingsHelper.parseJson(json, false);

        assertTrue(result.isJsonObject());
        assertTrue(result.getAsJsonObject().has("gopls"));
        assertTrue(result.getAsJsonObject()
                        .getAsJsonObject("gopls")
                        .getAsJsonObject("ui")
                        .getAsJsonObject("inlayhint")
                        .getAsJsonObject("hints")
                        .get("assignVariableTypes").getAsBoolean());
    }

    @Test
    void testParseJsonWithExpand() {
        String json = """
            {
              "gopls.ui.inlayhint.hints.assignVariableTypes": true
            }
            """;

        JsonElement result = SettingsHelper.parseJson(json, true);
        assertTrue(result.isJsonObject());

        JsonObject root = result.getAsJsonObject();
        assertTrue(root.has("gopls"));

        JsonObject gopls = root.getAsJsonObject("gopls");
        JsonObject ui = gopls.getAsJsonObject("ui");
        JsonObject inlayhint = ui.getAsJsonObject("inlayhint");
        JsonObject hints = inlayhint.getAsJsonObject("hints");

        assertEquals(true, hints.get("assignVariableTypes").getAsBoolean());
    }

    @Test
    void testParseJsonMixedKeys() {
        String json = """
            {
              "a": {
                "b": {
                  "c": 1
                }
              },
              "a.b.d": 2
            }
            """;

        JsonElement result = SettingsHelper.parseJson(json, true);
        assertTrue(result.isJsonObject());

        JsonObject a = result.getAsJsonObject().getAsJsonObject("a");
        JsonObject b = a.getAsJsonObject("b");

        assertEquals(1, b.get("c").getAsInt());
        assertEquals(2, b.get("d").getAsInt());
    }

    @Test
    void testParseJsonWithoutDottedKeysExpandFalse() {
        String json = """
            {
              "plain": "value",
              "deep": {
                "nest": true
              }
            }
            """;

        JsonElement result = SettingsHelper.parseJson(json, false);

        assertTrue(result.isJsonObject());
        assertEquals("value", result.getAsJsonObject().get("plain").getAsString());
        assertTrue(result.getAsJsonObject().getAsJsonObject("deep").get("nest").getAsBoolean());
    }

    @Test
    void testParseJsonEmptyObject() {
        String json = "{}";
        JsonElement result = SettingsHelper.parseJson(json, true);
        assertTrue(result.isJsonObject());
        assertTrue(result.getAsJsonObject().entrySet().isEmpty());
    }
}

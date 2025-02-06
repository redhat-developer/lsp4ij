/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
import com.google.gson.JsonParser;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.StringReader;

/**
 * Tests for LSP {@link SettingsHelper}.
 */
public class SettingsHelperTest extends BasePlatformTestCase {
    // language=json
    private final String testJson = """
            {
                "mylsp": {
                    "myscalarsetting": "value",
                    "myobjectsettings": {
                        "subsettingA": 1,
                        "subsettingB": 2
                    }
                },
                "flat.scalar.value": "flat value",
                "flat.scalar.value2": "flat value2",
                "JimmerDTO.Classpath.FindBuilder": false,
                "JimmerDTO.Classpath.FindConfiguration": false
            }
            """;

    // language=json
    private final String testJuliaJson = """
            {
            	"julia.lint.call": true,
                "julia.inlayHints.static.enabled": true
            }
            """;

    public void testGetSettingsObjectValue() {
        var requestedPath = "mylsp";
        assertFindSettings(testJson, requestedPath, """
                {
                    "myscalarsetting": "value",
                    "myobjectsettings": {
                        "subsettingA": 1,
                        "subsettingB": 2
                    }
                }
                """);
    }

    public void testGetSettingsPrimitiveValue() {
        var requestedPath = "mylsp.myscalarsetting";
        assertFindSettings(testJson, requestedPath, "\"value\"");
    }

    public void testGetSettingsDeepPrimitiveValue() {
        var requestedPath = "mylsp.myobjectsettings.subsettingA";
        assertFindSettings(testJson, requestedPath, "1");
    }

    public void testGetSettingsNonExistingValue() {
        var requestedPath = "mylsp.nonexistant";
        assertFindSettings(testJson, requestedPath, null);
    }

    public void testGetSettingsEmptyJson() {
        var requestedPath = "mylsp.myobjectsettings.subsettingA";
        assertFindSettings("{}", requestedPath, null);
    }

    public void testFlatScalarValue() {
        var requestedPath = "flat.scalar.value";
        assertFindSettings(testJson, requestedPath, "\"flat value\"");
    }

    public void testFlatScalar() {
        var requestedPath = "flat.scalar";
        assertFindSettings(testJson, requestedPath, """
                {
                    "flat.scalar.value": "flat value",
                    "flat.scalar.value2": "flat value2"
                }
                """);
    }

    public void testFlatScalarNonExisting() {
        var requestedPath = "flat.scalar.nonexistant";
        assertFindSettings(testJson, requestedPath, null);
    }

    public void testJimmerDTO() {
        var requestedPath = "JimmerDTO";
        assertFindSettings(testJson, requestedPath, """
                {
                    "JimmerDTO.Classpath.FindBuilder": false,
                    "JimmerDTO.Classpath.FindConfiguration": false
                }
                """);
    }

    public void testJimmerDTOClasspath() {
        var requestedPath = "JimmerDTO.Classpath";
        assertFindSettings(testJson, requestedPath, """
                {
                    "JimmerDTO.Classpath.FindBuilder": false,
                    "JimmerDTO.Classpath.FindConfiguration": false
                }
                """);
    }

    public void testJimmerDTOClasspathFindBuilder() {
        var requestedPath = "JimmerDTO.Classpath.FindBuilder";
        assertFindSettings(testJson, requestedPath, "false");
    }

    public void testJulia() {
        JsonObject juliaConfig = parseJsonObject(testJuliaJson);
        assertFindSettings(juliaConfig, "julia.lint.call", "true");
        assertFindSettings(juliaConfig, "julia.inlayHints.static.enabled", "true");
    }

    private static void assertFindSettings(@NotNull String json,
                                           @NotNull String section,
                                           @Nullable String expectedJsonText) {
        JsonObject jsonObject = parseJsonObject(json);
        assertFindSettings(jsonObject, section, expectedJsonText);
    }

    private static void assertFindSettings(@NotNull JsonObject jsonObject,
                                           @NotNull String section,
                                           @Nullable String expectedJsonText) {
        JsonElement expectedJson = expectedJsonText == null ? null : JsonParser.parseReader(new StringReader(expectedJsonText));

        JsonElement result = SettingsHelper.findSettings(section, jsonObject);

        assertEquals(result, expectedJson);
    }

    private static JsonObject parseJsonObject(@NotNull String json) {
        return JsonParser.parseReader(new StringReader(json)).getAsJsonObject();
    }
}

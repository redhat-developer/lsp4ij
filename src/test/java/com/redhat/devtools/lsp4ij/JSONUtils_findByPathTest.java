/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link JSONUtils#findByPath(JsonObject, String[])}.
 */
public class JSONUtils_findByPathTest {

    private static final String TYPESCRIPT_CONFIG = """
                {
                  "typescript": {
                    "inlayHints": {
                      "includeInlayEnumMemberValueHints": true,
                      "includeInlayFunctionLikeReturnTypeHints": true,
                      "includeInlayFunctionParameterTypeHints": true,
                      "includeInlayParameterNameHints": "all",
                      "includeInlayParameterNameHintsWhenArgumentMatchesName": true,
                      "includeInlayPropertyDeclarationTypeHints": true,
                      "includeInlayVariableTypeHints": true,
                      "includeInlayVariableTypeHintsWhenTypeMatchesName": true
                    },
                    "implementationsCodeLens": {
                      "enabled": true
                    },
                    "referencesCodeLens": {
                      "enabled": true,
                      "showOnAllFunctions": true
                    }
                  }
                }
            """;

    @Test
    public void testFindByPathLevel0() {
        assertJsonPath(TYPESCRIPT_CONFIG, new String[]{}, TYPESCRIPT_CONFIG);
    }

    @Test
    public void testFindByPathLevel1() {
        assertJsonPath(TYPESCRIPT_CONFIG, "typescript".split("[.]"), """
                {
                    "inlayHints": {
                      "includeInlayEnumMemberValueHints": true,
                      "includeInlayFunctionLikeReturnTypeHints": true,
                      "includeInlayFunctionParameterTypeHints": true,
                      "includeInlayParameterNameHints": "all",
                      "includeInlayParameterNameHintsWhenArgumentMatchesName": true,
                      "includeInlayPropertyDeclarationTypeHints": true,
                      "includeInlayVariableTypeHints": true,
                      "includeInlayVariableTypeHintsWhenTypeMatchesName": true
                    },
                    "implementationsCodeLens": {
                      "enabled": true
                    },
                    "referencesCodeLens": {
                      "enabled": true,
                      "showOnAllFunctions": true
                    }
                }
                """);
    }

    @Test
    public void testFindByPathLevel2() {
        assertJsonPath(TYPESCRIPT_CONFIG, "typescript.inlayHints".split("[.]"), """
                {
                  "includeInlayEnumMemberValueHints": true,
                  "includeInlayFunctionLikeReturnTypeHints": true,
                  "includeInlayFunctionParameterTypeHints": true,
                  "includeInlayParameterNameHints": "all",
                  "includeInlayParameterNameHintsWhenArgumentMatchesName": true,
                  "includeInlayPropertyDeclarationTypeHints": true,
                  "includeInlayVariableTypeHints": true,
                  "includeInlayVariableTypeHintsWhenTypeMatchesName": true
                }
                """);
    }

    @Test
    public void testFindByPathLevel3() {
        assertJsonPath(TYPESCRIPT_CONFIG, "typescript.inlayHints.includeInlayEnumMemberValueHints".split("[.]"),
                "true");
    }

    @Test
    public void testFindByInvalidPathLevel1() {
        assertJsonPath(TYPESCRIPT_CONFIG, "foo".split("[.]"), null);
    }

    @Test
    public void testFindByInvalidPathLevel2() {
        assertJsonPath(TYPESCRIPT_CONFIG, "typescript.foo".split("[.]"), null);
    }

    private static void assertJsonPath(String config, String[] paths, String expected) {
        JsonObject json = (JsonObject) JsonParser.parseString(config);
        var actualJson = JSONUtils.findByPath(json, paths);
        var expectedJson = expected != null ? JsonParser.parseString(expected) : null;
        Assert.assertEquals(expectedJson, actualJson);
    }

}

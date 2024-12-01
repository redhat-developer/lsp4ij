package com.redhat.devtools.lsp4ij.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.StringReader;

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
                }
            }
            """;

    public void testGetSettingsPrimitiveValue() {
        JsonObject jsonObject = JsonParser.parseReader(new StringReader(testJson)).getAsJsonObject();
        String[] requestedPath = new String[]{"mylsp", "myscalarsetting"};

        JsonElement result = SettingsHelper.findSettings(requestedPath, jsonObject);

        assertNotNull(result);
        assertTrue(result.isJsonPrimitive());
        assertEquals("value", result.getAsString());
    }

    public void testGetSettingsObjectValue() {
        JsonObject jsonObject = JsonParser.parseReader(new StringReader(testJson)).getAsJsonObject();
        String[] requestedPath = new String[]{"mylsp", "myobjectsettings"};

        JsonElement result = SettingsHelper.findSettings(requestedPath, jsonObject);

        assertNotNull(result);
        assertTrue(result.isJsonObject());
        assertEquals(1, result.getAsJsonObject().get("subsettingA").getAsInt());
        assertEquals(2, result.getAsJsonObject().get("subsettingB").getAsInt());
    }

    public void testGetSettingsNonExistingValue() {
        JsonObject jsonObject = JsonParser.parseReader(new StringReader(testJson)).getAsJsonObject();
        String[] requestedPath = new String[]{"mylsp", "nonexistant"};

        JsonElement result = SettingsHelper.findSettings(requestedPath, jsonObject);

        assertNull(result);
    }

    public void testGetSettingsEmptyJson() {
        JsonObject jsonObject = JsonParser.parseReader(new StringReader("{}")).getAsJsonObject();
        String[] requestedPath = new String[]{"mylsp", "myobjectsettings"};

        JsonElement result = SettingsHelper.findSettings(requestedPath, jsonObject);

        assertNull(result);
    }

    public void testGetSettingsIdentityOnEmptySections() {
        JsonObject jsonObject = JsonParser.parseReader(new StringReader(testJson)).getAsJsonObject();
        String[] requestedPath = new String[0];

        JsonElement result = SettingsHelper.findSettings(requestedPath, jsonObject);

        assertEquals(jsonObject, result);
    }
}

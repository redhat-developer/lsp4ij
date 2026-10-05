/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigurationSerializationTest {
    private static MessageJsonHandler handler() {
        return new MessageJsonHandler(ServiceEndpoints.getSupportedMethods(LanguageServer.class),
                JSONUtils::configureCompatibilityAdapters);
    }

    @Test
    public void explicitNullResetSurvivesTheNotificationWireRoundTrip() {
        var settings = JsonParser.parseString("""
                {"compiler": {"sourceRoots": null, "modulePath": [], "enabled": false}}
                """);
        var message = new NotificationMessage();
        message.setMethod("workspace/didChangeConfiguration");
        message.setParams(new DidChangeConfigurationParams(settings));
        var handler = handler();
        String wire = handler.serialize(message);
        assertEquals(settings, JsonParser.parseString(wire).getAsJsonObject()
                .getAsJsonObject("params").get("settings"));
        var decoded = (NotificationMessage) handler.parseMessage(wire);
        var params = (DidChangeConfigurationParams) decoded.getParams();
        assertEquals(settings, (JsonElement) params.getSettings());
    }

    @Test
    public void explicitNullInMapConfigurationIsNotLost() {
        var settings = Map.of("compiler", Collections.singletonMap("sourceRoots", null));
        String json = handler().getGson().toJson(new DidChangeConfigurationParams(settings));
        assertEquals(JsonParser.parseString("""
                {"settings": {"compiler": {"sourceRoots": null}}}
                """), JsonParser.parseString(json));
    }

    @Test
    public void optionalProtocolFieldsRemainOmittedAfterWritingConfiguration() {
        var params = new DidChangeConfigurationParams(JsonParser.parseString("""
                {"reset": null}
                """));
        var completion = new CompletionItem("name");
        var serialized = JsonParser.parseString(handler().getGson().toJson(List.of(params, completion))).getAsJsonArray();
        assertTrue(serialized.get(0).getAsJsonObject().getAsJsonObject("settings").has("reset"));
        var item = serialized.get(1).getAsJsonObject();
        assertEquals("name", item.get("label").getAsString());
        assertFalse(item.has("detail"));
        assertFalse(item.has("insertText"));
    }
}

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
import com.redhat.devtools.lsp4ij.internal.adapters.ConfigurationResult;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public void explicitNullInInitializationOptionsSurvivesTheRequestWireRoundTrip() {
        var options = JsonParser.parseString("""
                {"a": null, "b": "x", "c": {"d": null}, "e": [{"f": null}]}
                """);
        var params = new InitializeParams();
        params.setInitializationOptions(options);
        var message = new RequestMessage();
        message.setId(1);
        message.setMethod("initialize");
        message.setParams(params);
        var handler = handler();
        String wire = handler.serialize(message);
        var sent = JsonParser.parseString(wire).getAsJsonObject().getAsJsonObject("params");
        assertEquals(options, sent.get("initializationOptions"));
        // The other fields are written as before: the unset ones are left out, except processId and rootUri, which
        // LSP4J always sends
        assertEquals(Set.of("processId", "rootUri", "initializationOptions"), sent.keySet());
        var decoded = (RequestMessage) handler.parseMessage(wire);
        assertEquals(options, ((InitializeParams) decoded.getParams()).getInitializationOptions());
    }

    @Test
    public void initializationOptionsGivenAsAMapAreWrittenAsBefore() {
        var params = new InitializeParams();
        params.setInitializationOptions(Map.of("b", "x", "c", Map.of("d", List.of(1, 2))));
        var message = new RequestMessage();
        message.setId(1);
        message.setMethod("initialize");
        message.setParams(params);
        String wire = handler().serialize(message);
        // Written by LSP4J's own writer, which the adapter calls for options not given as JSON
        assertEquals(JsonParser.parseString("""
                {"b": "x", "c": {"d": [1, 2]}}
                """), JsonParser.parseString(wire).getAsJsonObject().getAsJsonObject("params").get("initializationOptions"));
    }

    @Test
    public void configurationReplyKeepsTheNullsOfJsonSettingsOnly() {
        var reply = new ConfigurationResult();
        reply.add(JsonParser.parseString("""
                {"a": null, "b": [{"c": null}]}
                """));
        reply.add(new CompletionItem("name"));
        reply.add(null);
        var message = new ResponseMessage();
        message.setId(1);
        message.setResult(reply);
        String wire = handler().serialize(message);
        // An object setting is written as before, without its unset fields
        assertEquals(JsonParser.parseString("""
                [{"a": null, "b": [{"c": null}]}, {"label": "name"}, null]
                """), JsonParser.parseString(wire).getAsJsonObject().get("result"));
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

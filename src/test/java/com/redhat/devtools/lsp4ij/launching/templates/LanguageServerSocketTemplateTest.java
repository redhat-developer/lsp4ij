/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.templates;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.intellij.testFramework.LightPlatformTestCase;
import com.redhat.devtools.lsp4ij.server.definition.launching.TransportSupport;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Tests the "socket" of a language server template: what an exported user-defined language server writes, and what an
 * imported template reads.
 */
public class LanguageServerSocketTemplateTest extends LightPlatformTestCase {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(UserDefinedLanguageServerDefinition.class, new LanguageServerDefinitionSerializer())
            .registerTypeAdapter(LanguageServerTemplate.class, new LanguageServerTemplateDeserializer())
            .create();

    public void testAnExportedLanguageServerOverASocketHasASocket() {
        var definition = definition("node server.js --socket=${port}");
        definition.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0));

        JsonObject socket = GSON.toJsonTree(definition).getAsJsonObject().getAsJsonObject("socket");

        assertEquals("startAndAccept", socket.get("mode").getAsString());
        assertFalse(socket.has("host"));
        assertEquals(0, socket.get("port").getAsInt());
    }

    public void testAnExportedLanguageServerThatRunsAlreadyHasItsHost() {
        var definition = definition("");
        definition.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 6005));

        JsonObject socket = GSON.toJsonTree(definition).getAsJsonObject().getAsJsonObject("socket");

        assertEquals("attach", socket.get("mode").getAsString());
        assertEquals("127.0.0.1", socket.get("host").getAsString());
        assertEquals(6005, socket.get("port").getAsInt());
    }

    public void testAnImportedSocketWithoutAPortLetsLSP4IJChooseOne() {
        var template = GSON.fromJson("""
                {
                  "id": "server",
                  "name": "Server",
                  "programArgs": { "default": "server --socket=${port}" },
                  "socket": { "mode": "startAndAccept" }
                }""", LanguageServerTemplate.class);

        assertEquals(new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0), template.getTransport());
    }

    public void testAnExportedLanguageServerOverItsStandardInputAndOutputHasNoSocket() {
        assertFalse(GSON.toJsonTree(definition("server --stdio")).getAsJsonObject().has("socket"));
    }

    public void testAnImportedTemplateWithASocketTalksOverIt() {
        var template = GSON.fromJson("""
                {
                  "id": "godot",
                  "name": "Godot",
                  "programArgs": { "default": "" },
                  "socket": { "mode": "attach", "host": "127.0.0.1", "port": 6005 }
                }""", LanguageServerTemplate.class);

        assertEquals(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 6005), template.getTransport());
    }

    public void testAnImportedTemplateWithoutASocketTalksOverTheStandardInputAndOutput() {
        var template = GSON.fromJson("""
                { "id": "server", "name": "Server", "programArgs": { "default": "server --stdio" } }""",
                LanguageServerTemplate.class);

        assertSame(UserDefinedTransport.STDIO, template.getTransport());
    }

    public void testATemplateListsTheTransportsItsLanguageServerSupports() {
        var template = GSON.fromJson("""
                {
                  "id": "server",
                  "name": "Server",
                  "programArgs": { "default": "server --stdio" },
                  "transports": [
                    { "mode": "stdio", "argument": "--stdio" },
                    { "mode": "startAndAccept", "argument": "--socket=${port}" },
                    { "mode": "pipe" },
                    { "mode": "attach" }
                  ]
                }""", LanguageServerTemplate.class);

        assertEquals(List.of(new TransportSupport(UserDefinedTransport.Mode.STDIO, "--stdio"),
                        new TransportSupport(UserDefinedTransport.Mode.START_AND_ACCEPT, "--socket=${port}"),
                        new TransportSupport(UserDefinedTransport.Mode.ATTACH, null)),
                template.getSupportedTransports());
        // listing a socket doesn't make it the transport
        assertSame(UserDefinedTransport.STDIO, template.getTransport());
    }

    public void testATemplateWithoutTransportsDoesNotSayWhichItsLanguageServerSupports() {
        var template = GSON.fromJson("""
                { "id": "server", "name": "Server", "programArgs": { "default": "server --stdio" } }""",
                LanguageServerTemplate.class);

        assertEmpty(template.getSupportedTransports());
    }

    public void testTheBundledTemplatesSayWhichTransportsTheirLanguageServersSupport() throws IOException {
        // typescript-language-server requires --stdio and rejects --socket
        assertEquals(List.of(new TransportSupport(UserDefinedTransport.Mode.STDIO, "--stdio")),
                bundled("typescript-language-server").getSupportedTransports());
        // the servers built on vscode-languageserver connect to the port of --socket
        for (var name : List.of("vscode-css-language-server", "vscode-html-language-server", "vscode-eslint-language-server")) {
            assertTrue(name, bundled(name).getSupportedTransports()
                    .contains(new TransportSupport(UserDefinedTransport.Mode.START_AND_ACCEPT, "--socket=${port}")));
        }
    }

    public void testAnExportedSocketIsImportedAgain() {
        var definition = definition("server --port ${port}");
        var transport = new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, null, 4389);
        definition.setTransport(transport);

        var json = GSON.toJsonTree(definition).getAsJsonObject();
        json.addProperty("id", "server");
        var template = GSON.fromJson(json, LanguageServerTemplate.class);

        assertEquals(transport, template.getTransport());
        assertEquals("server --port ${port}", template.getProgramArgs());
    }

    private static LanguageServerTemplate bundled(String name) throws IOException {
        var json = LanguageServerSocketTemplateTest.class.getResourceAsStream("/templates/lsp/" + name + "/template.json");
        assertNotNull(name, json);
        try (var reader = new InputStreamReader(json, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, LanguageServerTemplate.class);
        }
    }

    private static UserDefinedLanguageServerDefinition definition(String commandLine) {
        return new UserDefinedLanguageServerDefinition("id", null, "Server", null, null, commandLine, null,
                Map.of(), false, "", false, "", "", "", "", "", "");
    }
}

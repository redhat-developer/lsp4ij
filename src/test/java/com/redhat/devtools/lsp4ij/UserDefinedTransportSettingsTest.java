/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;

import java.util.Map;

/**
 * Tests that the transport of a user-defined language server persists in its settings, and that the language server
 * definition made from the settings, as when the IDE starts, has it.
 */
public class UserDefinedTransportSettingsTest extends LightPlatformTestCase {

    private static final UserDefinedTransport RUNNING =
            new UserDefinedTransport(UserDefinedTransport.Mode.CONNECT, "127.0.0.1", 6005);

    public void testASocketPersists() {
        var settings = settings();
        settings.setTransport(RUNNING);

        var element = XmlSerializer.serialize(settings);
        var loaded = XmlSerializer.deserialize(element, UserDefinedLanguageServerItemSettings.class);

        assertEquals(RUNNING, loaded.getTransport());
        assertEquals("connect", loaded.getTransportMode());
        assertEquals("127.0.0.1", loaded.getSocketHost());
        assertEquals(6005, loaded.getSocketPort());
    }

    public void testTheStandardInputAndOutputPersistsAsNoMode() {
        var settings = settings();
        settings.setTransport(UserDefinedTransport.STDIO);

        var loaded = XmlSerializer.deserialize(XmlSerializer.serialize(settings), UserDefinedLanguageServerItemSettings.class);

        assertNull(loaded.getTransportMode());
        assertSame(UserDefinedTransport.STDIO, loaded.getTransport());
    }

    public void testTheTransportIsNoPropertyOfItsOwn() {
        var settings = settings();
        settings.setTransport(RUNNING);

        var xml = JDOMUtil.write(XmlSerializer.serialize(settings));

        assertTrue(xml, xml.contains("transportMode"));
        assertFalse(xml, xml.contains("name=\"transport\""));
    }

    public void testTheDefinitionMadeFromTheSettingsHasTheirTransport() {
        var settings = settings();
        settings.setTransport(RUNNING);

        var definition = LanguageServersRegistry.createUserDefinedServerDefinition("server", settings, null);

        assertEquals(RUNNING, definition.getTransport());
        assertEquals("Server", definition.getDisplayName());
    }

    private static UserDefinedLanguageServerItemSettings settings() {
        var settings = new UserDefinedLanguageServerItemSettings();
        settings.setServerId("server");
        settings.setServerName("Server");
        settings.setCommandLine("");
        settings.setUserEnvironmentVariables(Map.of());
        return settings;
    }
}

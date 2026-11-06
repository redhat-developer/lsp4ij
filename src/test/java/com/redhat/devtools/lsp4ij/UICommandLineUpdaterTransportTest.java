/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.testFramework.LightPlatformTestCase;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings;
import com.redhat.devtools.lsp4ij.launching.ui.UICommandLineUpdater;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Tests the command which an installer sets, as with {@code "configureServer"} and {@code "update": true}, for a
 * language server over a socket: the installer's command is written for the standard input and output, unless it
 * takes the port, so the settings never hold a socket which the command can't use.
 */
public class UICommandLineUpdaterTransportTest extends LightPlatformTestCase {

    private static final String SERVER_ID = "installer-transport-test";

    private UserDefinedLanguageServerItemSettings settings;
    private UserDefinedLanguageServerDefinition definition;

    @Override
    protected void tearDown() throws Exception {
        try {
            UserDefinedLanguageServerSettings.getInstance().removeServerDefinition(SERVER_ID);
        } finally {
            super.tearDown();
        }
    }

    public void testAnInstallerCommandWithoutThePortStartsTheLanguageServerOverItsStandardInputAndOutput() {
        var updater = updater(new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0),
                "node server.js --socket=${port}");

        updater.setCommandLine("node server.js --stdio");

        assertEquals("node server.js --stdio", definition.getCommandLine());
        assertSame(UserDefinedTransport.STDIO, definition.getTransport());
        assertEquals("node server.js --stdio", settings.getCommandLine());
        assertSame(UserDefinedTransport.STDIO, settings.getTransport());
    }

    public void testAnInstallerCommandWhichTakesThePortKeepsTheSocket() {
        var socket = new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, null, 4389);
        var updater = updater(socket, "server --port ${port}");

        updater.setCommandLine("/opt/server/bin/server --port ${port}");

        assertEquals("/opt/server/bin/server --port ${port}", settings.getCommandLine());
        assertEquals(socket, definition.getTransport());
        assertEquals(socket, settings.getTransport());
    }

    public void testALanguageServerWhichRunsAlreadyKeepsItsSocket() {
        var running = new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 6005);
        var updater = updater(running, "");

        updater.setCommandLine("node server.js --stdio");

        assertEquals(running, definition.getTransport());
        assertEquals(running, settings.getTransport());
    }

    private @NotNull UICommandLineUpdater updater(@NotNull UserDefinedTransport transport, @NotNull String commandLine) {
        settings = new UserDefinedLanguageServerItemSettings();
        settings.setServerId(SERVER_ID);
        settings.setServerName("Installer transport test");
        settings.setCommandLine(commandLine);
        settings.setUserEnvironmentVariables(Map.of());
        settings.setTransport(transport);
        UserDefinedLanguageServerSettings.getInstance().setUserDefinedLanguageServerSettings(SERVER_ID, settings);
        definition = LanguageServersRegistry.createUserDefinedServerDefinition(SERVER_ID, settings, null);
        return new UICommandLineUpdater(definition, getProject());
    }
}

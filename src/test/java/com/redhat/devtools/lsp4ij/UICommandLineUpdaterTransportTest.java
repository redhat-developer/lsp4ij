/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import com.redhat.devtools.lsp4ij.launching.ui.UICommandLineUpdater;
import com.redhat.devtools.lsp4ij.server.definition.launching.TransportSupport;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tests the command which an installer sets, as with {@code "configureServer"} and {@code "update": true}, for a
 * language server over a socket: the installer's command is written for the standard input and output, unless it
 * takes the port, so the settings never hold a socket which the command can't use.
 */
public class UICommandLineUpdaterTransportTest extends LightPlatformTestCase {

    private static final String SERVER_ID = "installer-transport-test";
    // No bundled template lists only socket modes, so the tests add one
    private static final String SOCKET_ONLY_TEMPLATE = "socket-only-test-server";

    private UserDefinedLanguageServerItemSettings settings;
    private UserDefinedLanguageServerDefinition definition;
    private final List<Notification> warnings = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                if (notification.getType() == NotificationType.WARNING) {
                    warnings.add(notification);
                }
            }
        });
    }

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
        // and the user is told so
        UIUtil.dispatchAllInvocationEvents();
        assertEquals(1, warnings.size());
        assertEquals(LanguageServerBundle.message("language.server.installer.transport.stdio"), warnings.get(0).getContent());
        assertTrue(warnings.get(0).getContent().contains("${port}"));
    }

    public void testAnInstallerCommandForAServerOverItsStandardInputAndOutputSaysNothing() {
        updater(UserDefinedTransport.STDIO, "node server.js --stdio").setCommandLine("/opt/node/bin/node server.js --stdio");

        UIUtil.dispatchAllInvocationEvents();
        assertEmpty(warnings);
    }

    public void testAnInstallerCommandWhichTakesThePortKeepsTheSocket() {
        var socket = new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, null, 4389);
        var updater = updater(socket, "server --port ${port}");

        updater.setCommandLine("/opt/server/bin/server --port ${port}");

        assertEquals("/opt/server/bin/server --port ${port}", settings.getCommandLine());
        assertEquals(socket, definition.getTransport());
        assertEquals(socket, settings.getTransport());
        UIUtil.dispatchAllInvocationEvents();
        assertEmpty(warnings);
    }

    public void testALanguageServerWhichRunsAlreadyKeepsItsSocket() {
        var running = new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 6005);
        var updater = updater(running, "");

        updater.setCommandLine("node server.js --stdio");

        assertEquals(running, definition.getTransport());
        assertEquals(running, settings.getTransport());
    }

    public void testAnInstallerCommandWithoutThePortKeepsTheSocketOfALanguageServerWhichTalksOnlyOverASocket() {
        var connect = new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, null, 0);
        var updater = updater(connect, "server --port ${port}", socketOnlyTemplate());

        updater.setCommandLine("/opt/server/bin/server");

        assertEquals("/opt/server/bin/server", definition.getCommandLine());
        assertEquals("/opt/server/bin/server", settings.getCommandLine());
        // over its standard input and output, it couldn't talk at all
        assertEquals(connect, definition.getTransport());
        assertEquals(connect, settings.getTransport());
        // but it can't learn the port which LSP4IJ chooses, and the user is told so
        UIUtil.dispatchAllInvocationEvents();
        assertEquals(1, warnings.size());
        assertEquals(LanguageServerBundle.message("language.server.installer.transport.socket.only"),
                warnings.get(0).getContent());
    }

    public void testAFixedPortOfALanguageServerWhichTalksOnlyOverASocketIsKeptWithoutAWarning() {
        // the command may name the port itself
        var connect = new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, null, 4389);
        var updater = updater(connect, "server --port 4389", socketOnlyTemplate());

        updater.setCommandLine("/opt/server/bin/server --port 4389");

        assertEquals("/opt/server/bin/server --port 4389", settings.getCommandLine());
        assertEquals(connect, definition.getTransport());
        assertEquals(connect, settings.getTransport());
        UIUtil.dispatchAllInvocationEvents();
        assertEmpty(warnings);
    }

    /**
     * Adds a template which lists only socket modes to the bundled ones, among which LSP4IJ finds the template of a
     * language server by its id, until the test ends, and returns its id.
     */
    private @NotNull String socketOnlyTemplate() {
        // The bundled templates are read from the jar of LSP4IJ in the test sandbox, which is outside the roots that a
        // test may read when the checkout isn't in the user's home directory
        String templates = PathManager.getResourceRoot(LanguageServerTemplateManager.class, "/templates/lsp");
        assertNotNull(templates);
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), templates);
        var template = new LanguageServerTemplate();
        template.setId(SOCKET_ONLY_TEMPLATE);
        template.setName("Socket-only server");
        template.setSupportedTransports(List.of(
                new TransportSupport(UserDefinedTransport.Mode.START_AND_CONNECT, "--port ${port}")));
        var bundled = LanguageServerTemplateManager.getInstance().getTemplates();
        bundled.add(template);
        Disposer.register(getTestRootDisposable(), () -> bundled.remove(template));
        return SOCKET_ONLY_TEMPLATE;
    }

    private @NotNull UICommandLineUpdater updater(@NotNull UserDefinedTransport transport, @NotNull String commandLine) {
        return updater(transport, commandLine, null);
    }

    private @NotNull UICommandLineUpdater updater(@NotNull UserDefinedTransport transport, @NotNull String commandLine,
                                                  @Nullable String templateId) {
        settings = new UserDefinedLanguageServerItemSettings();
        settings.setServerId(SERVER_ID);
        settings.setTemplateId(templateId);
        settings.setServerName("Installer transport test");
        settings.setCommandLine(commandLine);
        settings.setUserEnvironmentVariables(Map.of());
        settings.setTransport(transport);
        UserDefinedLanguageServerSettings.getInstance().setUserDefinedLanguageServerSettings(SERVER_ID, settings);
        definition = LanguageServersRegistry.createUserDefinedServerDefinition(SERVER_ID, settings, null);
        return new UICommandLineUpdater(definition, getProject());
    }
}

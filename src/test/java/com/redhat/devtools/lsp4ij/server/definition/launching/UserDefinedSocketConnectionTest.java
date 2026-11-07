/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.google.gson.JsonElement;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.server.SocketStreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.SocketTestConnectingServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Tests the connection to a user-defined language server in each transport: the provider its definition makes, the
 * port its command line takes, and the user's initialization options.
 */
public class UserDefinedSocketConnectionTest extends BasePlatformTestCase {

    private static final String LOOPBACK = InetAddress.getLoopbackAddress().getHostAddress();

    public void testTheStandardInputAndOutputIsTheProcessStreams() {
        var definition = definition("server --stdio", null);

        assertInstanceOf(definition.createConnectionProvider(getProject()), UserDefinedStreamConnectionProvider.class);
    }

    public void testALanguageServerThatRunsAlreadyIsReachedOnTheLoopbackAddressWithoutAHost() {
        var definition = definition("", null);
        definition.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, null, 6005));

        var provider = assertInstanceOf(definition.createConnectionProvider(getProject()),
                UserDefinedSocketStreamConnectionProvider.class);

        assertSame(SocketStreamConnectionProvider.Direction.CONNECT, provider.getDirection());
        assertEquals(6005, provider.getPort());
        assertTrue(provider.toString(), provider.toString().contains(LOOPBACK + ":6005"));
    }

    public void testABlankHostIsTheLoopbackAddress() {
        var definition = definition("", null);
        definition.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "  ", 6005));

        var provider = definition.createConnectionProvider(getProject());

        assertTrue(provider.toString(), provider.toString().contains(LOOPBACK + ":6005"));
    }

    public void testALanguageServerWithoutSettingsHasNoInitializationOptions() {
        var definition = definition("", "{\"provideFormatter\": true}");
        definition.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, null, 6005));
        var provider = assertInstanceOf(definition.createConnectionProvider(getProject()),
                UserDefinedSocketStreamConnectionProvider.class);

        // the definition isn't registered, so it has no settings
        assertNull(provider.getInitializationOptions(null));
        assertNull(provider.getExperimentalFeaturesPOJO());
    }

    public void testALanguageServerWhichListensIsReachedOnTheLoopbackAddress() {
        var definition = definition("server --port ${port}", null);
        definition.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, "example.com", 4389));

        var provider = assertInstanceOf(definition.createConnectionProvider(getProject()),
                UserDefinedSocketStreamConnectionProvider.class);

        assertSame(SocketStreamConnectionProvider.Direction.CONNECT, provider.getDirection());
        assertTrue(provider.toString(), provider.toString().contains(LOOPBACK + ":4389"));
    }

    public void testAStartedLanguageServerConnectsToTheFreePortItsCommandLineTakes() throws Exception {
        var definition = definition(java(SocketTestConnectingServer.class) + " ${port}", null);
        definition.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0));
        var provider = assertInstanceOf(definition.createConnectionProvider(getProject()),
                UserDefinedSocketStreamConnectionProvider.class);
        try {
            provider.start();
            int port = provider.getPort();
            assertTrue(port > 0);
            assertEquals(String.valueOf(port), provider.getCommands().get(provider.getCommands().size() - 1));

            provider.getOutputStream().write("ping\n".getBytes(StandardCharsets.UTF_8));
            provider.getOutputStream().flush();
            assertEquals("ping", readLine(provider.getInputStream()));
        } finally {
            provider.stop();
            var pid = provider.getPid();
            if (pid != null) {
                var process = ProcessHandle.of(pid);
                if (process.isPresent()) {
                    process.get().destroyForcibly();
                    // the bound only ends a hang
                    process.get().onExit().get(1, TimeUnit.HOURS);
                }
            }
        }
    }

    public void testTheInitializationOptionsAreTheUsersJson() {
        var definition = definition("", "{\"provideFormatter\": true}");
        definition.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, null, 6005));
        LanguageServersRegistry.getInstance().addServerDefinition(getProject(), definition, List.of());
        try {
            var provider = assertInstanceOf(definition.createConnectionProvider(getProject()),
                    UserDefinedSocketStreamConnectionProvider.class);

            JsonElement options = provider.getInitializationOptions(null);

            assertNotNull(options);
            assertTrue(options.getAsJsonObject().get("provideFormatter").getAsBoolean());
            assertNull("no experimental settings", provider.getExperimentalFeaturesPOJO());
        } finally {
            LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), definition);
        }
    }

    public void testEveryTransportSendsTheSameInitializationOptions() {
        var definition = definition("server --stdio", "{\"provideFormatter\": true}");
        LanguageServersRegistry.getInstance().addServerDefinition(getProject(), definition, List.of());
        try {
            Object overStdio = definition.createConnectionProvider(getProject()).getInitializationOptions(null);
            definition.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, null, 6005));
            Object overSocket = definition.createConnectionProvider(getProject()).getInitializationOptions(null);

            assertNotNull(overSocket);
            assertEquals(overStdio, overSocket);
        } finally {
            LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), definition);
        }
    }

    private static UserDefinedLanguageServerDefinition definition(String commandLine, String initializationOptions) {
        return new UserDefinedLanguageServerDefinition(UUID.randomUUID().toString(), null, "Server", null, null,
                commandLine, null, Map.of(), false, null, false, null, initializationOptions, null, null, null, null);
    }

    /**
     * Returns the command line which runs the given program of the tests in a Java process.
     */
    private static String java(Class<?> program) {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return "\"" + java + "\" -cp \"" + PathManager.getJarPathForClass(program) + "\" " + program.getName();
    }

    private static String readLine(InputStream in) throws IOException {
        var line = new ByteArrayOutputStream();
        for (int b = in.read(); b != '\n'; b = in.read()) {
            if (b == -1) {
                throw new IOException("The connection ended before the line did: " + line);
            }
            line.write(b);
        }
        return line.toString(StandardCharsets.UTF_8);
    }
}

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.LightPlatformTestCase;
import com.redhat.devtools.lsp4ij.server.SocketTestServers.ConnectingServer;
import com.redhat.devtools.lsp4ij.server.SocketTestServers.EndingServer;
import com.redhat.devtools.lsp4ij.server.SocketTestServers.ListeningServer;
import com.redhat.devtools.lsp4ij.server.SocketTestServers.WaitingServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests the socket connection to a language server in its three ways: to a language server that runs already, to a
 * language server which LSP4IJ starts and which listens, and from a language server which LSP4IJ starts and which
 * connects to LSP4IJ. The language servers which LSP4IJ starts are small Java programs of this test that echo lines.
 * <p>
 * No test waits on time: they wait for the connection, for lines, and for processes to end, with an hour's bound that
 * only ends a hang.
 */
public class SocketStreamConnectionProviderTest extends LightPlatformTestCase {

    private static final String HOST = InetAddress.getLoopbackAddress().getHostAddress();
    private static final long HANG_BOUND_HOURS = 1;

    public void testConnectsToALanguageServerThatRunsAlreadyAndLeavesItRunningWhenItStops() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var provider = SocketStreamConnectionProvider.connectTo(HOST, server.getLocalPort());
            var stops = countUnexpectedStops(provider);
            try {
                // the connection is made once the language server's socket takes it, before it accepts it
                provider.start();
                try (var accepted = server.accept()) {
                    assertTrue(provider.isAlive());
                    assertNull("LSP4IJ started no process", provider.getPid());
                    assertEquals(List.of(), provider.getCommands());

                    writeLine(provider.getOutputStream(), "ping");
                    assertEquals("ping", readLine(accepted.getInputStream()));
                    writeLine(accepted.getOutputStream(), "pong");
                    assertEquals("pong", readLine(provider.getInputStream()));

                    provider.stop();
                    assertEquals("the language server sees the connection close", -1, accepted.getInputStream().read());
                    assertFalse("and runs on", server.isClosed());
                    assertFalse(provider.isAlive());
                    assertEquals("its own stop is not unexpected", 0, stops.get());
                }
            } finally {
                provider.stop();
            }
        }
    }

    public void testCannotStartWhenNoLanguageServerListens() throws Exception {
        int port;
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = server.getLocalPort();
        }
        var provider = SocketStreamConnectionProvider.connectTo(HOST, port);

        assertThrows(CannotStartProcessException.class, provider::start);
        assertFalse(provider.isAlive());
    }

    public void testALanguageServerThatClosesTheConnectionStopsUnexpectedlyOnce() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var provider = SocketStreamConnectionProvider.connectTo(HOST, server.getLocalPort());
            var stops = countUnexpectedStops(provider);
            try {
                provider.start();
                server.accept().close();

                assertEquals(-1, provider.getInputStream().read());
                assertEquals(1, stops.get());
                assertFalse(provider.isAlive());
                assertEquals(-1, provider.getInputStream().read());
                assertEquals("reported once", 1, stops.get());
            } finally {
                provider.stop();
            }
        }
    }

    public void testReadingAfterItsOwnStopIsNoUnexpectedStop() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var provider = SocketStreamConnectionProvider.connectTo(HOST, server.getLocalPort());
            var stops = countUnexpectedStops(provider);
            provider.start();
            try (var ignored = server.accept()) {
                provider.stop();

                assertThrows(IOException.class, () -> provider.getInputStream().read());
                assertEquals(0, stops.get());
            }
        }
    }

    public void testStartsALanguageServerThatListensOnAFreePortAndConnectsToIt() throws Exception {
        var provider = SocketStreamConnectionProvider.startAndConnect(0, port -> java(ListeningServer.class, port));
        var log = log(provider);
        try {
            provider.start();
            int port = provider.getPort();
            assertTrue(port > 0);
            assertTrue(provider.isAlive());
            assertNotNull(provider.getPid());
            assertTrue(provider.getCommands().toString(), provider.getCommands().contains(String.valueOf(port)));

            writeLine(provider.getOutputStream(), "ping");
            assertEquals("ping", readLine(provider.getInputStream()));
            // the output of the language server is its log
            awaitLog(log, "listening on " + port);
        } finally {
            reap(provider);
        }
    }

    public void testStartsALanguageServerThatConnectsToAFreePortAndAcceptsItsConnection() throws Exception {
        var provider = SocketStreamConnectionProvider.startAndAccept(0, port -> java(ConnectingServer.class, port));
        var log = log(provider);
        try {
            provider.start();
            int port = provider.getPort();
            assertTrue(port > 0);
            assertTrue(provider.isAlive());
            assertNotNull(provider.getPid());
            assertTrue(provider.getCommands().toString(), provider.getCommands().contains(String.valueOf(port)));

            writeLine(provider.getOutputStream(), "ping");
            assertEquals("ping", readLine(provider.getInputStream()));
            awaitLog(log, "connecting to " + port);
        } finally {
            reap(provider);
        }
    }

    public void testALanguageServerThatEndsOnceConnectedStopsUnexpectedly() throws Exception {
        var provider = SocketStreamConnectionProvider.startAndAccept(0, port -> java(ConnectingServer.class, port));
        var stops = countUnexpectedStops(provider);
        try {
            provider.start();
            writeLine(provider.getOutputStream(), "bye");

            // it ends, which closes the connection
            assertEquals(-1, provider.getInputStream().read());
            assertEquals(1, stops.get());
            awaitExit(provider);
            assertFalse(provider.isAlive());
            assertEquals("reported once", 1, stops.get());
        } finally {
            reap(provider);
        }
    }

    public void testCannotStartWhenTheLanguageServerEndsBeforeItAcceptsTheConnection() throws Exception {
        var provider = SocketStreamConnectionProvider.startAndConnect(0, port -> java(EndingServer.class, port));
        var stops = countUnexpectedStops(provider);
        try {
            assertThrows(CannotStartProcessException.class, provider::start);
            awaitExit(provider);
            assertEquals("start() reports it", 0, stops.get());
        } finally {
            reap(provider);
        }
    }

    public void testCannotStartWhenTheLanguageServerEndsBeforeItConnects() throws Exception {
        var provider = SocketStreamConnectionProvider.startAndAccept(0, port -> java(EndingServer.class, port));
        var stops = countUnexpectedStops(provider);
        try {
            assertThrows(CannotStartProcessException.class, provider::start);
            awaitExit(provider);
            assertEquals("start() reports it", 0, stops.get());
        } finally {
            reap(provider);
        }
    }

    public void testStopEndsAStartThatWaitsForTheLanguageServerToListen() throws Exception {
        assertStopEndsTheStart(SocketStreamConnectionProvider.startAndConnect(0, port -> java(WaitingServer.class, port)));
    }

    public void testStopEndsAStartThatWaitsForTheLanguageServerToConnect() throws Exception {
        assertStopEndsTheStart(SocketStreamConnectionProvider.startAndAccept(0, port -> java(WaitingServer.class, port)));
    }

    public void testStartsOnce() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var provider = SocketStreamConnectionProvider.connectTo(HOST, server.getLocalPort());
            try {
                provider.start();
                assertThrows(CannotStartProcessException.class, provider::start);
            } finally {
                provider.stop();
            }
            assertThrows(CannotStartProcessException.class, provider::start);
        }
    }

    public void testARunningLanguageServerNeedsItsPort() {
        assertThrows(IllegalArgumentException.class, () -> SocketStreamConnectionProvider.connectTo(HOST, 0));
        assertThrows(IllegalArgumentException.class, () -> SocketStreamConnectionProvider.connectTo(HOST, 65536));
    }

    /**
     * Stops the provider while its start waits for the language server, which never listens nor connects, once the
     * language server has logged that it runs.
     */
    private static void assertStopEndsTheStart(SocketStreamConnectionProvider provider) throws Exception {
        var log = log(provider);
        try {
            var start = CompletableFuture.runAsync(() -> {
                try {
                    provider.start();
                } catch (CannotStartProcessException e) {
                    throw new CompletionException(e);
                }
            });
            awaitLog(log, "waiting");

            provider.stop();

            try {
                start.get(HANG_BOUND_HOURS, TimeUnit.HOURS);
                fail("start() must fail once the provider is stopped");
            } catch (ExecutionException e) {
                assertInstanceOf(e.getCause(), CannotStartProcessException.class);
            }
            awaitExit(provider);
        } finally {
            reap(provider);
        }
    }

    /**
     * Waits for the given line of the log, past the lines before it, such as what the JVM prints when it starts.
     */
    private static void awaitLog(LinkedBlockingQueue<String> log, String expected) throws InterruptedException {
        for (String line = log.poll(HANG_BOUND_HOURS, TimeUnit.HOURS); !expected.equals(line);
             line = log.poll(HANG_BOUND_HOURS, TimeUnit.HOURS)) {
            assertNotNull("The log has no line \"" + expected + "\"", line);
        }
    }

    private static AtomicInteger countUnexpectedStops(StreamConnectionProvider provider) {
        var stops = new AtomicInteger();
        provider.addUnexpectedServerStopHandler(stops::incrementAndGet);
        return stops;
    }

    private static LinkedBlockingQueue<String> log(StreamConnectionProvider provider) {
        var log = new LinkedBlockingQueue<String>();
        provider.addLogErrorHandler(log::add);
        return log;
    }

    /**
     * Returns the command line which runs the given program of this test in a Java process, with the port.
     */
    private static GeneralCommandLine java(Class<?> program, int port) {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = PathManager.getJarPathForClass(program);
        return new GeneralCommandLine(java, "-cp", classes, program.getName(), String.valueOf(port));
    }

    private static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
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

    private static void awaitExit(SocketStreamConnectionProvider provider) throws Exception {
        var pid = provider.getPid();
        if (pid != null) {
            var process = ProcessHandle.of(pid);
            if (process.isPresent()) {
                process.get().onExit().get(HANG_BOUND_HOURS, TimeUnit.HOURS);
            }
        }
    }

    /**
     * Stops the provider and ends its process, if any.
     */
    private static void reap(SocketStreamConnectionProvider provider) throws Exception {
        provider.stop();
        var pid = provider.getPid();
        if (pid != null) {
            ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
        }
        awaitExit(provider);
    }
}

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
import com.redhat.devtools.lsp4ij.server.SocketTestServers.IPv6ConnectingServer;
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
import java.time.Duration;
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
            var provider = SocketStreamConnectionProvider.attachTo(HOST, server.getLocalPort());
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
        var provider = SocketStreamConnectionProvider.attachTo(HOST, port);

        try {
            provider.start();
            fail("nothing listens on port " + port);
        } catch (CannotStartProcessException e) {
            // it names the host and the port, and on the IPv4 loopback address, the IPv6 one
            assertTrue(e.getMessage(), e.getMessage().contains(HOST + ":" + port));
            assertEquals(e.getMessage(), HOST.startsWith("127."), e.getMessage().contains("::1"));
        }
        assertFalse(provider.isAlive());
    }

    public void testWithoutAHostItReachesALanguageServerWhichListensOnTheIPv6LoopbackAddressOnly() throws Exception {
        // As a language server which listens on localhost, which Node.js may bind to ::1 only
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("::1"))) {
            var provider = new SocketStreamConnectionProvider(SocketStreamConnectionProvider.Direction.CONNECT, null,
                    server.getLocalPort(), null);
            try {
                provider.start();
                try (var accepted = server.accept()) {
                    writeLine(provider.getOutputStream(), "ping");
                    assertEquals("ping", readLine(accepted.getInputStream()));
                }
            } finally {
                provider.stop();
            }
        }
    }

    public void testWithoutAHostTheErrorNamesBothLoopbackAddresses() throws Exception {
        int port;
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = server.getLocalPort();
        }
        var provider = new SocketStreamConnectionProvider(SocketStreamConnectionProvider.Direction.CONNECT, null, port,
                null);

        try {
            provider.start();
            fail("nothing listens on port " + port);
        } catch (CannotStartProcessException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(HOST + ":" + port + " or [::1]:" + port)
                    || e.getMessage().contains("[::1]:" + port + " or " + "127.0.0.1:" + port));
            // LSP4IJ tried ::1: no advice to set it
            assertFalse(e.getMessage(), e.getMessage().contains("Set the host to ::1"));
        }
    }

    public void testAnIPv4AddressAsTheHostIsNotTriedOverIPv6() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("::1"))) {
            var provider = SocketStreamConnectionProvider.attachTo("127.0.0.1", server.getLocalPort());

            try {
                provider.start();
                fail("LSP4IJ connects to the address it is given only");
            } catch (CannotStartProcessException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("Set the host to ::1"));
            }
        }
    }

    public void testALanguageServerThatClosesTheConnectionStopsUnexpectedlyOnce() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var provider = SocketStreamConnectionProvider.attachTo(HOST, server.getLocalPort());
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
            var provider = SocketStreamConnectionProvider.attachTo(HOST, server.getLocalPort());
            var stops = countUnexpectedStops(provider);
            provider.start();
            try (var ignored = server.accept()) {
                provider.stop();

                assertThrows(IOException.class, () -> provider.getInputStream().read());
                assertThrows(IOException.class, () -> provider.getInputStream().read(new byte[8]));
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
            // the output of the language server is its log, and its standard output is free, so information
            awaitLog(log, "info: listening on " + port);
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
            // its standard error is errors
            awaitLog(log, "error: connecting to " + port);
        } finally {
            reap(provider);
        }
    }

    public void testStartsALanguageServerThatConnectsToTheIPv6LoopbackAddressAndAcceptsItsConnection() throws Exception {
        var provider = SocketStreamConnectionProvider.startAndAccept(0, port -> java(IPv6ConnectingServer.class, port));
        try {
            provider.start();

            writeLine(provider.getOutputStream(), "ping");
            assertEquals("ping", readLine(provider.getInputStream()));
        } finally {
            reap(provider);
        }
    }

    public void testALanguageServerThatEndsOnceConnectedStopsUnexpectedly() throws Exception {
        var provider = SocketStreamConnectionProvider.startAndAccept(0, port -> java(ConnectingServer.class, port));
        var stops = countUnexpectedStops(provider);
        var log = log(provider);
        try {
            provider.start();
            writeLine(provider.getOutputStream(), "bye");

            // it ends, which closes the connection
            assertEquals(-1, provider.getInputStream().read(new byte[8]));
            assertEquals(1, stops.get());
            // its last line has no line feed
            awaitLog(log, "error: ended");
            awaitExit(provider);
            assertFalse(provider.isAlive());
            assertEquals("reported once", 1, stops.get());
        } finally {
            reap(provider);
        }
    }

    public void testALanguageServerThatEndsOnceConnectedStopsUnexpectedlyWhenNothingReadsTheConnection() throws Exception {
        var provider = SocketStreamConnectionProvider.startAndAccept(0, port -> java(ConnectingServer.class, port));
        // the handlers run in their order, so the count is up to date once the test is released
        var stops = countUnexpectedStops(provider);
        var stopped = new CompletableFuture<Void>();
        provider.addUnexpectedServerStopHandler(() -> stopped.complete(null));
        try {
            provider.start();
            writeLine(provider.getOutputStream(), "bye");

            // nothing reads the connection's end, so the end of the process reports the stop
            stopped.get(HANG_BOUND_HOURS, TimeUnit.HOURS);
            awaitExit(provider);
            assertEquals(1, stops.get());
        } finally {
            reap(provider);
        }
    }

    public void testCannotStartALanguageServerOnAPortAnotherProcessListensOn() throws Exception {
        try (var other = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var provider = SocketStreamConnectionProvider.startAndConnect(other.getLocalPort(),
                    port -> java(ListeningServer.class, port));
            try {
                assertThrows(CannotStartProcessException.class, provider::start);
                assertNull("the language server is not started", provider.getPid());
            } finally {
                reap(provider);
            }
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

    public void testCannotStartALanguageServerWhoseCommandCannotRun() {
        var provider = SocketStreamConnectionProvider.startAndAccept(0,
                port -> new GeneralCommandLine(Path.of("no", "such", "language-server").toString(), String.valueOf(port)));

        assertThrows(CannotStartProcessException.class, provider::start);
        assertNull(provider.getPid());
        assertFalse(provider.isAlive());
    }

    public void testCannotStartOnceStopped() {
        var provider = SocketStreamConnectionProvider.attachTo(HOST, 6005);
        provider.stop();

        assertThrows(CannotStartProcessException.class, provider::start);
        assertFalse(provider.isAlive());
        assertNull(provider.getInputStream());
    }

    public void testAnAttachWaitsLessForItsConnectionThanAStart() {
        var attach = SocketStreamConnectionProvider.attachTo(HOST, 6005);
        // a language server which runs already accepts the connection at once, or never
        assertEquals(SocketStreamConnectionProvider.ATTACH_TIMEOUT, attach.connectTimeout());
        attach.setStartTimeout(Duration.ofSeconds(5));
        assertEquals("a shorter start timeout bounds it", Duration.ofSeconds(5), attach.connectTimeout());

        var start = SocketStreamConnectionProvider.startAndConnect(0, port -> java(ListeningServer.class, port));
        assertEquals(SocketStreamConnectionProvider.DEFAULT_START_TIMEOUT, start.connectTimeout());
    }

    public void testEachWayHasItsDirection() {
        assertSame(SocketStreamConnectionProvider.Direction.CONNECT,
                SocketStreamConnectionProvider.attachTo(HOST, 6005).getDirection());
        assertSame(SocketStreamConnectionProvider.Direction.CONNECT,
                SocketStreamConnectionProvider.startAndConnect(0, port -> java(ListeningServer.class, port)).getDirection());
        assertSame(SocketStreamConnectionProvider.Direction.ACCEPT,
                SocketStreamConnectionProvider.startAndAccept(0, port -> java(ConnectingServer.class, port)).getDirection());
    }

    public void testAStartEndsWhenTheLanguageServerDoesNotListenInTime() throws Exception {
        assertStartTimesOut(SocketStreamConnectionProvider.startAndConnect(0, port -> java(WaitingServer.class, port)));
    }

    public void testAStartEndsWhenTheLanguageServerDoesNotConnectInTime() throws Exception {
        assertStartTimesOut(SocketStreamConnectionProvider.startAndAccept(0, port -> java(WaitingServer.class, port)));
    }

    public void testTheStartTimeoutIsPositive() {
        var provider = SocketStreamConnectionProvider.attachTo(HOST, 6005);
        assertEquals(SocketStreamConnectionProvider.DEFAULT_START_TIMEOUT, provider.getStartTimeout());
        assertThrows(IllegalArgumentException.class, () -> provider.setStartTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> provider.setStartTimeout(Duration.ofSeconds(-1)));
    }

    public void testStartsOnce() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var provider = SocketStreamConnectionProvider.attachTo(HOST, server.getLocalPort());
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
        assertThrows(IllegalArgumentException.class, () -> SocketStreamConnectionProvider.attachTo(HOST, 0));
        assertThrows(IllegalArgumentException.class, () -> SocketStreamConnectionProvider.attachTo(HOST, 65536));
        assertThrows(IllegalArgumentException.class, () -> SocketStreamConnectionProvider.attachTo(HOST, -1));
    }

    /**
     * Starts the provider with a start timeout which any machine passes, since the language server never listens nor
     * connects: the start fails, and stops the language server.
     */
    private static void assertStartTimesOut(SocketStreamConnectionProvider provider) throws Exception {
        provider.setStartTimeout(Duration.ofMillis(1));
        var stops = countUnexpectedStops(provider);
        try {
            assertThrows(CannotStartProcessException.class, provider::start);
            awaitExit(provider);
            assertFalse(provider.isAlive());
            assertEquals("start() reports it", 0, stops.get());
        } finally {
            reap(provider);
        }
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
            awaitLog(log, "error: waiting");

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

    /**
     * Returns the log of the given provider, each line led by its level, "info: " or "error: ".
     */
    private static LinkedBlockingQueue<String> log(StreamConnectionProvider provider) {
        var log = new LinkedBlockingQueue<String>();
        provider.addLogErrorHandler(new LanguageServerLogErrorHandler() {
            @Override
            public void logError(String error) {
                log.add("error: " + error);
            }

            @Override
            public void logInfo(String message) {
                log.add("info: " + message);
            }
        });
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

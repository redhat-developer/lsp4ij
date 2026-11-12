/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.util.Key;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stream connection provider which talks to a language server over a TCP socket, instead of the standard input and
 * output of its process. It covers the three ways a language server takes a socket connection:
 * <ul>
 *     <li>{@link #attachTo(String, int)}: the language server runs already and listens on a host and port, as when it
 *     runs in a debugger or inside another application, and LSP4IJ connects to it.</li>
 *     <li>{@link #startAndConnect(int, IntFunction)}: LSP4IJ starts the language server, which listens on the port,
 *     and connects to it once it accepts connections.</li>
 *     <li>{@link #startAndAccept(int, IntFunction)}: LSP4IJ listens on the port and starts the language server, which
 *     connects to it, as VS Code's socket transport does for a language server started with {@code --socket=<port>}.
 *     </li>
 * </ul>
 * A port of 0 lets LSP4IJ choose a free port when it starts. The command line of a language server which LSP4IJ starts
 * is made for the port, which {@link #getPort()} returns. LSP4IJ listens on the loopback address only.
 * <p>
 * The output of a language server which LSP4IJ starts is logged in the Log tab of the LSP console. A language server
 * which ends, or closes the connection, once connected, is reported as an unexpected stop, as a language server process
 * is. {@link #stop()} closes the connection, and stops the language server only if this provider started it. Each
 * provider makes at most one connection: create a new provider to connect again.
 * <p>
 * As with any TCP transport of LSP, which has no handshake to authenticate its peer, any local process can connect to
 * a loopback port: on a machine shared with other users, one of their processes could connect to the port LSP4IJ
 * listens on before the language server does, or listen on the port LSP4IJ connects to. Prefer the standard input and
 * output of the language server's process there.
 * <p>
 * This API is experimental: it may change in a future release.
 */
@ApiStatus.Experimental
public class SocketStreamConnectionProvider implements StreamConnectionProvider, ProcessDataProvider {

    /**
     * Which side of the connection listens.
     */
    public enum Direction {
        /**
         * The language server listens, and LSP4IJ connects to it.
         */
        CONNECT,
        /**
         * LSP4IJ listens, and the language server connects to it.
         */
        ACCEPT
    }

    // While a language server which LSP4IJ started doesn't listen yet, the pause between two connection attempts
    private static final long FIRST_RETRY_PAUSE_MILLIS = 10;
    private static final long MAX_RETRY_PAUSE_MILLIS = 500;

    // Without a host, LSP4IJ connects to both loopback addresses, that of the JVM first: a language server which listens
    // on localhost may get either, as Node.js does, in the order in which the system resolves localhost
    private static final List<InetAddress> LOOPBACK_ADDRESSES = loopbackAddresses();

    /**
     * How long a start waits for the connection by default.
     */
    public static final @NotNull Duration DEFAULT_START_TIMEOUT = Duration.ofSeconds(60);

    /**
     * How long an attach waits for the connection at most: a language server which runs already accepts it at once,
     * or not at all, as on a host which drops packets, which shouldn't look like a minute-long hang.
     */
    public static final @NotNull Duration ATTACH_TIMEOUT = Duration.ofSeconds(10);

    private final @NotNull Direction direction;
    // The host LSP4IJ connects to, or null for the loopback addresses
    private final @Nullable String host;
    private final int port;
    private final @Nullable IntFunction<GeneralCommandLine> commandLine;
    private final List<LanguageServerLogErrorHandler> logErrorHandlers = new CopyOnWriteArrayList<>();
    private final List<Runnable> unexpectedServerStopHandlers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean unexpectedStopReported = new AtomicBoolean();

    // Changed under the monitor, so that stop() closes whatever start() has made
    private volatile boolean started;
    private volatile boolean stopped;
    private volatile @Nullable OSProcessHandler processHandler;
    private volatile @Nullable GeneralCommandLine startedCommandLine;
    private volatile @Nullable ServerSocket serverSocket;
    private volatile @Nullable Socket connecting;
    private volatile @Nullable Socket socket;
    private volatile @Nullable InputStream inputStream;
    private volatile @Nullable OutputStream outputStream;
    private volatile boolean connectionEnded;
    private volatile int boundPort;
    private volatile @NotNull Duration startTimeout = DEFAULT_START_TIMEOUT;

    /**
     * Returns a provider which connects to a language server that runs already and listens on the given host and
     * port.
     *
     * @param host the host of the language server.
     * @param port the port the language server listens on.
     * @return a provider which connects to a running language server.
     */
    public static @NotNull SocketStreamConnectionProvider attachTo(@NotNull String host, int port) {
        return new SocketStreamConnectionProvider(Direction.CONNECT, host, port, null);
    }

    /**
     * Returns a provider which starts the language server, which listens on the given port, and connects to it once
     * it accepts connections, on the IPv4 or the IPv6 loopback address.
     *
     * @param port        the port the language server listens on, or 0 to choose a free port.
     * @param commandLine the command line which starts the language server, for the port it listens on.
     * @return a provider which starts a language server and connects to it.
     */
    public static @NotNull SocketStreamConnectionProvider startAndConnect(int port,
                                                                          @NotNull IntFunction<GeneralCommandLine> commandLine) {
        return new SocketStreamConnectionProvider(Direction.CONNECT, null, port, commandLine);
    }

    /**
     * Returns a provider which listens on the given port and starts the language server, which connects to it.
     *
     * @param port        the port LSP4IJ listens on, or 0 to choose a free port.
     * @param commandLine the command line which starts the language server, for the port it connects to.
     * @return a provider which starts a language server and accepts its connection.
     */
    public static @NotNull SocketStreamConnectionProvider startAndAccept(int port,
                                                                         @NotNull IntFunction<GeneralCommandLine> commandLine) {
        return new SocketStreamConnectionProvider(Direction.ACCEPT, null, port, commandLine);
    }

    /**
     * Creates a provider.
     *
     * @param direction   which side of the connection listens.
     * @param host        the host LSP4IJ connects to, each of its addresses in turn, or null for the loopback addresses,
     *                    that of the JVM and then the other one, {@code 127.0.0.1} and {@code ::1}. LSP4IJ listens on the
     *                    loopback address of the JVM.
     * @param port        the port, or 0 to choose a free port when the provider starts a language server or listens.
     * @param commandLine the command line which starts the language server, for the port, or null if it is not
     *                    started by LSP4IJ.
     */
    protected SocketStreamConnectionProvider(@NotNull Direction direction,
                                             @Nullable String host,
                                             int port,
                                             @Nullable IntFunction<GeneralCommandLine> commandLine) {
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("Port out of range: " + port);
        }
        if (port == 0 && direction == Direction.CONNECT && commandLine == null) {
            throw new IllegalArgumentException("The port of a running language server is needed to connect to it");
        }
        this.direction = direction;
        this.host = host;
        this.port = port;
        this.commandLine = commandLine;
        this.boundPort = port;
    }

    /**
     * Returns which side of the connection listens.
     *
     * @return which side of the connection listens.
     */
    public @NotNull Direction getDirection() {
        return direction;
    }

    /**
     * Returns the port of the connection: the given one, or the free port chosen when the provider started.
     *
     * @return the port of the connection, or 0 before the provider chose a free port.
     */
    public int getPort() {
        return boundPort;
    }

    /**
     * Returns how long a start waits for the connection: for a language server which runs already to accept it, for a
     * language server which LSP4IJ starts to listen or to connect. Past it, the start fails.
     *
     * @return how long a start waits for the connection.
     */
    public @NotNull Duration getStartTimeout() {
        return startTimeout;
    }

    /**
     * Sets how long a start waits for the connection, {@link #DEFAULT_START_TIMEOUT} by default, as for a language
     * server which starts slowly. Past it, the start fails, and stops the language server if LSP4IJ started it.
     *
     * @param startTimeout how long a start waits for the connection.
     */
    public void setStartTimeout(@NotNull Duration startTimeout) {
        if (startTimeout.isNegative() || startTimeout.isZero()) {
            throw new IllegalArgumentException("The start timeout must be positive: " + startTimeout);
        }
        this.startTimeout = startTimeout;
    }

    @Override
    public void addLogErrorHandler(LanguageServerLogErrorHandler handler) {
        logErrorHandlers.add(handler);
    }

    @Override
    public void addUnexpectedServerStopHandler(Runnable handler) {
        unexpectedServerStopHandlers.add(handler);
    }

    /**
     * Starts the language server if this provider starts it, and makes the connection. It returns once connected,
     * and {@link #stop()} ends it while it waits.
     *
     * @throws CannotStartProcessException if this provider was started or stopped already, or the connection could
     *                                     not be made.
     */
    @Override
    public void start() throws CannotStartProcessException {
        synchronized (this) {
            if (started || stopped) {
                throw new CannotStartProcessException("This connection provider has already been started or stopped");
            }
            started = true;
        }
        try {
            if (direction == Direction.CONNECT) {
                connect();
            } else {
                accept();
            }
        } catch (CannotStartProcessException e) {
            stop();
            throw e;
        } catch (IOException | ExecutionException e) {
            stop();
            throw new CannotStartProcessException(e);
        }
    }

    private void connect() throws IOException, ExecutionException, CannotStartProcessException {
        int port = this.port != 0 ? this.port : NetUtils.findAvailableSocketPort();
        boundPort = port;
        if (commandLine == null) {
            // The language server runs already: it listens, or it doesn't
            try {
                install(connectOnce(port, connectTimeout()));
            } catch (ConnectException refused) {
                throw new CannotStartProcessException("Nothing listens on " + address(port) + " (" + refused.getMessage()
                        + "): " + this + ipv6Hint(" Set the host to ::1 for one which listens on ::1."));
            } catch (SocketTimeoutException noAnswer) {
                throw new CannotStartProcessException("No answer from " + address(port) + " within "
                        + connectTimeout().toSeconds() + " s: " + this
                        + ipv6Hint(" Set the host to ::1 for one which listens on ::1."));
            }
            return;
        }
        if (this.port != 0) {
            // A process which listens on the port already would take the connection meant for the language server
            checkNothingListens(port);
        }
        var handler = startProcess(commandLine.apply(port));
        // The language server listens once it has started: try until it accepts the connection, ends, or the start
        // times out, as when its command doesn't take the port
        long deadline = System.nanoTime() + startTimeout.toNanos();
        long pause = FIRST_RETRY_PAUSE_MILLIS;
        while (true) {
            try {
                install(connectOnce(port, connectTimeout()));
                return;
            } catch (ConnectException notListeningYet) {
                long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remaining <= 0) {
                    throw new CannotStartProcessException("The language server didn't accept a connection on "
                            + address(port) + " within " + startTimeout.toSeconds() + " s: " + this
                            + ipv6Hint(" Let it listen on " + host + " instead."));
                }
                boolean ended = handler.waitFor(Math.min(pause, remaining));
                checkNotStopped();
                if (ended) {
                    throw new CannotStartProcessException("The language server ended before it accepted a connection: " + this);
                }
                pause = Math.min(pause * 2, MAX_RETRY_PAUSE_MILLIS);
            }
        }
    }

    /**
     * Returns the host and the given port, as {@code host:port}, or {@code [host]:port} for an IPv6 address; without a
     * host, both loopback addresses with the port.
     */
    private @NotNull String address(int port) {
        if (host != null) {
            return withPort(host, port);
        }
        return LOOPBACK_ADDRESSES.stream()
                .map(address -> withPort(address instanceof Inet6Address ? "::1" : address.getHostAddress(), port))
                .collect(Collectors.joining(" or "));
    }

    private static @NotNull String withPort(@NotNull String host, int port) {
        return (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + port;
    }

    /**
     * Returns, when LSP4IJ connects to the IPv4 loopback address, a hint that the language server may listen on the IPv6
     * loopback address, ::1, only, as Node.js may do for {@code localhost}, followed by the given advice; and an empty
     * string otherwise.
     */
    private @NotNull String ipv6Hint(@NotNull String advice) {
        boolean ipv4Loopback = host != null && (host.equalsIgnoreCase("localhost") || host.startsWith("127."));
        return ipv4Loopback && !connectsToTheIPv6Loopback()
                ? " A language server which listens on localhost may listen on ::1 only, the IPv6 loopback address, to"
                + " which LSP4IJ doesn't connect." + advice
                : "";
    }

    /**
     * Returns true if LSP4IJ connects to {@code ::1} too, as without a host, or with {@code localhost} where the system
     * resolves it to both loopback addresses.
     */
    private boolean connectsToTheIPv6Loopback() {
        try {
            return addresses().stream().anyMatch(address -> address instanceof Inet6Address && address.isLoopbackAddress());
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private void checkNothingListens(int port) throws IOException, CannotStartProcessException {
        for (InetAddress address : addresses()) {
            try (var probe = new Socket()) {
                probe.connect(new InetSocketAddress(address, port));
            } catch (IOException nothingListens) {
                continue;
            }
            throw new CannotStartProcessException("A process listens on port " + port + " already: " + this);
        }
    }

    /**
     * Returns the addresses to connect to, in turn: those of the host, in the order in which the system resolves it, or
     * without a host, both loopback addresses.
     */
    private @NotNull List<InetAddress> addresses() throws UnknownHostException {
        return host != null ? List.of(InetAddress.getAllByName(host)) : LOOPBACK_ADDRESSES;
    }

    private static @NotNull List<InetAddress> loopbackAddresses() {
        InetAddress jvm = InetAddress.getLoopbackAddress();
        try {
            // An IP address is never looked up
            return List.of(jvm, InetAddress.getByName(jvm instanceof Inet6Address ? "127.0.0.1" : "::1"));
        } catch (UnknownHostException e) {
            return List.of(jvm);
        }
    }

    /**
     * Returns how long one connection attempt waits: for a language server which runs already, the shorter of
     * {@link #ATTACH_TIMEOUT} and the start timeout; for one which this provider starts, the start timeout.
     */
    @NotNull
    Duration connectTimeout() {
        return commandLine == null && ATTACH_TIMEOUT.compareTo(startTimeout) < 0 ? ATTACH_TIMEOUT : startTimeout;
    }

    /**
     * Connects once to each address in turn, until one takes the connection. When none takes it, it fails as the first
     * address did, as with "Connection refused", and the failures of the others are suppressed in it.
     */
    private @NotNull Socket connectOnce(int port, @NotNull Duration timeout) throws IOException, CannotStartProcessException {
        IOException failure = null;
        for (InetAddress address : addresses()) {
            try {
                return connectOnce(new InetSocketAddress(address, port), timeout);
            } catch (IOException e) {
                // A stop closes the attempt: the start ends, without trying the next address
                checkNotStopped();
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        throw Objects.requireNonNull(failure, "no address to connect to");
    }

    private @NotNull Socket connectOnce(@NotNull InetSocketAddress address, @NotNull Duration timeout)
            throws IOException, CannotStartProcessException {
        var attempt = new Socket();
        synchronized (this) {
            checkNotStopped();
            connecting = attempt;
        }
        try {
            // stop() closes the attempt
            attempt.connect(address, Math.toIntExact(timeout.toMillis()));
            return attempt;
        } catch (IOException e) {
            closeQuietly(attempt);
            throw e;
        } finally {
            synchronized (this) {
                connecting = null;
            }
        }
    }

    private void accept() throws IOException, ExecutionException, CannotStartProcessException {
        var server = new ServerSocket(port, 1, InetAddress.getLoopbackAddress());
        synchronized (this) {
            if (stopped) {
                server.close();
                throw stoppedException();
            }
            serverSocket = server;
        }
        boundPort = server.getLocalPort();
        OSProcessHandler handler = commandLine != null ? startProcess(commandLine.apply(boundPort)) : null;
        Socket accepted;
        try {
            // stop(), and the end of the language server, close the server socket
            server.setSoTimeout(timeoutMillis());
            accepted = server.accept();
        } catch (SocketTimeoutException e) {
            throw new CannotStartProcessException("The language server didn't connect to "
                    + server.getInetAddress().getHostAddress() + ":" + boundPort + " within " + startTimeout.toSeconds()
                    + " s: " + this + (server.getInetAddress() instanceof Inet4Address
                    ? " A language server which connects to localhost may try ::1, the IPv6 loopback address, on which"
                    + " LSP4IJ doesn't listen. Let it connect to " + server.getInetAddress().getHostAddress() + " instead."
                    : ""));
        } catch (SocketException e) {
            checkNotStopped();
            if (handler != null && handler.isProcessTerminated()) {
                throw new CannotStartProcessException("The language server ended before it connected: " + this);
            }
            throw e;
        } finally {
            // One connection only
            server.close();
        }
        install(accepted);
    }

    private @NotNull OSProcessHandler startProcess(@NotNull GeneralCommandLine commandLine)
            throws ExecutionException, CannotStartProcessException {
        synchronized (this) {
            checkNotStopped();
            var handler = new OSProcessHandler(commandLine) {
                @Override
                protected BaseOutputReader.@NotNull Options readerOptions() {
                    return BaseOutputReader.Options.forMostlySilentProcess();
                }
            };
            handler.addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                    // The connection carries the protocol, so the output of the language server is its log: its
                    // standard output information, as it is free over a socket, and its standard error errors
                    if (ProcessOutputType.isStdout(outputType)) {
                        log(event.getText(), false);
                    } else if (ProcessOutputType.isStderr(outputType)) {
                        log(event.getText(), true);
                    }
                }

                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
                    languageServerEnded();
                }
            });
            startedCommandLine = commandLine;
            processHandler = handler;
            handler.startNotify();
            return handler;
        }
    }

    private void install(@NotNull Socket connected) throws IOException, CannotStartProcessException {
        synchronized (this) {
            if (stopped) {
                connected.close();
                throw stoppedException();
            }
            inputStream = new ConnectionInputStream(connected.getInputStream());
            outputStream = connected.getOutputStream();
            socket = connected;
        }
    }

    private void log(@NotNull String text, boolean error) {
        String line = text.endsWith("\r\n") ? text.substring(0, text.length() - 2)
                : text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        if (!line.isEmpty()) {
            logErrorHandlers.forEach(handler -> {
                if (error) {
                    handler.logError(line);
                } else {
                    handler.logInfo(line);
                }
            });
        }
    }

    private void languageServerEnded() {
        ServerSocket server;
        synchronized (this) {
            server = socket == null ? serverSocket : null;
        }
        if (server != null) {
            // Not connected yet: stop waiting for the connection, and let start() fail
            closeQuietly(server);
        }
        reportUnexpectedStop();
    }

    /**
     * Reports an unexpected stop once connected, unless the provider was stopped.
     */
    private void reportUnexpectedStop() {
        if (!stopped && socket != null && unexpectedStopReported.compareAndSet(false, true)) {
            unexpectedServerStopHandlers.forEach(Runnable::run);
        }
    }

    private int timeoutMillis() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, startTimeout.toMillis()));
    }

    private void checkNotStopped() throws CannotStartProcessException {
        if (stopped) {
            throw stoppedException();
        }
    }

    private @NotNull CannotStartProcessException stoppedException() {
        return new CannotStartProcessException("The connection provider was stopped while it started: " + this);
    }

    @Override
    public boolean isAlive() {
        var connected = socket;
        var handler = processHandler;
        return connected != null && !connected.isClosed() && !connectionEnded
                && (handler == null || !handler.isProcessTerminated());
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    /**
     * Permanently stops this provider: it closes the connection, or ends a connection which is being made, and stops
     * the language server if this provider started it. Repeated calls have no effect.
     */
    @Override
    public void stop() {
        OSProcessHandler handler;
        List<Closeable> closeables;
        synchronized (this) {
            if (stopped) {
                return;
            }
            stopped = true;
            handler = processHandler;
            closeables = Stream.of(socket, connecting, serverSocket)
                    .filter(Objects::nonNull)
                    .map(Closeable.class::cast)
                    .toList();
        }
        closeables.forEach(SocketStreamConnectionProvider::closeQuietly);
        if (handler != null && !handler.isProcessTerminated()) {
            ExecutionManagerImpl.stopProcess(handler);
        }
    }

    @Override
    public @Nullable Long getPid() {
        var handler = processHandler;
        var process = handler != null ? handler.getProcess() : null;
        return process != null ? process.pid() : null;
    }

    @Override
    public List<String> getCommands() {
        var commandLine = startedCommandLine;
        if (commandLine == null) {
            return List.of();
        }
        List<String> commands = new ArrayList<>();
        commands.add(commandLine.getExePath());
        commands.addAll(commandLine.getParametersList().getParameters());
        return commands;
    }

    @Override
    public String toString() {
        String connection = direction == Direction.CONNECT ? "connect to " + address(boundPort)
                : "accept on port " + boundPort;
        var commandLine = startedCommandLine;
        return "SocketStreamConnectionProvider [" + connection
                + (commandLine != null ? ", commandLine=" + commandLine.getCommandLineString() : "") + "]";
    }

    private static void closeQuietly(@NotNull Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // Closing on stop, or once the connection is no longer waited for
        }
    }

    /**
     * The input of the connection, which reports the end of the connection, unless the provider was stopped, as an
     * unexpected stop of the language server.
     */
    private final class ConnectionInputStream extends FilterInputStream {

        ConnectionInputStream(@NotNull InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            try {
                return ended(super.read());
            } catch (IOException e) {
                connectionClosed();
                throw e;
            }
        }

        @Override
        public int read(byte @NotNull [] b, int off, int len) throws IOException {
            try {
                return ended(super.read(b, off, len));
            } catch (IOException e) {
                connectionClosed();
                throw e;
            }
        }

        private int ended(int read) {
            if (read == -1) {
                connectionClosed();
            }
            return read;
        }

        private void connectionClosed() {
            connectionEnded = true;
            reportUnexpectedStop();
        }
    }
}

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A language server or debug adapter for the tests of {@link InitializeTask}, which runs as its own process and needs
 * nothing but the JDK. It appends its pid, then the method or command of each message it gets, to a file, and answers
 * as told:
 *
 * <pre>
 * InitializeTestServer lsp|dap answer|error|silent|exit|stay|echo record-file [listen port [print] | connect port]
 * </pre>
 * <ul>
 *     <li>{@code answer}: answers {@code initialize}, {@code shutdown} and {@code disconnect}, and ends on {@code exit}
 *     and after {@code disconnect}.</li>
 *     <li>{@code error}: answers {@code initialize} with an error, and else as {@code answer}.</li>
 *     <li>{@code silent}: answers nothing.</li>
 *     <li>{@code exit}: ends at once, with the exit code 1.</li>
 *     <li>{@code stay}: answers {@code initialize}, and nothing else, and doesn't end.</li>
 *     <li>{@code echo}: sends back what it gets, as {@code cat} does.</li>
 * </ul>
 * With {@code listen}, it listens on the port, 0 for a free one, on the loopback address, instead of using its standard
 * input and output, and with {@code print}, it prints {@code Test debug adapter listening at: 127.0.0.1:<port>}. With
 * {@code connect}, it connects to the port on the loopback address.
 */
public final class InitializeTestServer {

    private static final Pattern LSP_METHOD = Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern LSP_ID = Pattern.compile("\"id\"\\s*:\\s*(\"[^\"]*\"|\\d+)");
    private static final Pattern DAP_COMMAND = Pattern.compile("\"command\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DAP_SEQ = Pattern.compile("\"seq\"\\s*:\\s*(\\d+)");

    private InitializeTestServer() {
    }

    /**
     * Returns the command line which starts this server, as a language server or debug adapter command, with the given
     * protocol, behavior, record file and arguments: each in double quotes, but the macros, as {@code ${port}}.
     */
    public static String commandLine(String protocol, String behavior, Path record, String... args) {
        String java = ProcessHandle.current().info().command().orElseThrow();
        return Stream.concat(Stream.of(java, "-cp", testClasses().toString(), InitializeTestServer.class.getName(),
                        protocol, behavior, record.toString()), Stream.of(args))
                .map(arg -> arg.startsWith("${") ? arg : "\"" + arg + "\"")
                .collect(Collectors.joining(" "));
    }

    /**
     * Returns the directory of the compiled test classes, above the folders of the package of this class.
     */
    private static Path testClasses() {
        try {
            Path classes = Path.of(InitializeTestServer.class.getResource("InitializeTestServer.class").toURI());
            for (int i = InitializeTestServer.class.getName().split("\\.").length; i > 0; i--) {
                classes = classes.getParent();
            }
            return classes;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        boolean lsp = args[0].equals("lsp");
        String behavior = args[1];
        Path record = Path.of(args[2]);
        record(record, "pid " + ProcessHandle.current().pid());
        if (behavior.equals("exit")) {
            System.exit(1);
        }
        InputStream in = System.in;
        OutputStream out = System.out;
        if (args.length > 3 && args[3].equals("listen")) {
            var serverSocket = new ServerSocket(Integer.parseInt(args[4]), 1, InetAddress.getLoopbackAddress());
            if (args.length > 5 && args[5].equals("print")) {
                System.out.println("Test debug adapter listening at: 127.0.0.1:" + serverSocket.getLocalPort());
                System.out.flush();
            }
            Socket socket = serverSocket.accept();
            in = socket.getInputStream();
            out = socket.getOutputStream();
        } else if (args.length > 3 && args[3].equals("connect")) {
            var socket = new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(args[4]));
            in = socket.getInputStream();
            out = socket.getOutputStream();
        }
        if (behavior.equals("echo")) {
            byte[] buffer = new byte[8192];
            for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
                out.write(buffer, 0, read);
                out.flush();
            }
            return;
        }
        int seq = 0;
        for (String message = read(in); message != null; message = read(in)) {
            String name = find(lsp ? LSP_METHOD : DAP_COMMAND, message);
            record(record, name);
            if (behavior.equals("silent") || (behavior.equals("stay") && !"initialize".equals(name))) {
                continue;
            }
            if (lsp) {
                String id = find(LSP_ID, message);
                switch (name) {
                    case "initialize" -> write(out, behavior.equals("error")
                            ? "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":-32603,\"message\":\"test error\"}}"
                            : "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"capabilities\":{},"
                            + "\"serverInfo\":{\"name\":\"test-server\",\"version\":\"1.0\"}}}");
                    case "shutdown" -> write(out, "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":null}");
                    case "exit" -> System.exit(0);
                    default -> {
                        // Ignored
                    }
                }
            } else {
                String requestSeq = find(DAP_SEQ, message);
                boolean success = !(name.equals("initialize") && behavior.equals("error"));
                write(out, "{\"seq\":" + (++seq) + ",\"type\":\"response\",\"request_seq\":" + requestSeq
                        + ",\"success\":" + success + ",\"command\":\"" + name + "\""
                        + (success ? ",\"body\":{}" : ",\"message\":\"test error\"") + "}");
                if (name.equals("disconnect")) {
                    System.exit(0);
                }
            }
        }
    }

    private static String find(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static void record(Path record, String line) throws IOException {
        Files.writeString(record, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Reads a message with its {@code Content-Length} header, or returns null at the end of the input.
     */
    private static String read(InputStream in) throws IOException {
        var header = new ByteArrayOutputStream();
        while (!header.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
            int c = in.read();
            if (c == -1) {
                return null;
            }
            header.write(c);
        }
        Matcher length = Pattern.compile("Content-Length:\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
                .matcher(header.toString(StandardCharsets.US_ASCII));
        if (!length.find()) {
            return "";
        }
        byte[] content = in.readNBytes(Integer.parseInt(length.group(1)));
        return new String(content, StandardCharsets.UTF_8);
    }

    private static void write(OutputStream out, String message) throws IOException {
        byte[] content = message.getBytes(StandardCharsets.UTF_8);
        out.write(("Content-Length: " + content.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(content);
        out.flush();
    }
}

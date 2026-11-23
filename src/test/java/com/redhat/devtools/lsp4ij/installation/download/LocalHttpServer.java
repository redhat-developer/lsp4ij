/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.download;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A local HTTP server, on 127.0.0.1, which stands for the GitHub API and for the downloads of the tests: it answers a
 * path as the test says, or with 404, and records the requests that it gets.
 */
final class LocalHttpServer implements AutoCloseable {

    /**
     * A request that the server got.
     *
     * @param path          the path of the request, without its query.
     * @param authorization the {@code Authorization} header of the request, or null if it has none.
     */
    record Request(@NotNull String path, @Nullable String authorization) {
    }

    /**
     * A response of the server.
     *
     * @param code    the HTTP status code.
     * @param headers the headers of the response.
     * @param body    the body of the response.
     */
    record Response(int code, @NotNull Map<String, String> headers, @NotNull String body) {

        static @NotNull Response ok(@NotNull String body) {
            return new Response(200, Map.of(), body);
        }

        static @NotNull Response redirect(int code, @NotNull String location) {
            return new Response(code, Map.of("Location", location), "");
        }
    }

    private final @NotNull HttpServer server;
    private final @NotNull Map<String, Response> responses = new ConcurrentHashMap<>();
    private final @NotNull List<Request> requests = new CopyOnWriteArrayList<>();

    LocalHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    /**
     * Returns the URL of the server, without a trailing slash.
     */
    @NotNull String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * Answers the requests to the given path, without its query, with the given response.
     */
    void answer(@NotNull String path, @NotNull Response response) {
        responses.put(path, response);
    }

    /**
     * Returns the requests that the server got, in order.
     */
    @NotNull List<Request> requests() {
        return List.copyOf(requests);
    }

    private void handle(@NotNull HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            requests.add(new Request(path, exchange.getRequestHeaders().getFirst("Authorization")));
            Response response = responses.getOrDefault(path, new Response(404, Map.of(), ""));
            response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.code(), body.length > 0 ? body.length : -1);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

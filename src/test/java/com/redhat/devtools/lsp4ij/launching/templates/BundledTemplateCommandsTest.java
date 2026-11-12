/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.templates;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that no command of a bundled template or installer goes through {@code sh} in a way that loses its arguments.
 * <p>
 * LSP4IJ splits a command at its spaces, so {@code sh -c typescript-language-server --stdio} gives {@code sh} three
 * arguments, and {@code sh -c} runs only the first one: the language server starts without {@code --stdio}. And
 * {@code sh -s ruby-lsp} reads its commands from the standard input, which carries the LSP messages, so it never starts
 * {@code ruby-lsp}.
 */
public class BundledTemplateCommandsTest {

    @Test
    void shRunsTheWholeCommandWhereABundledTemplateUsesIt() throws IOException, URISyntaxException {
        List<String> files = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (String kind : List.of("templates/lsp", "templates/dap")) {
            var url = BundledTemplateCommandsTest.class.getClassLoader().getResource(kind);
            assertNotNull(url, kind);
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                // As in the sandbox of the tests, which has the plugin's jar
                try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of())) {
                    check(jar.provider().getPath(uri), files, problems);
                }
            } else {
                check(Path.of(uri), files, problems);
            }
        }
        assertTrue(files.size() > 40, "the templates and installers of the language servers and debug adapters: " + files);
        assertEquals(List.of(), problems,
                "sh runs only the argument right after -c: start the command without a shell, or give sh -c the whole command in double quotes");
    }

    private static void check(@NotNull Path templates, @NotNull List<String> files, @NotNull List<String> problems)
            throws IOException {
        try (Stream<Path> paths = Files.walk(templates)) {
            for (Path path : paths.filter(p -> p.getFileName() != null
                    && p.getFileName().toString().matches("template\\.json|installer\\.json")).toList()) {
                files.add(path.toString());
                var json = JsonParser.parseString(Files.readString(path));
                checkStrings(json, path.getParent().getFileName() + "/" + path.getFileName(), problems);
            }
        }
    }

    private static void checkStrings(@NotNull JsonElement json, @NotNull String file, @NotNull List<String> problems) {
        if (json.isJsonObject()) {
            json.getAsJsonObject().entrySet().forEach(entry -> checkStrings(entry.getValue(), file, problems));
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> checkStrings(element, file, problems));
        } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            String command = json.getAsString();
            List<String> arguments = CommandUtils.createCommands(command);
            if (!arguments.isEmpty() && List.of("sh", "/bin/sh").contains(arguments.get(0))
                    && !(arguments.size() == 3 && arguments.get(1).equals("-c"))) {
                problems.add(file + ": " + command);
            }
        }
    }
}

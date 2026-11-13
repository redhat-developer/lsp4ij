/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.templates;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the commands of the bundled templates and installers keep their arguments.
 * <p>
 * LSP4IJ splits a command at its spaces, so {@code sh -c typescript-language-server --stdio} gives {@code sh} three
 * arguments, and {@code sh -c} runs only the first one: the language server starts without {@code --stdio}. And
 * {@code sh -s ruby-lsp} reads its commands from the standard input, which carries the LSP messages, so it never starts
 * {@code ruby-lsp}.
 * <p>
 * LSP4IJ also expands the macros of the command of a language server or debug adapter, as {@code $USER_HOME$}, before
 * it splits the command, so a path under a home directory with a space, as {@code C:\Users\Jane Doe} on Windows, must
 * be in quotes.
 */
public class BundledTemplateCommandsTest {

    private static final String HOME = "C:/Users/Jane Doe";
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([\\w.]+)}");

    @Test
    void shRunsTheWholeCommandWhereABundledTemplateUsesIt() throws IOException, URISyntaxException {
        var files = bundledFiles();
        List<String> problems = new ArrayList<>();
        files.forEach((file, json) -> checkStrings(json, file, problems));
        assertTrue(files.size() > 40, "the templates and installers of the language servers and debug adapters: "
                + files.keySet());
        assertEquals(List.of(), problems,
                "sh runs only the argument right after -c: start the command without a shell, or give sh -c the whole command in double quotes");
    }

    @Test
    void aHomeDirectoryWithASpaceStaysOneArgumentInTheCommandsOfTheTemplatesAndTheInstallers()
            throws IOException, URISyntaxException {
        List<String> commands = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        bundledFiles().forEach((file, json) -> {
            List<String> fileCommands = new ArrayList<>();
            Map<String, String> properties = new HashMap<>();
            if (file.endsWith("/template.json")) {
                collectByOs(json.get("programArgs"), fileCommands);
            } else {
                // The command which the installer gives the language server or debug adapter once it has installed
                // it, with the properties of the installer, and the directory where it put it
                var declared = json.getAsJsonObject("properties");
                if (declared != null) {
                    declared.entrySet().forEach(property -> properties.put(property.getKey(),
                            property.getValue().getAsString()));
                }
                collectInstallation(json, properties, fileCommands);
                properties.put("output.file.name", "server");
            }
            for (String command : fileCommands) {
                String expanded = resolve(command, properties)
                        .replace("$USER_HOME$", HOME)
                        .replace("$PROJECT_DIR$", HOME + "/project");
                if (!expanded.contains(HOME)) {
                    continue;
                }
                commands.add(file + ": " + command);
                List<String> arguments = CommandUtils.createCommands(expanded);
                if (arguments.stream().anyMatch(argument -> argument.endsWith("/Jane") || argument.startsWith("Doe/"))) {
                    problems.add(file + ": " + command + " -> " + arguments);
                }
            }
        });
        assertTrue(commands.size() > 30, "the commands with a path under the home directory: " + commands);
        assertEquals(List.of(), problems, "put the paths of the commands in double quotes");
    }

    private static @NotNull String resolve(@NotNull String text, @NotNull Map<String, String> properties) {
        String resolved = text;
        for (int i = 0; i < 5; i++) {
            Matcher matcher = PROPERTY.matcher(resolved);
            resolved = matcher.replaceAll(match -> Matcher.quoteReplacement(
                    properties.getOrDefault(match.group(1), match.group())));
        }
        return resolved;
    }

    private static void collectInstallation(@NotNull JsonElement json, @NotNull Map<String, String> properties,
                                            @NotNull List<String> commands) {
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(entry -> {
            if (entry.getKey().equals("configureServer")) {
                collectByOs(entry.getValue().getAsJsonObject().get("command"), commands);
            } else if (entry.getKey().equals("output") && entry.getValue().isJsonObject()
                    && entry.getValue().getAsJsonObject().has("dir")) {
                // A download puts the language server there
                properties.put("output.dir", resolve(entry.getValue().getAsJsonObject().get("dir").getAsString(),
                        properties));
            } else {
                collectInstallation(entry.getValue(), properties, commands);
            }
        });
    }

    private static void collectByOs(JsonElement command, @NotNull List<String> commands) {
        if (command == null) {
            return;
        }
        if (command.isJsonPrimitive()) {
            commands.add(command.getAsString());
        } else if (command.isJsonObject()) {
            command.getAsJsonObject().entrySet().forEach(os -> collectByOs(os.getValue(), commands));
        }
    }

    /**
     * Returns the templates and installers of the bundled language servers and debug adapters, by their path, as
     * {@code lsp/jdtls/installer.json}.
     */
    private static @NotNull Map<String, JsonObject> bundledFiles() throws IOException, URISyntaxException {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (String kind : List.of("templates/lsp", "templates/dap")) {
            var url = BundledTemplateCommandsTest.class.getClassLoader().getResource(kind);
            assertNotNull(url, kind);
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                // As in the sandbox of the tests, which has the plugin's jar
                try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of())) {
                    read(jar.provider().getPath(uri), files);
                }
            } else {
                read(Path.of(uri), files);
            }
        }
        return files;
    }

    private static void read(@NotNull Path templates, @NotNull Map<String, JsonObject> files) throws IOException {
        try (Stream<Path> paths = Files.walk(templates)) {
            for (Path path : paths.filter(p -> p.getFileName() != null
                    && p.getFileName().toString().matches("template\\.json|installer\\.json")).toList()) {
                files.put(templates.getFileName() + "/" + path.getParent().getFileName() + "/" + path.getFileName(),
                        JsonParser.parseString(Files.readString(path)).getAsJsonObject());
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

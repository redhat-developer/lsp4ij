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
import java.util.Set;
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
    /**
     * A folder under {@code ~/.lsp4ij}: {@code lsp} or {@code dap}, and the folder of the server in it, if there is one.
     */
    private static final Pattern INSTALL_FOLDER = Pattern.compile("\\.lsp4ij/([^/\"\\\\$]*)(?:/([^/\"\\\\$]*))?");

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

    /**
     * A template whose installer checks for the language server where its download puts it starts the language server
     * there, as the command that the installer gives it once it has downloaded it: when the check finds the language
     * server installed, the download doesn't run, and the language server keeps the command of its template (#2011).
     * Not the templates that start a language server on the PATH, as cpp_ls, nor those whose installer puts it in a
     * folder named after its version, as clangd. The CSS and ESLint language servers start from the npm package that
     * their installers share (#1960).
     */
    @Test
    void aTemplateStartsItsLanguageServerWhereItsInstallerPutsIt() throws IOException, URISyntaxException {
        var files = bundledFiles();
        List<String> problems = new ArrayList<>();
        for (String server : List.of("clojure-lsp", "erlang-ls", "harper-ls", "jdtls", "lemminx", "luau-lsp", "perl-lsp",
                "vscode-css-language-server", "vscode-eslint-language-server")) {
            var template = files.get("lsp/" + server + "/template.json");
            var installer = files.get("lsp/" + server + "/installer.json");
            assertNotNull(template, server);
            assertNotNull(installer, server);
            for (String os : List.of("windows", "default")) {
                String command = byOs(template.get("programArgs"), os);
                String installed = installedCommand(installer, os);
                if (!command.equals(installed)) {
                    problems.add(server + " (" + os + "): " + command + ", installed: " + installed);
                }
            }
        }
        assertEquals(List.of(), problems, "start the language server where its installer puts it");
    }

    /**
     * An installer puts its language server or debug adapter in {@code ~/.lsp4ij/lsp} or {@code ~/.lsp4ij/dap}, in a
     * folder of its own, named after its template, as {@code ~/.lsp4ij/dap/perl-dap} for the debug adapter
     * {@code perl-dap}, and the files of the template, as its command or its initialization options, take it from
     * there: so two templates that download the same archive, as {@code perl-lsp} and {@code perl-dap}, don't share a
     * folder, and the folder of a server is found from its template (#1960). The name is the id of the template, or its
     * directory, as {@code jdt-ls} for the template in {@code jdtls}. Not clangd, which unpacks its archive, a folder
     * named after the version of clangd, into {@code ~/.lsp4ij/lsp} itself (#2011).
     */
    @Test
    void anInstallerPutsItsServerInTheFolderOfItsTemplate() throws IOException, URISyntaxException {
        Map<String, String> templateIds = new HashMap<>();
        bundledFiles().forEach((file, json) -> {
            if (file.endsWith("/template.json")) {
                templateIds.put(file.substring(0, file.lastIndexOf('/')), json.get("id").getAsString());
            }
        });
        var texts = bundledTexts(".*\\.json");
        List<String> folders = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        texts.forEach((file, text) -> {
            // As lsp/perl-lsp
            String template = file.substring(0, file.lastIndexOf('/'));
            String kind = template.substring(0, template.indexOf('/'));
            Set<String> names = Set.copyOf(List.of(template.substring(template.indexOf('/') + 1),
                    templateIds.getOrDefault(template, "")));
            Matcher matcher = INSTALL_FOLDER.matcher(text);
            while (matcher.find()) {
                folders.add(file + ": " + matcher.group());
                if (template.equals("lsp/clangd")) {
                    continue;
                }
                if (!kind.equals(matcher.group(1)) || matcher.group(2) == null || !names.contains(matcher.group(2))) {
                    problems.add(file + ": " + matcher.group());
                }
            }
        });
        assertTrue(folders.size() > 30, "the folders of the installers and of the templates: " + folders);
        assertEquals(List.of(), problems, "install a server in ~/.lsp4ij/lsp or ~/.lsp4ij/dap, in the folder of its template");
    }

    /**
     * Returns the command that the download or the install command of the given installer gives the language server on
     * the given OS, {@code windows} or {@code default}, with the directory and the file of the download, or the
     * properties of the installer, as the working directory of an {@code npm install}.
     */
    private static @NotNull String installedCommand(@NotNull JsonObject installer, @NotNull String os) {
        Map<String, String> properties = new HashMap<>();
        var declared = installer.getAsJsonObject("properties");
        if (declared != null) {
            declared.entrySet().forEach(property -> properties.put(property.getKey(), property.getValue().getAsString()));
        }
        var run = installer.getAsJsonObject("run");
        var exec = run.getAsJsonObject("exec");
        if (exec != null) {
            var command = exec.getAsJsonObject("onSuccess").getAsJsonObject("configureServer").get("command");
            return resolve(byOs(command, os), properties);
        }
        var download = run.getAsJsonObject("download");
        var output = download.getAsJsonObject("output");
        properties.put("output.dir", resolve(output.get("dir").getAsString(), properties));
        var file = output.getAsJsonObject("file");
        if (file != null) {
            properties.put("output.file.name", byOs(file.get("name"), os));
        } else {
            // Without a file name, the download keeps the name of the file of its URL
            String url = byOs(download.get("url"), os);
            properties.put("output.file.name", url.substring(url.lastIndexOf('/') + 1));
        }
        var command = download.getAsJsonObject("onSuccess").getAsJsonObject("configureServer").get("command");
        return resolve(byOs(command, os), properties);
    }

    /**
     * Returns the value for the given OS, {@code windows} or {@code default}, of a value that can differ by OS: a
     * string, or an object by OS, whose {@code default}, or else {@code unix}, is for the OS other than Windows.
     */
    private static @NotNull String byOs(@NotNull JsonElement value, @NotNull String os) {
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        var byOs = value.getAsJsonObject();
        for (String key : os.equals("windows") ? List.of("windows", "default") : List.of("default", "unix")) {
            if (byOs.has(key)) {
                return byOs.get(key).getAsString();
            }
        }
        throw new AssertionError("No value for " + os + " in " + value);
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
        bundledTexts("template\\.json|installer\\.json")
                .forEach((file, text) -> files.put(file, JsonParser.parseString(text).getAsJsonObject()));
        return files;
    }

    /**
     * Returns the text of the files of the bundled language servers and debug adapters whose name matches the given
     * regular expression, by their path, as {@code lsp/jdtls/installer.json}.
     */
    private static @NotNull Map<String, String> bundledTexts(@NotNull String fileNames)
            throws IOException, URISyntaxException {
        Map<String, String> texts = new LinkedHashMap<>();
        for (String kind : List.of("templates/lsp", "templates/dap")) {
            var url = BundledTemplateCommandsTest.class.getClassLoader().getResource(kind);
            assertNotNull(url, kind);
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                // As in the sandbox of the tests, which has the plugin's jar
                try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of())) {
                    read(jar.provider().getPath(uri), fileNames, texts);
                }
            } else {
                read(Path.of(uri), fileNames, texts);
            }
        }
        return texts;
    }

    private static void read(@NotNull Path templates, @NotNull String fileNames, @NotNull Map<String, String> texts)
            throws IOException {
        try (Stream<Path> paths = Files.walk(templates)) {
            for (Path path : paths.filter(p -> p.getFileName() != null
                    && p.getFileName().toString().matches(fileNames)).toList()) {
                texts.put(templates.getFileName() + "/" + path.getParent().getFileName() + "/" + path.getFileName(),
                        Files.readString(path));
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
